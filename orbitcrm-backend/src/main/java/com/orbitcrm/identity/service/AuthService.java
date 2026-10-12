package com.orbitcrm.identity.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbitcrm.audit.dto.AuditEntry;
import com.orbitcrm.audit.model.AuditAction;
import com.orbitcrm.audit.service.AuditService;
import com.orbitcrm.identity.dto.AcceptInvitationRequest;
import com.orbitcrm.identity.dto.AuthTokens;
import com.orbitcrm.identity.dto.ChangePasswordRequest;
import com.orbitcrm.identity.dto.ClientInfo;
import com.orbitcrm.identity.dto.ForgotPasswordRequest;
import com.orbitcrm.identity.dto.IssuedSession;
import com.orbitcrm.identity.dto.IssuedToken;
import com.orbitcrm.identity.dto.LoginOutcome;
import com.orbitcrm.identity.dto.LoginRequest;
import com.orbitcrm.identity.dto.MeResponse;
import com.orbitcrm.identity.dto.MfaChallenge;
import com.orbitcrm.identity.dto.MfaDisableRequest;
import com.orbitcrm.identity.dto.MfaLoginRequest;
import com.orbitcrm.identity.dto.MfaSetupResponse;
import com.orbitcrm.identity.dto.ResetPasswordRequest;
import com.orbitcrm.identity.dto.SessionResponse;
import com.orbitcrm.identity.dto.SessionRotation;
import com.orbitcrm.identity.dto.UpdateProfileRequest;
import com.orbitcrm.identity.entity.User;
import com.orbitcrm.identity.entity.UserToken;
import com.orbitcrm.identity.exception.InvalidPrincipalException;
import com.orbitcrm.identity.mapper.MeAssembler;
import com.orbitcrm.identity.repository.UserRepository;
import com.orbitcrm.identity.security.JwtService;
import com.orbitcrm.identity.security.LoginThrottle;
import com.orbitcrm.identity.security.PrincipalService;
import com.orbitcrm.identity.util.PasswordPolicy;
import com.orbitcrm.identity.util.Totp;
import com.orbitcrm.shared.config.OrbitProperties;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.security.AccessControl;
import com.orbitcrm.shared.security.CurrentUser;
import com.orbitcrm.shared.security.SecretCipher;
import com.orbitcrm.shared.tenant.TenantContext;
import com.orbitcrm.tenant.dto.TenantInfo;
import com.orbitcrm.tenant.service.TenantService;

/**
 * Implements all authentication flows of {@code AuthController}.
 *
 * <p>Purpose: the security core of sign-in and credential management:
 * <ul>
 *   <li><b>Login</b> - resolves the workspace by slug, checks the password (with account lockout after repeated
 *   failures), and either issues tokens or returns an MFA challenge. Unknown workspaces or accounts still run a
 *   dummy hash comparison, so response time does not reveal whether an account exists; every failure returns the
 *   same {@code INVALID_CREDENTIALS} error.</li>
 *   <li><b>MFA login</b> - verifies the challenge token and the TOTP code, rejecting replays of an already used
 *   code.</li>
 *   <li><b>Refresh and logout</b> - rotates the refresh token through {@code SessionService}; logout revokes the
 *   session identified by the refresh token and/or the current access token.</li>
 *   <li><b>Password lifecycle</b> - forgot password (always succeeds, counted against the IP throttle), reset via
 *   emailed token (signs out all sessions), change password (signs out the other sessions).</li>
 *   <li><b>Invitations</b> - accepting sets the password, activates the user and signs them in.</li>
 *   <li><b>Profile, MFA enrolment and session management</b> for the signed-in user.</li>
 * </ul>
 * Pre-authentication flows bind the tenant explicitly with {@code TenantContext.callAs} before starting their
 * transaction, and failed-attempt counters are committed before the error is raised. Security-relevant events
 * (login, failed login, logout, password and MFA changes) are written to the audit log.
 *
 * @author Abhay
 */
@Service
public class AuthService {

    private static final String MFA_ISSUER = "OrbitCRM";

    private final TenantService tenants;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessions;
    private final UserTokenService userTokens;
    private final JwtService jwt;
    private final PrincipalService principals;
    private final LoginThrottle throttle;
    private final SecretCipher cipher;
    private final IdentityMailer mailer;
    private final MeAssembler meAssembler;
    private final AuditService audit;
    private final OrbitProperties.LoginProtection loginProtection;
    private final TransactionTemplate tx;
    private final String timingDummyHash;

    AuthService(TenantService tenants, UserRepository users, PasswordEncoder passwordEncoder,
                SessionService sessions, UserTokenService userTokens, JwtService jwt, PrincipalService principals,
                LoginThrottle throttle, SecretCipher cipher, IdentityMailer mailer, MeAssembler meAssembler,
                AuditService audit, OrbitProperties properties, PlatformTransactionManager txManager) {
        this.tenants = tenants;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.userTokens = userTokens;
        this.jwt = jwt;
        this.principals = principals;
        this.throttle = throttle;
        this.cipher = cipher;
        this.mailer = mailer;
        this.meAssembler = meAssembler;
        this.audit = audit;
        this.loginProtection = properties.security().login();
        this.tx = new TransactionTemplate(txManager);
        this.timingDummyHash = passwordEncoder.encode("timing-equalisation-" + UUID.randomUUID());
    }

    // ------------------------------------------------------------------ login

    public LoginOutcome login(LoginRequest request, ClientInfo client) {
        throttle.check(client.ipAddress());
        Optional<TenantInfo> tenant = tenants.findBySlug(request.tenant()).filter(TenantInfo::active);
        if (tenant.isEmpty()) {
            burnPasswordCheck(request.password());
            throw loginFailed(client);
        }
        UUID tenantId = tenant.get().id();
        PasswordStep step = TenantContext.callAs(tenantId,
                () -> tx.execute(status -> passwordStep(tenantId, request, client)));
        if (step.failed()) {
            throw loginFailed(client);
        }
        if (step.mfaToken() != null) {
            return LoginOutcome.mfa(step.mfaToken());
        }
        return LoginOutcome.authenticated(complete(step.session()));
    }

    private PasswordStep passwordStep(UUID tenantId, LoginRequest request, ClientInfo client) {
        Instant now = Instant.now();
        User user = users.findByEmail(User.normalizeEmail(request.email())).orElse(null);
        if (user == null || user.getPasswordHash() == null || !user.isActive() || user.isLocked(now)) {
            burnPasswordCheck(request.password());
            return PasswordStep.failure();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.recordFailedLogin(loginProtection.maxFailedAttempts(), now.plus(loginProtection.lockDuration()));
            auditSecurity(AuditAction.LOGIN_FAILED, user, Map.of("reason", "INVALID_PASSWORD"));
            return PasswordStep.failure();
        }
        if (user.isMfaEnabled()) {
            return PasswordStep.mfa(jwt.issueMfaToken(tenantId, user.getId()));
        }
        user.recordSuccessfulLogin(now);
        auditSecurity(AuditAction.LOGIN, user, Map.of("mfa", false));
        return PasswordStep.success(sessions.create(tenantId, user.getId(), client));
    }

    public AuthTokens loginWithMfa(MfaLoginRequest request, ClientInfo client) {
        throttle.check(client.ipAddress());
        MfaChallenge challenge = jwt.verifyMfaToken(request.mfaToken());
        PasswordStep step = TenantContext.callAs(challenge.tenantId(), () -> tx.execute(status -> {
            Instant now = Instant.now();
            User user = users.findById(challenge.userId())
                    .filter(u -> u.isActive() && u.isMfaEnabled() && !u.isLocked(now))
                    .orElse(null);
            if (user == null) {
                return PasswordStep.failure();
            }
            OptionalLong step1 = verifyTotp(user.getMfaSecret(), request.code(), user.getMfaLastUsedStep());
            if (step1.isEmpty()) {
                user.recordFailedLogin(loginProtection.maxFailedAttempts(), now.plus(loginProtection.lockDuration()));
                auditSecurity(AuditAction.LOGIN_FAILED, user, Map.of("reason", "INVALID_MFA_CODE"));
                return PasswordStep.failure();
            }
            user.markMfaStepUsed(step1.getAsLong());
            user.recordSuccessfulLogin(now);
            auditSecurity(AuditAction.LOGIN, user, Map.of("mfa", true));
            return PasswordStep.success(sessions.create(challenge.tenantId(), user.getId(), client));
        }));
        if (step.failed()) {
            throttle.recordFailure(client.ipAddress());
            throw ApiException.unauthorized("INVALID_MFA_CODE", "The verification code is incorrect or expired");
        }
        return complete(step.session());
    }

    // ------------------------------------------------------------------ tokens

    public AuthTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw sessionExpired();
        }
        SessionRotation rotation = sessions.rotate(rawRefreshToken);
        if (!rotation.succeeded()) {
            throw sessionExpired();
        }
        IssuedSession session = rotation.session();
        try {
            return complete(session);
        } catch (InvalidPrincipalException ex) {
            sessions.revoke(session.tenantId(), session.userId(), session.sessionId(), "PRINCIPAL_INVALID");
            throw sessionExpired();
        }
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            sessions.revokeByRefreshToken(rawRefreshToken, "LOGOUT");
        }
        AccessControl.currentUser().ifPresent(user -> {
            sessions.revoke(user.tenantId(), user.userId(), user.sessionId(), "LOGOUT");
            audit.record(AuditEntry.security(AuditAction.LOGOUT, user.userId(), user.displayName(), null));
        });
    }

    private AuthTokens complete(IssuedSession session) {
        CurrentUser principal = principals.load(session.tenantId(), session.userId(), session.sessionId());
        MeResponse me = TenantContext.callAs(session.tenantId(), () -> meAssembler.assemble(principal));
        IssuedToken access = jwt.issueAccessToken(session.tenantId(), session.userId(),
                session.sessionId());
        return new AuthTokens(access, session, me);
    }

    // ------------------------------------------------------------------ password lifecycle

    public void forgotPassword(ForgotPasswordRequest request, ClientInfo client) {
        throttle.check(client.ipAddress());
        // Counted like a failure so the endpoint cannot be used to spam a mailbox.
        throttle.recordFailure(client.ipAddress());
        tenants.findBySlug(request.tenant()).filter(TenantInfo::active).ifPresent(tenant ->
                TenantContext.runAs(tenant.id(), () -> tx.executeWithoutResult(status ->
                        users.findByEmail(User.normalizeEmail(request.email()))
                                .filter(User::isActive)
                                .ifPresent(user -> {
                                    String raw = userTokens.issue(tenant.id(), user.getId(),
                                            UserToken.Purpose.PASSWORD_RESET);
                                    mailer.sendPasswordReset(user.getEmail(), user.getFirstName(), raw);
                                }))));
    }

    public void resetPassword(ResetPasswordRequest request) {
        UUID tenantId = userTokens.tenantOf(request.token(), UserToken.Purpose.PASSWORD_RESET)
                .orElseThrow(UserTokenService::invalidToken);
        TenantContext.runAs(tenantId, () -> tx.executeWithoutResult(status -> {
            UUID userId = userTokens.consume(request.token(), UserToken.Purpose.PASSWORD_RESET, tenantId);
            User user = users.findById(userId).filter(User::isActive).orElseThrow(UserTokenService::invalidToken);
            PasswordPolicy.validate(request.newPassword(), user.getEmail(), "newPassword");
            user.setPassword(passwordEncoder.encode(request.newPassword()), Instant.now());
            sessions.revokeAll(tenantId, userId, "PASSWORD_RESET");
            auditSecurity(AuditAction.PASSWORD_RESET, user, null);
        }));
    }

    public AuthTokens acceptInvitation(AcceptInvitationRequest request, ClientInfo client) {
        UUID tenantId = userTokens.tenantOf(request.token(), UserToken.Purpose.INVITE)
                .orElseThrow(UserTokenService::invalidToken);
        IssuedSession session = TenantContext.callAs(tenantId, () -> tx.execute(status -> {
            UUID userId = userTokens.consume(request.token(), UserToken.Purpose.INVITE, tenantId);
            User user = users.findById(userId)
                    .filter(u -> u.getStatus() == User.Status.INVITED)
                    .orElseThrow(UserTokenService::invalidToken);
            PasswordPolicy.validate(request.password(), user.getEmail(), "password");
            user.updateProfile(request.firstName(), request.lastName(), null, null, null, null);
            Instant now = Instant.now();
            user.setPassword(passwordEncoder.encode(request.password()), now);
            user.activate();
            user.recordSuccessfulLogin(now);
            return sessions.create(tenantId, userId, client);
        }));
        return complete(session);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        CurrentUser principal = AccessControl.requireUser();
        User user = currentUserEntity(principal);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.validation("currentPassword", "INVALID_PASSWORD", "Current password is incorrect");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw ApiException.validation("newPassword", "UNCHANGED", "New password must differ from the current one");
        }
        PasswordPolicy.validate(request.newPassword(), user.getEmail(), "newPassword");
        user.setPassword(passwordEncoder.encode(request.newPassword()), Instant.now());
        sessions.revokeAllExcept(principal.tenantId(), principal.userId(), principal.sessionId(), "PASSWORD_CHANGED");
        auditSecurity(AuditAction.PASSWORD_CHANGED, user, null);
    }

    // ------------------------------------------------------------------ profile

    public MeResponse me() {
        return meAssembler.assemble(AccessControl.requireUser());
    }

    @Transactional
    public MeResponse updateProfile(UpdateProfileRequest request) {
        CurrentUser principal = AccessControl.requireUser();
        User user = currentUserEntity(principal);
        if (request.timezone() != null && !request.timezone().isBlank()) {
            try {
                java.time.ZoneId.of(request.timezone());
            } catch (java.time.DateTimeException ex) {
                throw ApiException.validation("timezone", "INVALID", "Unknown time zone");
            }
        }
        user.updateProfile(request.firstName(), request.lastName(), request.jobTitle(), request.phone(),
                request.timezone(), request.locale());
        users.flush();
        principals.evictUser(principal.tenantId(), principal.userId());
        return meAssembler.assemble(principal);
    }

    // ------------------------------------------------------------------ MFA

    @Transactional
    public MfaSetupResponse startMfaSetup() {
        CurrentUser principal = AccessControl.requireUser();
        User user = currentUserEntity(principal);
        if (user.isMfaEnabled()) {
            throw ApiException.conflict("MFA_ALREADY_ENABLED", "Two-factor authentication is already enabled");
        }
        String secret = Totp.newSecret();
        user.startMfaEnrollment(cipher.encrypt(secret));
        return new MfaSetupResponse(secret, Totp.otpauthUri(MFA_ISSUER, user.getEmail(), secret));
    }

    @Transactional
    public void enableMfa(String code) {
        User user = currentUserEntity(AccessControl.requireUser());
        if (user.getMfaPendingSecret() == null) {
            throw ApiException.badRequest("MFA_SETUP_REQUIRED", "Start two-factor setup first");
        }
        OptionalLong step = Totp.verify(cipher.decrypt(user.getMfaPendingSecret()), code,
                Instant.now().getEpochSecond(), 1);
        if (step.isEmpty()) {
            throw ApiException.validation("code", "INVALID_MFA_CODE", "The verification code is incorrect");
        }
        user.enableMfa();
        user.markMfaStepUsed(step.getAsLong());
        auditSecurity(AuditAction.MFA_ENABLED, user, null);
    }

    @Transactional
    public void disableMfa(MfaDisableRequest request) {
        User user = currentUserEntity(AccessControl.requireUser());
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.validation("password", "INVALID_PASSWORD", "Password is incorrect");
        }
        if (user.isMfaEnabled() && verifyTotp(user.getMfaSecret(), request.code(), user.getMfaLastUsedStep()).isEmpty()) {
            throw ApiException.validation("code", "INVALID_MFA_CODE", "The verification code is incorrect");
        }
        user.disableMfa();
        auditSecurity(AuditAction.MFA_DISABLED, user, null);
    }

    // ------------------------------------------------------------------ sessions

    public List<SessionResponse> sessions() {
        CurrentUser principal = AccessControl.requireUser();
        return sessions.listActive(principal.tenantId(), principal.userId()).stream()
                .map(s -> new SessionResponse(s.getId(), s.getCreatedAt(), s.getLastUsedAt(), s.getExpiresAt(),
                        s.getUserAgent(), s.getIpAddress(), s.getId().equals(principal.sessionId())))
                .toList();
    }

    public void revokeSession(UUID sessionId) {
        CurrentUser principal = AccessControl.requireUser();
        if (!sessions.revoke(principal.tenantId(), principal.userId(), sessionId, "USER_REVOKED")) {
            throw ApiException.notFound("SESSION_NOT_FOUND", "Session does not exist");
        }
    }

    // ------------------------------------------------------------------ helpers

    private User currentUserEntity(CurrentUser principal) {
        return users.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "User no longer exists"));
    }

    private OptionalLong verifyTotp(String encryptedSecret, String code, Long lastUsedStep) {
        if (encryptedSecret == null) {
            return OptionalLong.empty();
        }
        OptionalLong step = Totp.verify(cipher.decrypt(encryptedSecret), code, Instant.now().getEpochSecond(), 1);
        if (step.isPresent() && lastUsedStep != null && step.getAsLong() <= lastUsedStep) {
            return OptionalLong.empty(); // replay of an already used code
        }
        return step;
    }

    private void auditSecurity(AuditAction action, User user, Map<String, Object> metadata) {
        audit.record(AuditEntry.security(action, user.getId(), user.displayName(), metadata));
    }

    /** Runs a hash comparison anyway so response time does not reveal whether the account exists. */
    private void burnPasswordCheck(String password) {
        passwordEncoder.matches(password, timingDummyHash);
    }

    private ApiException loginFailed(ClientInfo client) {
        throttle.recordFailure(client.ipAddress());
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "Invalid workspace, email or password, or the account is temporarily locked");
    }

    private static ApiException sessionExpired() {
        return ApiException.unauthorized("SESSION_EXPIRED", "Your session has expired. Please sign in again.");
    }

    /**
     * Result of the password (or MFA) step of a login, computed inside the login transaction.
     *
     * <p>Purpose: let the transaction commit failed-attempt counters and audit entries before the caller raises the
     * login error. Exactly one outcome applies: failure, MFA challenge required, or success with a new session.
     *
     * @param failed   whether the credentials were rejected
     * @param mfaToken MFA challenge token when a second factor is required
     * @param session  the created session when the login succeeded
     */
    private record PasswordStep(boolean failed, String mfaToken, IssuedSession session) {
        static PasswordStep failure() {
            return new PasswordStep(true, null, null);
        }

        static PasswordStep mfa(String token) {
            return new PasswordStep(false, token, null);
        }

        static PasswordStep success(IssuedSession session) {
            return new PasswordStep(false, null, session);
        }
    }
}
