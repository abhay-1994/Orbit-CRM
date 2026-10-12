package com.orbitcrm.identity.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.orbitcrm.shared.entity.TenantScopedEntity;

/**
 * JPA entity for the {@code users} table: a person who can sign in to a tenant.
 *
 * <p>Purpose: hold the account and its security state:
 * <ul>
 *   <li>identity and profile: email (normalized to lower case, unique per tenant), name, job title, phone,
 *   personal time zone and locale;</li>
 *   <li>status: {@code INVITED} (has not set a password yet), {@code ACTIVE} or {@code DISABLED};</li>
 *   <li>authorization: role and optional team;</li>
 *   <li>credentials: password hash and change time;</li>
 *   <li>two-factor authentication: encrypted TOTP secret, a pending secret during enrolment and the last used
 *   time step (to reject replayed codes);</li>
 *   <li>brute-force protection: failed attempt counter and lock expiry;</li>
 *   <li>last successful login time.</li>
 * </ul>
 * State changes go through intention-revealing methods such as {@code recordFailedLogin}, {@code setPassword},
 * {@code enableMfa} and {@code changeStatus}.
 *
 * @author Abhay
 */
@Entity
@Table(name = "users")
public class User extends TenantScopedEntity {

    /**
     * Lifecycle status of a user account.
     *
     * <p>Purpose: control who may sign in. {@code INVITED} users have been invited but have not set a password yet;
     * {@code ACTIVE} users can sign in and own records; {@code DISABLED} users are blocked and their sessions revoked,
     * while their records and history remain.
     */
    public enum Status { INVITED, ACTIVE, DISABLED }

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "team_id")
    private UUID teamId;

    @Column(name = "job_title")
    private String jobTitle;

    private String phone;

    private String timezone;

    private String locale;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    @Column(name = "mfa_secret")
    private String mfaSecret;

    @Column(name = "mfa_pending_secret")
    private String mfaPendingSecret;

    @Column(name = "mfa_last_used_step")
    private Long mfaLastUsedStep;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    protected User() {
    }

    public User(String email, String firstName, String lastName, UUID roleId, UUID teamId, Status status) {
        this.email = normalizeEmail(email);
        this.firstName = firstName.trim();
        this.lastName = lastName.trim();
        this.roleId = roleId;
        this.teamId = teamId;
        this.status = status;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public String displayName() {
        return (firstName + " " + lastName).trim();
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void setPassword(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        this.passwordChangedAt = now;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public void activate() {
        this.status = Status.ACTIVE;
    }

    public void changeStatus(Status status) {
        this.status = status;
    }

    public void recordFailedLogin(int maxAttempts, Instant lockUntil) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = lockUntil;
            failedLoginAttempts = 0;
        }
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public void updateProfile(String firstName, String lastName, String jobTitle, String phone,
                              String timezone, String locale) {
        if (firstName != null) {
            this.firstName = firstName.trim();
        }
        if (lastName != null) {
            this.lastName = lastName.trim();
        }
        if (jobTitle != null) {
            this.jobTitle = blankToNull(jobTitle);
        }
        if (phone != null) {
            this.phone = blankToNull(phone);
        }
        if (timezone != null) {
            this.timezone = blankToNull(timezone);
        }
        if (locale != null) {
            this.locale = blankToNull(locale);
        }
    }

    public void assign(UUID roleId, UUID teamId) {
        this.roleId = roleId;
        this.teamId = teamId;
    }

    public void startMfaEnrollment(String encryptedSecret) {
        this.mfaPendingSecret = encryptedSecret;
    }

    public void enableMfa() {
        this.mfaSecret = mfaPendingSecret;
        this.mfaPendingSecret = null;
        this.mfaEnabled = true;
    }

    public void disableMfa() {
        this.mfaEnabled = false;
        this.mfaSecret = null;
        this.mfaPendingSecret = null;
        this.mfaLastUsedStep = null;
    }

    public void markMfaStepUsed(long step) {
        this.mfaLastUsedStep = step;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public Status getStatus() {
        return status;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public String getPhone() {
        return phone;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getLocale() {
        return locale;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public String getMfaSecret() {
        return mfaSecret;
    }

    public String getMfaPendingSecret() {
        return mfaPendingSecret;
    }

    public Long getMfaLastUsedStep() {
        return mfaLastUsedStep;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
