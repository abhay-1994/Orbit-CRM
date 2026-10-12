package com.orbitcrm.identity.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbitcrm.audit.dto.AuditEntry;
import com.orbitcrm.audit.model.AuditAction;
import com.orbitcrm.audit.service.AuditService;
import com.orbitcrm.identity.dto.CatalogResource;
import com.orbitcrm.identity.dto.PermissionCatalogResponse;
import com.orbitcrm.identity.dto.RoleRequest;
import com.orbitcrm.identity.dto.RoleResponse;
import com.orbitcrm.identity.entity.PermissionGrant;
import com.orbitcrm.identity.entity.Role;
import com.orbitcrm.identity.repository.RoleRepository;
import com.orbitcrm.identity.repository.UserRepository;
import com.orbitcrm.identity.security.PrincipalService;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.security.AccessScope;
import com.orbitcrm.shared.security.Action;
import com.orbitcrm.shared.security.SecuredResource;
import com.orbitcrm.shared.tenant.TenantContext;

/**
 * Business logic for role administration.
 *
 * <p>Purpose: create, read, update and delete roles while keeping the permission model consistent:
 * <ul>
 *   <li>role names are unique per tenant (case-insensitive);</li>
 *   <li>permissions are validated against the permission catalog (unknown resources or inapplicable actions are
 *   rejected), and for unscoped resources and the CREATE / IMPORT actions any granted scope is normalized to
 *   {@code ALL};</li>
 *   <li>the Administrator role cannot be changed; system roles cannot be deleted; roles still held by users cannot
 *   be deleted;</li>
 *   <li>changes evict cached principals so they apply immediately, and are recorded in the audit log.</li>
 * </ul>
 *
 * @author Abhay
 */
@Service
public class RoleService {

    private final RoleRepository roles;
    private final UserRepository users;
    private final PermissionCatalog catalog;
    private final PrincipalService principals;
    private final AuditService audit;

    RoleService(RoleRepository roles, UserRepository users, PermissionCatalog catalog, PrincipalService principals,
                AuditService audit) {
        this.roles = roles;
        this.users = users;
        this.catalog = catalog;
        this.principals = principals;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roles.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse get(UUID id) {
        return toResponse(find(id));
    }

    public PermissionCatalogResponse catalog() {
        return new PermissionCatalogResponse(
                catalog.all().stream()
                        .map(r -> new CatalogResource(r.key(), r.label(), r.category(), r.actions(), r.scoped()))
                        .toList(),
                List.of(AccessScope.values()));
    }

    @Transactional
    public RoleResponse create(RoleRequest request) {
        String name = request.name().trim();
        if (roles.findByNameIgnoreCase(name).isPresent()) {
            throw ApiException.conflict("ROLE_NAME_TAKEN", "A role with this name already exists");
        }
        Role role = new Role(name, request.description(), false, false);
        role.replaceGrants(toGrants(request.permissions()));
        Role saved = roles.save(role);
        audit.record(AuditEntry.security(AuditAction.ROLE_CREATED, null, null, Map.of("roleId", saved.getId().toString(), "name", saved.getName())));
        return toResponse(saved);
    }

    @Transactional
    public RoleResponse update(UUID id, RoleRequest request) {
        Role role = find(id);
        if (role.isAdministrator()) {
            throw ApiException.conflict("ROLE_LOCKED", "The Administrator role cannot be modified");
        }
        String name = request.name().trim();
        roles.findByNameIgnoreCase(name).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw ApiException.conflict("ROLE_NAME_TAKEN", "A role with this name already exists");
        });
        role.rename(name, request.description());
        role.replaceGrants(toGrants(request.permissions()));
        roles.flush();
        principals.evictTenant(TenantContext.requireTenantId());
        audit.record(AuditEntry.security(AuditAction.ROLE_UPDATED, null, null, Map.of("roleId", role.getId().toString(), "name", role.getName())));
        return toResponse(role);
    }

    @Transactional
    public void delete(UUID id) {
        Role role = find(id);
        if (role.isSystem()) {
            throw ApiException.conflict("ROLE_LOCKED", "Built-in roles cannot be deleted");
        }
        if (users.countByRoleId(id) > 0) {
            throw ApiException.conflict("ROLE_IN_USE", "Reassign the role's users before deleting it");
        }
        roles.delete(role);
        audit.record(AuditEntry.security(AuditAction.ROLE_DELETED, null, null, Map.of("roleId", id.toString(), "name", role.getName())));
    }

    /**
     * Validates grants against the catalog. Unscoped resources and the CREATE/IMPORT actions only know
     * "granted or not", so any non-NONE scope is normalised to ALL.
     */
    private List<PermissionGrant> toGrants(Map<String, Map<Action, AccessScope>> permissions) {
        List<PermissionGrant> grants = new ArrayList<>();
        if (permissions == null) {
            return grants;
        }
        permissions.forEach((resourceKey, actions) -> {
            SecuredResource resource = catalog.find(resourceKey).orElseThrow(() ->
                    ApiException.validation("permissions." + resourceKey, "UNKNOWN_RESOURCE",
                            "Unknown resource: " + resourceKey));
            if (actions == null) {
                return;
            }
            actions.forEach((action, scope) -> {
                if (scope == null || !scope.allows()) {
                    return;
                }
                if (!resource.actions().contains(action)) {
                    throw ApiException.validation("permissions." + resourceKey + "." + action, "UNSUPPORTED_ACTION",
                            action + " is not applicable to " + resourceKey);
                }
                boolean binary = !resource.scoped() || action == Action.CREATE || action == Action.IMPORT;
                grants.add(new PermissionGrant(resourceKey, action, binary ? AccessScope.ALL : scope));
            });
        });
        return grants;
    }

    private Role find(UUID id) {
        return roles.findById(id).orElseThrow(() -> ApiException.notFound("ROLE_NOT_FOUND", "Role does not exist"));
    }

    private RoleResponse toResponse(Role role) {
        return new RoleResponse(role.getId(), role.getName(), role.getDescription(), role.isAdministrator(),
                role.isSystem(), users.countByRoleId(role.getId()), role.permissionMap(), role.getCreatedAt(),
                role.getUpdatedAt());
    }
}
