package com.orbitcrm.identity.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One active login session (device), returned by {@code GET /api/v1/auth/sessions}.
 *
 * <p>Purpose: let users see where they are signed in and revoke sessions they do not recognize.
 *
 * @author Abhay
 * @param id         session id (used with {@code DELETE /api/v1/auth/sessions/{id}})
 * @param createdAt  when the user signed in
 * @param lastUsedAt last token refresh
 * @param expiresAt  absolute expiry of the session
 * @param userAgent  device / browser description
 * @param ipAddress  IP address at sign-in
 * @param current    whether this is the session making the request
 */
public record SessionResponse(UUID id, Instant createdAt, Instant lastUsedAt, Instant expiresAt,
                              String userAgent, String ipAddress, boolean current) {
}
