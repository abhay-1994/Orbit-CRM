package com.orbitcrm.identity.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Internal description of a login session together with its newly issued refresh token.
 *
 * <p>Purpose: returned by {@code SessionService} when a session is created or its refresh token rotated. The raw
 * refresh token exists only in this object and in the response to the client; the database stores its hash.
 *
 * @author Abhay
 * @param sessionId             id of the login session
 * @param tenantId              tenant the session belongs to
 * @param userId                user the session belongs to
 * @param refreshToken          the raw (unhashed) refresh token to hand to the client
 * @param refreshTokenExpiresAt when this refresh token expires (idle timeout, capped by the session's absolute lifetime)
 */
public record IssuedSession(UUID sessionId, UUID tenantId, UUID userId, String refreshToken,
                            Instant refreshTokenExpiresAt) {
}
