package com.orbitcrm.identity.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbitcrm.identity.entity.UserToken;
import com.orbitcrm.identity.repository.UserTokenRepository;
import com.orbitcrm.shared.config.OrbitProperties;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.security.Tokens;

/**
 * Issues and consumes single-use emailed tokens for invitations and password resets.
 *
 * <p>Purpose: provide secure links: 256-bit random tokens of which only the hash is stored, with lifetimes of 7 days
 * (invitations) and 1 hour (password resets). Issuing a new token invalidates the user's outstanding tokens of the
 * same purpose. {@link #tenantOf} finds a token's tenant before any tenant context exists; {@link #consume} marks it
 * used under a row lock inside that tenant's context. Invalid, expired or used tokens all produce the same
 * {@code TOKEN_INVALID} error.
 *
 * @author Abhay
 */
@Service
public class UserTokenService {

    private final UserTokenRepository tokens;
    private final OrbitProperties.Session config;

    UserTokenService(UserTokenRepository tokens, OrbitProperties properties) {
        this.tokens = tokens;
        this.config = properties.security().session();
    }

    @Transactional
    public String issue(UUID tenantId, UUID userId, UserToken.Purpose purpose) {
        Instant now = Instant.now();
        tokens.invalidateOutstanding(tenantId, userId, purpose, now);
        Duration ttl = purpose == UserToken.Purpose.INVITE ? config.inviteTtl() : config.passwordResetTtl();
        String raw = Tokens.newToken();
        tokens.save(new UserToken(tenantId, userId, purpose, Tokens.sha256(raw), now.plus(ttl)));
        return raw;
    }

    /** Looks up which tenant a token belongs to, before any tenant context exists. */
    @Transactional(readOnly = true)
    public Optional<UUID> tenantOf(String rawToken, UserToken.Purpose purpose) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return tokens.findByHash(Tokens.sha256(rawToken))
                .filter(t -> t.getPurpose() == purpose)
                .map(UserToken::getTenantId);
    }

    /** Marks the token used and returns its user id; must run in the token's tenant context. */
    @Transactional
    public UUID consume(String rawToken, UserToken.Purpose purpose, UUID tenantId) {
        Instant now = Instant.now();
        UserToken token = tokens.findByHashForUpdate(Tokens.sha256(rawToken))
                .filter(t -> t.getPurpose() == purpose && t.getTenantId().equals(tenantId))
                .filter(t -> t.isUsable(now))
                .orElseThrow(UserTokenService::invalidToken);
        token.markUsed(now);
        return token.getUserId();
    }

    public static ApiException invalidToken() {
        return ApiException.badRequest("TOKEN_INVALID", "This link is invalid or has expired");
    }
}
