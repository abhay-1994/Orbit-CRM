package com.orbitcrm.identity.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.orbitcrm.shared.entity.BaseEntity;

/**
 * JPA entity for the {@code refresh_tokens} table: one single-use refresh token of a session.
 *
 * <p>Purpose: implement rotating refresh tokens with reuse detection. Only the SHA-256 hash of the token is stored.
 * Each refresh marks the presented token as used and issues a new one. Presenting a token that was already used
 * means it was stolen or replayed, so the whole session is revoked - except within a short leeway (30 seconds by
 * default), which tolerates two browser tabs refreshing at the same moment.
 *
 * @author Abhay
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected RefreshToken() {
    }

    public RefreshToken(UUID sessionId, String tokenHash, Instant expiresAt) {
        this.sessionId = sessionId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean usedWithin(java.time.Duration leeway, Instant now) {
        return usedAt != null && !usedAt.plus(leeway).isBefore(now);
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    public UUID getSessionId() {
        return sessionId;
    }
}
