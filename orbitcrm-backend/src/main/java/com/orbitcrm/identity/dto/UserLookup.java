package com.orbitcrm.identity.dto;

import java.util.UUID;

/**
 * Lightweight user entry returned by {@code GET /api/v1/users/lookup}.
 *
 * <p>Purpose: populate owner and assignee pickers. Available to every authenticated user, so it exposes only what a
 * picker needs.
 *
 * @author Abhay
 * @param id       user id
 * @param fullName first and last name
 * @param email    email address
 */
public record UserLookup(UUID id, String fullName, String email) {
}
