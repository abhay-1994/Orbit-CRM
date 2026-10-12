package com.orbitcrm.identity.security;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbitcrm.identity.entity.AuthSession;
import com.orbitcrm.identity.entity.Role;
import com.orbitcrm.identity.entity.User;
import com.orbitcrm.identity.exception.InvalidPrincipalException;
import com.orbitcrm.identity.repository.AuthSessionRepository;
import com.orbitcrm.identity.repository.RoleRepository;
import com.orbitcrm.identity.repository.TeamRepository;
import com.orbitcrm.identity.repository.UserRepository;
import com.orbitcrm.identity.util.TeamHierarchy;
import com.orbitcrm.shared.config.OrbitProperties;
import com.orbitcrm.shared.security.CurrentUser;
import com.orbitcrm.shared.tenant.TenantContext;
import com.orbitcrm.shared.util.AfterCommit;
import com.orbitcrm.tenant.dto.TenantInfo;
import com.orbitcrm.tenant.service.TenantService;

/**
 * Resolves a validated access token to a {@link com.orbitcrm.shared.security.CurrentUser} and caches the result.
 *
 * <p>Purpose: perform the per-request authorization lookups cheaply. Building a principal checks that the tenant is
 * active, the session belongs to the user and is still active, and the user is active; it then loads the role's
 * permissions and the user's team subtree (for TEAM scope). Results are cached for a short time (30 seconds by
 * default) and evicted after commit when something relevant changes: a user's profile or role
 * ({@code evictUser}), roles or teams of a tenant ({@code evictTenant}) or a session ({@code evictSession}). The
 * short TTL bounds staleness when several instances run.
 *
 * @author Abhay
 */
@Component
public class PrincipalService {

    private final Cache<Key, CurrentUser> cache;
    private final TransactionTemplate readOnlyTx;
    private final TenantService tenants;
    private final UserRepository users;
    private final RoleRepository roles;
    private final TeamRepository teams;
    private final AuthSessionRepository sessions;

    PrincipalService(OrbitProperties properties, PlatformTransactionManager txManager, TenantService tenants,
                     UserRepository users, RoleRepository roles, TeamRepository teams,
                     AuthSessionRepository sessions) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.security().principalCacheTtl())
                .maximumSize(50_000)
                .build();
        this.readOnlyTx = new TransactionTemplate(txManager);
        this.readOnlyTx.setReadOnly(true);
        this.tenants = tenants;
        this.users = users;
        this.roles = roles;
        this.teams = teams;
        this.sessions = sessions;
    }

    public CurrentUser load(UUID tenantId, UUID userId, UUID sessionId) {
        return cache.get(new Key(tenantId, userId, sessionId), this::build);
    }

    private CurrentUser build(Key key) {
        return TenantContext.callAs(key.tenantId(), () -> readOnlyTx.execute(status -> {
            TenantInfo tenant = tenants.get(key.tenantId());
            if (!tenant.active()) {
                throw new InvalidPrincipalException("Tenant is suspended");
            }
            AuthSession session = sessions.findById(key.sessionId())
                    .filter(s -> s.getUserId().equals(key.userId()) && s.getTenantId().equals(key.tenantId()))
                    .filter(s -> s.isActive(Instant.now()))
                    .orElseThrow(() -> new InvalidPrincipalException("Session is no longer valid"));
            User user = users.findById(key.userId())
                    .filter(User::isActive)
                    .orElseThrow(() -> new InvalidPrincipalException("User is not active"));
            Role role = roles.findById(user.getRoleId())
                    .orElseThrow(() -> new InvalidPrincipalException("Role not found"));
            Set<UUID> teamIds = user.getTeamId() == null
                    ? Set.of()
                    : Set.copyOf(new TeamHierarchy(teams.findAll()).subtree(user.getTeamId()));
            return new CurrentUser(user.getId(), key.tenantId(), session.getId(), user.getEmail(),
                    user.displayName(), role.getId(), role.getName(), role.isAdministrator(), user.getTeamId(),
                    teamIds, role.permissionMap());
        }));
    }

    public void evictUser(UUID tenantId, UUID userId) {
        AfterCommit.run(() -> cache.asMap().keySet()
                .removeIf(k -> k.tenantId().equals(tenantId) && k.userId().equals(userId)));
    }

    public void evictTenant(UUID tenantId) {
        AfterCommit.run(() -> cache.asMap().keySet().removeIf(k -> k.tenantId().equals(tenantId)));
    }

    public void evictSession(UUID sessionId) {
        AfterCommit.run(() -> cache.asMap().keySet().removeIf(k -> k.sessionId().equals(sessionId)));
    }

    /**
     * Cache key of a resolved principal.
     *
     * <p>Purpose: cache principals per tenant, user and session, so that revoking one session or changing one user evicts
     * exactly the affected entries.
     *
     * @param tenantId  tenant id from the access token
     * @param userId    user id from the access token
     * @param sessionId session id from the access token
     */
    private record Key(UUID tenantId, UUID userId, UUID sessionId) {
    }
}
