package com.orbitcrm.identity.security;

import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import com.orbitcrm.identity.exception.InvalidPrincipalException;
import com.orbitcrm.shared.exception.ApiException;

/**
 * Converts a signature-valid access token into an authenticated {@link OrbitAuthenticationToken}.
 *
 * <p>Purpose: bridge Spring Security's resource server support and OrbitCRM's principal model. It reads the tenant,
 * session and user ids from the token and loads the {@code CurrentUser} through {@link PrincipalService}. Tokens
 * whose session was revoked, whose user was disabled or whose tenant was suspended after issue - as well as
 * malformed tokens - are rejected as invalid bearer tokens (401).
 *
 * @author Abhay
 */
@Component
public class OrbitJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final PrincipalService principals;

    OrbitJwtAuthenticationConverter(PrincipalService principals) {
        this.principals = principals;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            UUID tenantId = UUID.fromString(jwt.getClaimAsString(JwtService.CLAIM_TENANT));
            UUID sessionId = UUID.fromString(jwt.getClaimAsString(JwtService.CLAIM_SESSION));
            UUID userId = UUID.fromString(jwt.getSubject());
            return new OrbitAuthenticationToken(principals.load(tenantId, userId, sessionId), jwt);
        } catch (InvalidPrincipalException | ApiException ex) {
            throw new InvalidBearerTokenException(ex.getMessage());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new InvalidBearerTokenException("Malformed access token");
        }
    }
}
