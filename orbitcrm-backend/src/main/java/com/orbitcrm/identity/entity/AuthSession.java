package com.orbitcrm.identity.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.orbitcrm.shared.entity.BaseEntity;

/**
 * JPA entity for the {@code auth_sessions} table: one login on one device.
 *
 * <p>Purpose: the unit of sign-in and sign-out. Refresh tokens rotate inside a session; the access token carries
 * the session id ({@code sid} claim), so revoking the session logs that device out everywhere, including access
 * tokens that have not expired yet. A session has an absolute lifetime (30 days by default) regardless of
 * activity, records the device's user agent and IP address for the sessions list, and keeps the time and reason
 * of revocation (logout, password change, token reuse, user disabled...).
 *
 * @author Abhay
 */
@Entity
@Table(name = "auth_sessions")
public class AuthSession extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason")
    private String revokedReason;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "ip_address")
    private String ipAddress;

    protected AuthSession() {
    }

    public AuthSession(UUID tenantId, UUID userId, Instant now, Instant expiresAt, String userAgent, String ipAddress) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.lastUsedAt = now;
        this.expiresAt = expiresAt;
        this.userAgent = truncate(userAgent, 400);
        this.ipAddress = truncate(ipAddress, 64);
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void touch(Instant now) {
        this.lastUsedAt = now;
    }

    public void revoke(Instant now, String reason) {
        if (revokedAt == null) {
            this.revokedAt = now;
            this.revokedReason = reason;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getIpAddress() {
        return ipAddress;
    }
}
