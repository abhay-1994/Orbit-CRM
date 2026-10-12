package com.orbitcrm.identity.dto;

/**
 * Response body returned when a session is created or refreshed (register, MFA login, refresh, invitation
 * acceptance, and inside {@link LoginResponse} for a password login).
 *
 * <p>Purpose: hand the client everything it needs to call the API. The access token is sent as
 * {@code Authorization: Bearer <token>}. The refresh token is returned in the body for non-browser clients and is
 * also set as an HttpOnly cookie; browser apps should rely on the cookie and not store the body value in
 * JavaScript-accessible storage.
 *
 * @author Abhay
 * @param accessToken  short-lived JWT access token (15 minutes by default)
 * @param tokenType    always {@code Bearer}
 * @param refreshToken opaque single-use refresh token; each refresh returns a new one
 * @param expiresIn    lifetime of the access token in seconds
 * @param user         profile, tenant and effective permissions of the signed-in user
 */
public record AuthResponse(String accessToken, String tokenType, String refreshToken, long expiresIn,
                           MeResponse user) {
}
