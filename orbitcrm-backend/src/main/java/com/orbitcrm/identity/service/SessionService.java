package com.orbitcrm.identity.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbitcrm.identity.dto.ClientInfo;
import com.orbitcrm.identity.dto.IssuedSession;
import com.orbitcrm.identity.dto.SessionRotation;
import com.orbitcrm.identity.entity.AuthSession;
import com.orbitcrm.identity.entity.RefreshToken;
import com.orbitcrm.identity.repository.AuthSessionRepository;
import com.orbitcrm.identity.repository.RefreshTokenRepository;
import com.orbitcrm.identity.security.PrincipalService;
import com.orbitcrm.shared.config.OrbitProperties;
import com.orbitcrm.shared.security.Tokens;

/**
 * Manages login sessions and their rotating refresh tokens.
 *
 * <p>Purpose: implement the session lifecycle securely:
 * <ul>
 *   <li>{@code create} opens a session (absolute lifetime 30 days by default) and issues the first refresh token
 *   (idle lifetime 7 days by default, never beyond the session's end);</li>
 *   <li>{@code rotate} exchanges a refresh token for a new one under a row lock. A token presented a second time
 *   indicates theft or replay, so the whole session is revoked; a repeat within a short leeway is tolerated for
 *   concurrent browser tabs;</li>
 *   <li>revocation of one session, of all sessions of a user, or of all but the current one, each evicting the
 *   affected principals from the cache;</li>
 *   <li>listing a user's active sessions.</li>
 * </ul>
 * Only token hashes are stored.
 *
 * @author Abhay
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final AuthSessionRepository sessions;
    private final RefreshTokenRepository refreshTokens;
    private final PrincipalService principals;
    private final OrbitProperties.Session config;

    SessionService(AuthSessionRepository sessions, RefreshTokenRepository refreshTokens,
                   PrincipalService principals, OrbitProperties properties) {
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.principals = principals;
        this.config = properties.security().session();
    }

    @Transactional
    public IssuedSession create(UUID tenantId, UUID userId, ClientInfo client) {
        Instant now = Instant.now();
        AuthSession session = sessions.save(new AuthSession(tenantId, userId, now, now.plus(config.absoluteTtl()),
                client.userAgent(), client.ipAddress()));
        return issueRefreshToken(session, now);
    }

    /**
     * Exchanges a refresh token for a new one. A token that was already used signals theft or replay:
     * the whole session is revoked and the caller must sign in again.
     */
    @Transactional
    public SessionRotation rotate(String rawToken) {
        Instant now = Instant.now();
        Optional<RefreshToken> found = refreshTokens.findByHashForUpdate(Tokens.sha256(rawToken));
        if (found.isEmpty()) {
            return SessionRotation.invalid();
        }
        RefreshToken token = found.get();
        AuthSession session = sessions.findById(token.getSessionId()).orElse(null);
        if (session == null) {
            return SessionRotation.invalid();
        }
        if (token.isUsed() && token.usedWithin(config.refreshReuseLeeway(), now) && session.isActive(now)) {
            // Two browser tabs refreshing at the same moment: tolerate within a short leeway instead of
            // treating it as theft, and give the late caller its own fresh token.
            session.touch(now);
            return new SessionRotation(issueRefreshToken(session, now));
        }
        if (token.isUsed()) {
            log.warn("Refresh token reuse detected; revoking session {}", session.getId());
            session.revoke(now, "TOKEN_REUSE");
            principals.evictSession(session.getId());
            return SessionRotation.invalid();
        }
        if (token.isExpired(now) || !session.isActive(now)) {
            return SessionRotation.invalid();
        }
        token.markUsed(now);
        session.touch(now);
        return new SessionRotation(issueRefreshToken(session, now));
    }

    @Transactional
    public Optional<AuthSession> revokeByRefreshToken(String rawToken, String reason) {
        return refreshTokens.findByHashForUpdate(Tokens.sha256(rawToken))
                .flatMap(token -> sessions.findById(token.getSessionId()))
                .map(session -> {
                    revoke(session, reason);
                    return session;
                });
    }

    @Transactional
    public boolean revoke(UUID tenantId, UUID userId, UUID sessionId, String reason) {
        return sessions.findById(sessionId)
                .filter(s -> s.getTenantId().equals(tenantId) && s.getUserId().equals(userId))
                .map(s -> {
                    revoke(s, reason);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public void revokeAll(UUID tenantId, UUID userId, String reason) {
        List<UUID> ids = sessions.findUnrevokedIds(tenantId, userId);
        sessions.revokeAll(tenantId, userId, Instant.now(), reason);
        ids.forEach(principals::evictSession);
    }

    @Transactional
    public void revokeAllExcept(UUID tenantId, UUID userId, UUID keepSessionId, String reason) {
        List<UUID> ids = sessions.findUnrevokedIds(tenantId, userId);
        sessions.revokeAllExcept(tenantId, userId, keepSessionId, Instant.now(), reason);
        ids.stream().filter(id -> !id.equals(keepSessionId)).forEach(principals::evictSession);
    }

    @Transactional(readOnly = true)
    public List<AuthSession> listActive(UUID tenantId, UUID userId) {
        return sessions.findActive(tenantId, userId, Instant.now());
    }

    private void revoke(AuthSession session, String reason) {
        session.revoke(Instant.now(), reason);
        principals.evictSession(session.getId());
    }

    private IssuedSession issueRefreshToken(AuthSession session, Instant now) {
        String raw = Tokens.newToken();
        Instant idleExpiry = now.plus(config.refreshTokenTtl());
        Instant expiresAt = idleExpiry.isBefore(session.getExpiresAt()) ? idleExpiry : session.getExpiresAt();
        refreshTokens.save(new RefreshToken(session.getId(), Tokens.sha256(raw), expiresAt));
        return new IssuedSession(session.getId(), session.getTenantId(), session.getUserId(), raw, expiresAt);
    }
}
