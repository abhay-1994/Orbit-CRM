package com.orbitcrm.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body of {@code POST /api/v1/auth/password/forgot}.
 *
 * <p>Purpose: request a password reset email. The endpoint always answers the same way, whether or not the
 * workspace and account exist, so it cannot be used to discover accounts.
 *
 * @author Abhay
 * @param tenant workspace slug
 * @param email  account email address
 */
public record ForgotPasswordRequest(@NotBlank String tenant, @NotBlank @Email String email) {
}
