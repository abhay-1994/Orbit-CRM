package com.orbitcrm.identity.entity;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import com.orbitcrm.shared.entity.TenantScopedEntity;
import com.orbitcrm.shared.security.AccessScope;
import com.orbitcrm.shared.security.Action;

/**
 * JPA entity for the {@code roles} table: a named set of permissions within a tenant.
 *
 * <p>Purpose: the basis of authorization. Users hold exactly one role; the role's {@link PermissionGrant}s define
 * which actions they may perform on which resources and with which scope. Special flags:
 * <ul>
 *   <li>{@code administrator} - implicitly holds every permission with ALL scope, including on modules added
 *   later, and cannot be modified;</li>
 *   <li>{@code system} - seeded for every tenant (Administrator, Sales Manager, Sales User, ...) and cannot be
 *   deleted, although non-administrator system roles remain editable.</li>
 * </ul>
 * Grants are loaded eagerly because they are needed on every authentication.
 *
 * @author Abhay
 */
@Entity
@Table(name = "roles")
public class Role extends TenantScopedEntity {

    @Column(nullable = false)
    private String name;

    private String description;

    /** Administrators implicitly hold every permission, including on modules added later. */
    @Column(nullable = false)
    private boolean administrator;

    /** System roles are seeded for every tenant and cannot be deleted. */
    @Column(nullable = false)
    private boolean system;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    private Set<PermissionGrant> grants = new HashSet<>();

    protected Role() {
    }

    public Role(String name, String description, boolean administrator, boolean system) {
        this.name = name;
        this.description = description;
        this.administrator = administrator;
        this.system = system;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isAdministrator() {
        return administrator;
    }

    public boolean isSystem() {
        return system;
    }

    public Set<PermissionGrant> getGrants() {
        return Set.copyOf(grants);
    }

    public void rename(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void replaceGrants(Collection<PermissionGrant> newGrants) {
        grants.clear();
        newGrants.stream().filter(g -> g.scope().allows()).forEach(grants::add);
    }

    public Map<String, Map<Action, AccessScope>> permissionMap() {
        Map<String, Map<Action, AccessScope>> map = new HashMap<>();
        for (PermissionGrant grant : grants) {
            map.computeIfAbsent(grant.resource(), r -> new EnumMap<>(Action.class))
                    .put(grant.action(), grant.scope());
        }
        return map;
    }
}
