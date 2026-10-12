package com.orbitcrm.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body of {@code POST /api/v1/auth/password/change}.
 *
 * <p>Purpose: let a signed-in user change their password. The current password must be correct, the new one must
 * differ from it and satisfy the password policy; all other sessions of the user are signed out afterwards.
 *
 * @author Abhay
 * @param currentPassword the user's current password
 * @param newPassword     the new password
 */
public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
}
