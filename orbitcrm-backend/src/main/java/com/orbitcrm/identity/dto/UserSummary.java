package com.orbitcrm.identity.dto;

import java.util.UUID;

/**
 * Minimal view of a user exposed to other modules through {@code UserDirectory}.
 *
 * <p>Purpose: let modules such as notifications, notes or import check a user's status, email or team without
 * depending on the identity module's entities.
 *
 * @author Abhay
 * @param id          user id
 * @param email       email address
 * @param displayName first and last name
 * @param status      {@code INVITED}, {@code ACTIVE} or {@code DISABLED}
 * @param teamId      team id, or {@code null}
 */
public record UserSummary(UUID id, String email, String displayName, String status, UUID teamId) {
}
