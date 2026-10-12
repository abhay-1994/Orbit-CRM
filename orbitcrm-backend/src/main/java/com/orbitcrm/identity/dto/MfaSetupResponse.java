package com.orbitcrm.identity.dto;

/**
 * Response body of {@code POST /api/v1/auth/mfa/setup}.
 *
 * <p>Purpose: provide the new TOTP secret so the user can add the account to an authenticator app, either by
 * scanning a QR code generated from {@code otpauthUri} or by typing the secret. MFA becomes active only after the
 * user confirms a code via {@code /mfa/enable}.
 *
 * @author Abhay
 * @param secret     base32-encoded TOTP secret
 * @param otpauthUri {@code otpauth://totp/...} URI to render as a QR code
 */
public record MfaSetupResponse(String secret, String otpauthUri) {
}
