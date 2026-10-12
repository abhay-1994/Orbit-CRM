package com.orbitcrm.identity.entity;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import com.orbitcrm.shared.security.AccessScope;
import com.orbitcrm.shared.security.Action;

/**
 * Embeddable value object for one row of the {@code role_permissions} table.
 *
 * <p>Purpose: store a single permission of a {@link Role}: resource, action and scope, e.g.
 * {@code (leads, UPDATE, TEAM)}. Equality is based on resource and action only, so a role holds at most one scope
 * per resource and action.
 *
 * @author Abhay
 */
@Embeddable
public class PermissionGrant {

    @Column(name = "resource", nullable = false)
    private String resource;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    private AccessScope scope;

    protected PermissionGrant() {
    }

    public PermissionGrant(String resource, Action action, AccessScope scope) {
        this.resource = resource;
        this.action = action;
        this.scope = scope;
    }

    public String resource() {
        return resource;
    }

    public Action action() {
        return action;
    }

    public AccessScope scope() {
        return scope;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PermissionGrant other)) {
            return false;
        }
        return resource.equals(other.resource) && action == other.action;
    }

    @Override
    public int hashCode() {
        return Objects.hash(resource, action);
    }
}
