package com.orbitcrm.identity.dto;

/**
 * Internal result of a completed authentication, passed from the auth services to {@code AuthController}.
 *
 * <p>Purpose: bundle the freshly issued access token, the session with its new refresh token and the user's
 * profile, so the controller can build the response body and the refresh cookie from one object.
 *
 * @author Abhay
 * @param accessToken the issued JWT and its lifetime
 * @param session     the login session and its newly issued refresh token
 * @param me          the "who am I" payload for the signed-in user
 */
public record AuthTokens(IssuedToken accessToken, IssuedSession session, MeResponse me) {
}
