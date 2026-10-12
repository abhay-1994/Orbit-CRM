package com.orbitcrm.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body of {@code POST /api/v1/auth/mfa/disable}.
 *
 * <p>Purpose: turn off two-factor authentication. Requires the account password and, while MFA is enabled, a
 * current TOTP code, so a hijacked session alone cannot remove the second factor.
 *
 * @author Abhay
 * @param password the account password
 * @param code     the current 6-digit TOTP code
 */
public record MfaDisableRequest(@NotBlank String password, String code) {
}
