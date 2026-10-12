package com.orbitcrm.identity.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.orbitcrm.shared.entity.BaseEntity;

/**
 * JPA entity for the {@code user_tokens} table: a single-use token sent by email.
 *
 * <p>Purpose: back invitation links ({@code INVITE}, valid 7 days) and password reset links
 * ({@code PASSWORD_RESET}, valid 1 hour). Only the SHA-256 hash is stored. A token is usable once and until it
 * expires; issuing a new token for the same user and purpose invalidates older ones. The tenant id is stored so
 * the token can be resolved before any tenant context exists.
 *
 * @author Abhay
 */
@Entity
@Table(name = "user_tokens")
public class UserToken extends BaseEntity {

    /**
     * What an emailed single-use token is for.
     *
     * <p>Purpose: keep invitation and password reset tokens apart, so a token issued for one flow can never be used in
     * the other. {@code INVITE} tokens are valid for 7 days, {@code PASSWORD_RESET} tokens for 1 hour (defaults).
     */
    public enum Purpose { INVITE, PASSWORD_RESET }

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Purpose purpose;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected UserToken() {
    }

    public UserToken(UUID tenantId, UUID userId, Purpose purpose, String tokenHash, Instant expiresAt) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Purpose getPurpose() {
        return purpose;
    }
}
