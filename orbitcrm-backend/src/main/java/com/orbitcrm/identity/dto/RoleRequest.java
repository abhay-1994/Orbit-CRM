package com.orbitcrm.identity.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.orbitcrm.shared.security.AccessScope;
import com.orbitcrm.shared.security.Action;

/**
 * Request body for creating ({@code POST /api/v1/roles}) or replacing ({@code PUT /api/v1/roles/{id}}) a role.
 *
 * <p>Purpose: define a role's name and its complete permission matrix. Permissions are validated against the
 * permission catalog; for unscoped resources and for the CREATE and IMPORT actions any granted scope is
 * normalized to {@code ALL}, and {@code NONE} entries are dropped.
 *
 * @author Abhay
 * @param name        unique role name (max 80 characters)
 * @param description optional description (max 500 characters)
 * @param permissions resource key to action to scope, e.g. {@code {"leads": {"READ": "TEAM"}}}
 */
public record RoleRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 500) String description,
        Map<String, Map<Action, AccessScope>> permissions) {
}
