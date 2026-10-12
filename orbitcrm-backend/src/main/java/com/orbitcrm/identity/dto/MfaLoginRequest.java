package com.orbitcrm.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body of {@code POST /api/v1/auth/login/mfa}.
 *
 * <p>Purpose: complete a two-step login by presenting the challenge token from {@code /auth/login} together with a
 * code from the authenticator app. Each code can be used only once.
 *
 * @author Abhay
 * @param mfaToken the challenge token returned by the password step (valid for 5 minutes)
 * @param code     the current 6-digit TOTP code
 */
public record MfaLoginRequest(@NotBlank String mfaToken, @NotBlank String code) {
}
