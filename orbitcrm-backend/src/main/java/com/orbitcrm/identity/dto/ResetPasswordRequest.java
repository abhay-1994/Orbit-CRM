package com.orbitcrm.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body of {@code POST /api/v1/auth/password/reset}.
 *
 * <p>Purpose: set a new password with the single-use token from a reset email. On success every session of the
 * user is signed out.
 *
 * @author Abhay
 * @param token       the raw reset token from the email link
 * @param newPassword the new password; must satisfy {@code PasswordPolicy}
 */
public record ResetPasswordRequest(@NotBlank String token, @NotBlank String newPassword) {
}
