package com.orbitcrm.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body of {@code POST /api/v1/auth/mfa/enable}.
 *
 * <p>Purpose: confirm two-factor enrolment by proving the authenticator app was set up with the secret returned by
 * {@code /mfa/setup}.
 *
 * @author Abhay
 * @param code the current 6-digit TOTP code
 */
public record MfaCodeRequest(@NotBlank String code) {
}
