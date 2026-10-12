package com.orbitcrm.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body of {@code POST /api/v1/auth/login}.
 *
 * <p>Purpose: sign in to a specific workspace. Because the same email may exist in several workspaces, the
 * workspace slug is part of the credentials.
 *
 * @author Abhay
 * @param tenant   workspace slug, e.g. {@code acme}
 * @param email    account email address
 * @param password account password
 */
public record LoginRequest(
        @NotBlank String tenant,
        @NotBlank @Email String email,
        @NotBlank String password) {
}
