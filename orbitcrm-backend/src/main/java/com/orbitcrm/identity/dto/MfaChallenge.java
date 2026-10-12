package com.orbitcrm.identity.dto;

import java.util.UUID;

/**
 * Identity extracted from a valid MFA challenge token.
 *
 * <p>Purpose: returned by {@code JwtService.verifyMfaToken} so the MFA login step knows which user in which
 * tenant must present the TOTP code.
 *
 * @author Abhay
 * @param tenantId tenant of the user who passed the password step
 * @param userId   the user who passed the password step
 */
public record MfaChallenge(UUID tenantId, UUID userId) {
}
