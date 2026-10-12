package com.orbitcrm.identity.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.orbitcrm.shared.security.AccessScope;
import com.orbitcrm.shared.security.Action;

/**
 * Representation of a role returned by the role endpoints.
 *
 * <p>Purpose: show a role with its permission matrix and how many users hold it (used to warn before changes and to
 * block deletion of roles in use).
 *
 * @author Abhay
 * @param id            role id
 * @param name          role name
 * @param description   description
 * @param administrator whether this is the Administrator role (implicitly holds every permission)
 * @param system        whether the role was seeded for the tenant and cannot be deleted
 * @param userCount     number of users holding the role
 * @param permissions   resource key to action to scope
 * @param createdAt     creation time
 * @param updatedAt     last modification time
 */
public record RoleResponse(
        UUID id,
        String name,
        String description,
        boolean administrator,
        boolean system,
        long userCount,
        Map<String, Map<Action, AccessScope>> permissions,
        Instant createdAt,
        Instant updatedAt) {
}
