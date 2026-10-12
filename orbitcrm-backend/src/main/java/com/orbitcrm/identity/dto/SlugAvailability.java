package com.orbitcrm.identity.dto;

/**
 * Response body of {@code GET /api/v1/auth/slug-availability}.
 *
 * <p>Purpose: let the signup form tell the user whether the workspace URL they typed can be used (valid format, not
 * reserved, not taken).
 *
 * @author Abhay
 * @param slug      the slug that was checked
 * @param available whether it can be used for a new workspace
 */
public record SlugAvailability(String slug, boolean available) {
}
