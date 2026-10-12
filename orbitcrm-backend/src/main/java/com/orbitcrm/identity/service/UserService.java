package com.orbitcrm.identity.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbitcrm.audit.dto.AuditEntry;
import com.orbitcrm.audit.model.AuditAction;
import com.orbitcrm.audit.service.AuditService;
import com.orbitcrm.identity.dto.InviteUserRequest;
import com.orbitcrm.identity.dto.UpdateUserRequest;
import com.orbitcrm.identity.dto.UserLookup;
import com.orbitcrm.identity.dto.UserResponse;
import com.orbitcrm.identity.entity.Role;
import com.orbitcrm.identity.entity.Team;
import com.orbitcrm.identity.entity.User;
import com.orbitcrm.identity.entity.UserToken;
import com.orbitcrm.identity.repository.RoleRepository;
import com.orbitcrm.identity.repository.TeamRepository;
import com.orbitcrm.identity.repository.UserRepository;
import com.orbitcrm.identity.security.PrincipalService;
import com.orbitcrm.shared.dto.PageParams;
import com.orbitcrm.shared.dto.PageResponse;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.security.AccessControl;
import com.orbitcrm.shared.security.CurrentUser;
import com.orbitcrm.tenant.service.TenantService;

/**
 * Business logic for user administration within the current tenant.
 *
 * <p>Purpose: manage the people of a workspace:
 * <ul>
 *   <li>paged search by name or email with status, role and team filters, plus a lightweight lookup of active
 *   users for pickers;</li>
 *   <li>invitations: creates an {@code INVITED} user with a unique email, a valid role and optional team, and emails
 *   a single-use link; invitations can be resent;</li>
 *   <li>updates of profile, role, team and status with guard rails: administrators cannot disable or demote
 *   themselves, the last active administrator is protected, and disabling a user revokes all their sessions;</li>
 *   <li>every change evicts the cached principal and is recorded in the audit log.</li>
 * </ul>
 *
 * @author Abhay
 */
@Service
public class UserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final TeamRepository teams;
    private final UserTokenService userTokens;
    private final SessionService sessions;
    private final PrincipalService principals;
    private final IdentityMailer mailer;
    private final TenantService tenants;
    private final AuditService audit;

    UserService(UserRepository users, RoleRepository roles, TeamRepository teams, UserTokenService userTokens,
                SessionService sessions, PrincipalService principals, IdentityMailer mailer, TenantService tenants,
                AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.teams = teams;
        this.userTokens = userTokens;
        this.sessions = sessions;
        this.principals = principals;
        this.mailer = mailer;
        this.tenants = tenants;
        this.audit = audit;
    }

    private static Map<String, Object> userChangeMetadata(User user, UpdateUserRequest request) {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("userId", user.getId().toString());
        metadata.put("email", user.getEmail());
        if (request.roleId() != null) {
            metadata.put("roleId", request.roleId().toString());
        }
        if (request.status() != null) {
            metadata.put("status", request.status().name());
        }
        if (request.teamId() != null || Boolean.TRUE.equals(request.clearTeam())) {
            metadata.put("teamId", user.getTeamId() == null ? null : user.getTeamId().toString());
        }
        return metadata;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String query, User.Status status, UUID roleId, UUID teamId,
                                           PageParams pageParams) {
        Specification<User> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase(java.util.Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("email")), like, '\\'),
                        cb.like(cb.lower(root.get("firstName")), like, '\\'),
                        cb.like(cb.lower(root.get("lastName")), like, '\\')));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (roleId != null) {
                predicates.add(cb.equal(root.get("roleId"), roleId));
            }
            if (teamId != null) {
                predicates.add(cb.equal(root.get("teamId"), teamId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<User> page = users.findAll(spec, PageRequest.of(pageParams.page(), pageParams.size(),
                Sort.by("firstName", "lastName", "id")));
        return toResponses(page);
    }

    @Transactional(readOnly = true)
    public List<UserLookup> lookup(String query, int limit) {
        Specification<User> spec = (root, cq, cb) -> {
            Predicate active = cb.equal(root.get("status"), User.Status.ACTIVE);
            if (query == null || query.isBlank()) {
                return active;
            }
            String like = "%" + query.trim().toLowerCase(java.util.Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            return cb.and(active, cb.or(
                    cb.like(cb.lower(root.get("email")), like, '\\'),
                    cb.like(cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"))),
                            like, '\\')));
        };
        return users.findAll(spec, PageRequest.of(0, Math.min(Math.max(limit, 1), 50), Sort.by("firstName")))
                .map(u -> new UserLookup(u.getId(), u.displayName(), u.getEmail()))
                .getContent();
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return toResponse(find(id));
    }

    @Transactional
    public UserResponse invite(InviteUserRequest request) {
        String email = User.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("USER_EMAIL_TAKEN", "A user with this email already exists");
        }
        Role role = requireRole(request.roleId());
        UUID teamId = request.teamId() == null ? null : requireTeam(request.teamId()).getId();
        User user = new User(email, request.firstName(), request.lastName(), role.getId(), teamId,
                User.Status.INVITED);
        user.updateProfile(null, null, request.jobTitle(), request.phone(), null, null);
        users.save(user);
        sendInvitation(user);
        audit.record(AuditEntry.security(AuditAction.USER_INVITED, null, null,
                Map.of("userId", user.getId().toString(), "email", user.getEmail(), "roleId", role.getId().toString())));
        return toResponse(user);
    }

    @Transactional
    public void resendInvitation(UUID id) {
        User user = find(id);
        if (user.getStatus() != User.Status.INVITED) {
            throw ApiException.conflict("USER_NOT_INVITED", "Only invited users can receive a new invitation");
        }
        sendInvitation(user);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        CurrentUser actor = AccessControl.requireUser();
        User user = find(id);
        UUID newRoleId = request.roleId() == null ? user.getRoleId() : requireRole(request.roleId()).getId();
        UUID newTeamId = Boolean.TRUE.equals(request.clearTeam()) ? null
                : request.teamId() == null ? user.getTeamId() : requireTeam(request.teamId()).getId();

        boolean losesAdmin = isAdministratorRole(user.getRoleId()) && !isAdministratorRole(newRoleId);
        boolean disabling = request.status() == User.Status.DISABLED && user.getStatus() != User.Status.DISABLED;
        if (user.getId().equals(actor.userId()) && (disabling || losesAdmin)) {
            throw ApiException.conflict("CANNOT_DEMOTE_SELF",
                    "You cannot disable yourself or remove your own administrator role");
        }
        if ((losesAdmin || disabling) && isAdministratorRole(user.getRoleId()) && user.isActive()) {
            guardLastAdministrator();
        }
        if (request.status() == User.Status.INVITED && user.getStatus() != User.Status.INVITED) {
            throw ApiException.validation("status", "INVALID", "A user cannot be moved back to INVITED");
        }
        if (request.status() == User.Status.ACTIVE && user.getPasswordHash() == null) {
            throw ApiException.validation("status", "INVALID", "The user has not accepted the invitation yet");
        }

        user.updateProfile(request.firstName(), request.lastName(), request.jobTitle(), request.phone(), null, null);
        user.assign(newRoleId, newTeamId);
        if (request.status() != null) {
            user.changeStatus(request.status());
        }
        users.flush();
        if (disabling) {
            sessions.revokeAll(user.getTenantId(), user.getId(), "USER_DISABLED");
        }
        principals.evictUser(user.getTenantId(), user.getId());
        audit.record(AuditEntry.security(AuditAction.USER_UPDATED, null, null, userChangeMetadata(user, request)));
        return toResponse(user);
    }

    private void guardLastAdministrator() {
        List<UUID> adminRoleIds = roles.findAll().stream().filter(Role::isAdministrator).map(Role::getId).toList();
        if (users.countByRoleIdInAndStatus(adminRoleIds, User.Status.ACTIVE) <= 1) {
            throw ApiException.conflict("LAST_ADMINISTRATOR", "The workspace must keep at least one active administrator");
        }
    }

    private boolean isAdministratorRole(UUID roleId) {
        return roles.findById(roleId).map(Role::isAdministrator).orElse(false);
    }

    private void sendInvitation(User user) {
        String raw = userTokens.issue(user.getTenantId(), user.getId(), UserToken.Purpose.INVITE);
        String workspace = tenants.current().name();
        String inviter = AccessControl.currentUser().map(CurrentUser::displayName).orElse("An administrator");
        mailer.sendInvitation(user.getEmail(), user.getFirstName(), inviter, workspace, raw);
    }

    private User find(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User does not exist"));
    }

    private Role requireRole(UUID roleId) {
        return roles.findById(roleId)
                .orElseThrow(() -> ApiException.validation("roleId", "NOT_FOUND", "Role does not exist"));
    }

    private Team requireTeam(UUID teamId) {
        return teams.findById(teamId)
                .orElseThrow(() -> ApiException.validation("teamId", "NOT_FOUND", "Team does not exist"));
    }

    private PageResponse<UserResponse> toResponses(Page<User> page) {
        Map<UUID, String> roleNames = roles.findAll().stream().collect(Collectors.toMap(Role::getId, Role::getName));
        Map<UUID, String> teamNames = teams.findAll().stream().collect(Collectors.toMap(Team::getId, Team::getName));
        return PageResponse.from(page).map(u -> toResponse(u, roleNames::get, teamNames::get));
    }

    private UserResponse toResponse(User user) {
        return toResponse(user,
                id -> roles.findById(id).map(Role::getName).orElse(null),
                id -> teams.findById(id).map(Team::getName).orElse(null));
    }

    private static UserResponse toResponse(User u, Function<UUID, String> roleName, Function<UUID, String> teamName) {
        return new UserResponse(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.displayName(),
                u.getStatus().name(), u.getRoleId(), roleName.apply(u.getRoleId()), u.getTeamId(),
                u.getTeamId() == null ? null : teamName.apply(u.getTeamId()), u.getJobTitle(), u.getPhone(),
                u.isMfaEnabled(), u.getLastLoginAt(), u.getCreatedAt(), u.getUpdatedAt());
    }
}
