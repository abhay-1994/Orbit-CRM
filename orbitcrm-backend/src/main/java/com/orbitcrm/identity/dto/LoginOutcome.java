package com.orbitcrm.identity.dto;

/**
 * Internal result of the password step of a login.
 *
 * <p>Purpose: express the two possible successful outcomes of {@code AuthService.login}: either the user has
 * two-factor authentication enabled and must still present a TOTP code ({@code mfaToken} set), or the login is
 * complete ({@code tokens} set). Exactly one of the two is non-null.
 *
 * @author Abhay
 * @param mfaToken short-lived token to exchange, together with a TOTP code, at {@code /auth/login/mfa}
 * @param tokens   the issued tokens when no second factor is required
 */
public record LoginOutcome(String mfaToken, AuthTokens tokens) {
    public static LoginOutcome mfa(String token) {
        return new LoginOutcome(token, null);
    }

    public static LoginOutcome authenticated(AuthTokens tokens) {
        return new LoginOutcome(null, tokens);
    }
}
