package com.orbitcrm.identity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response body of {@code POST /api/v1/auth/login}; {@code null} fields are omitted from the JSON.
 *
 * <p>Purpose: support both one-step and two-step logins with a single response type:
 * <ul>
 *   <li>two-factor required: {@code {"mfaRequired": true, "mfaToken": "..."}};</li>
 *   <li>signed in: {@code mfaRequired=false} plus the same token fields as {@link AuthResponse}.</li>
 * </ul>
 *
 * @author Abhay
 * @param mfaRequired  whether the client must now call {@code /auth/login/mfa}
 * @param mfaToken     short-lived challenge token for the MFA step
 * @param accessToken  JWT access token (when signed in)
 * @param tokenType    {@code Bearer} (when signed in)
 * @param refreshToken single-use refresh token (when signed in; also set as a cookie)
 * @param expiresIn    access token lifetime in seconds (when signed in)
 * @param user         the signed-in user's profile and permissions (when signed in)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(boolean mfaRequired, String mfaToken, String accessToken, String tokenType,
                            String refreshToken, Long expiresIn, MeResponse user) {

    public static LoginResponse mfa(String mfaToken) {
        return new LoginResponse(true, mfaToken, null, null, null, null, null);
    }

    public static LoginResponse authenticated(AuthResponse auth) {
        return new LoginResponse(false, null, auth.accessToken(), auth.tokenType(), auth.refreshToken(),
                auth.expiresIn(), auth.user());
    }
}
