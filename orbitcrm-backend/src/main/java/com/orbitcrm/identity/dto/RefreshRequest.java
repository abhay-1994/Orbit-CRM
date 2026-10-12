package com.orbitcrm.identity.dto;

/**
 * Optional request body of {@code POST /api/v1/auth/refresh} and {@code POST /api/v1/auth/logout}.
 *
 * <p>Purpose: let clients that do not use cookies (mobile apps, API tools, server-to-server integrations) send the
 * refresh token explicitly. When the body is missing or the token is blank, the endpoints fall back to the
 * refresh cookie and require the {@code X-Requested-With} header.
 *
 * @author Abhay
 * @param refreshToken the refresh token received from the last login or refresh
 */
public record RefreshRequest(String refreshToken) {
}
