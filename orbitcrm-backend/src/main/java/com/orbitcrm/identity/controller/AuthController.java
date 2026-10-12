package com.orbitcrm.identity.controller;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.orbitcrm.identity.dto.AcceptInvitationRequest;
import com.orbitcrm.identity.dto.AuthResponse;
import com.orbitcrm.identity.dto.AuthTokens;
import com.orbitcrm.identity.dto.ChangePasswordRequest;
import com.orbitcrm.identity.dto.ClientInfo;
import com.orbitcrm.identity.dto.ForgotPasswordRequest;
import com.orbitcrm.identity.dto.LoginOutcome;
import com.orbitcrm.identity.dto.LoginRequest;
import com.orbitcrm.identity.dto.LoginResponse;
import com.orbitcrm.identity.dto.MeResponse;
import com.orbitcrm.identity.dto.MfaCodeRequest;
import com.orbitcrm.identity.dto.MfaDisableRequest;
import com.orbitcrm.identity.dto.MfaLoginRequest;
import com.orbitcrm.identity.dto.MfaSetupResponse;
import com.orbitcrm.identity.dto.RefreshRequest;
import com.orbitcrm.identity.dto.RegisterRequest;
import com.orbitcrm.identity.dto.ResetPasswordRequest;
import com.orbitcrm.identity.dto.SessionResponse;
import com.orbitcrm.identity.dto.SlugAvailability;
import com.orbitcrm.identity.dto.UpdateProfileRequest;
import com.orbitcrm.identity.security.RefreshCookies;
import com.orbitcrm.identity.service.AuthService;
import com.orbitcrm.identity.service.RegistrationService;
import com.orbitcrm.shared.dto.ApiErrorResponse;
import com.orbitcrm.shared.dto.ApiResponse;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.web.RequestIdFilter;
import com.orbitcrm.tenant.service.TenantService;

/**
 * REST controller for authentication and the signed-in user's own account at {@code /api/v1/auth}.
 *
 * <p>Purpose: the entry point for everything related to signing in and managing one's own credentials.
 * <ul>
 *   <li><b>Public:</b> {@code POST /register} (create a workspace and its first administrator),
 *   {@code GET /slug-availability}, {@code POST /login} (returns tokens, or {@code mfaRequired} with an
 *   {@code mfaToken}), {@code POST /login/mfa}, {@code POST /refresh}, {@code POST /logout},
 *   {@code POST /password/forgot}, {@code POST /password/reset}, {@code POST /invitations/accept}.</li>
 *   <li><b>Authenticated:</b> {@code POST /password/change}, {@code GET/PATCH /me}, MFA enrolment
 *   ({@code /mfa/setup}, {@code /mfa/enable}, {@code /mfa/disable}) and session management
 *   ({@code GET /sessions}, {@code DELETE /sessions/{id}}).</li>
 * </ul>
 * Every response that starts or rotates a session returns the refresh token in the body and also sets it as the
 * HttpOnly {@code orbitcrm_refresh} cookie. Refresh and logout accept the token either in the body (no CSRF check
 * needed, because only the client that received it knows it) or from the cookie, in which case the
 * {@code X-Requested-With} header is required as CSRF protection. A failed refresh clears the cookie so clients
 * stop retrying with a dead token.
 *
 * @author Abhay
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Signup, login, tokens, passwords, MFA and sessions")
public class AuthController {

    private final AuthService auth;
    private final RegistrationService registration;
    private final TenantService tenants;
    private final RefreshCookies cookies;

    AuthController(AuthService auth, RegistrationService registration, TenantService tenants, RefreshCookies cookies) {
        this.auth = auth;
        this.registration = registration;
        this.tenants = tenants;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    @Operation(summary = "Create a new workspace and its first administrator")
    ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request,
                                                       HttpServletRequest http) {
        AuthTokens tokens = registration.register(request, ClientInfo.from(http));
        return withRefreshCookie(HttpStatus.CREATED, tokens, "Workspace created");
    }

    @GetMapping("/slug-availability")
    @Operation(summary = "Check whether a workspace URL is available")
    ApiResponse<SlugAvailability> slugAvailability(@RequestParam String slug) {
        return ApiResponse.ok(new SlugAvailability(slug, tenants.isSlugAvailable(slug)));
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with workspace, email and password",
            description = "Returns tokens, or {mfaRequired:true, mfaToken} when two-factor authentication is on")
    ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                     HttpServletRequest http) {
        LoginOutcome outcome = auth.login(request, ClientInfo.from(http));
        if (outcome.mfaToken() != null) {
            return ResponseEntity.ok(ApiResponse.ok(LoginResponse.mfa(outcome.mfaToken()),
                    "Two-factor verification required"));
        }
        AuthTokens tokens = outcome.tokens();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(tokens.session().refreshToken(),
                        tokens.session().refreshTokenExpiresAt()))
                .body(ApiResponse.ok(LoginResponse.authenticated(toResponse(tokens)), "Signed in"));
    }

    @PostMapping("/login/mfa")
    @Operation(summary = "Complete sign-in with a TOTP code")
    ResponseEntity<ApiResponse<AuthResponse>> loginMfa(@Valid @RequestBody MfaLoginRequest request,
                                                       HttpServletRequest http) {
        return withRefreshCookie(HttpStatus.OK, auth.loginWithMfa(request, ClientInfo.from(http)), "Signed in");
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh token and get a new access token",
            description = "Send {\"refreshToken\": \"...\"} in the body, or rely on the refresh cookie together with "
                    + "the X-Requested-With header (CSRF protection). The old refresh token stops working.")
    ResponseEntity<?> refresh(@RequestBody(required = false) RefreshRequest body, HttpServletRequest http) {
        String refreshToken = refreshTokenOf(body, http);
        try {
            return withRefreshCookie(HttpStatus.OK, auth.refresh(refreshToken), null);
        } catch (ApiException ex) {
            // Drop a dead cookie so the client stops retrying with it.
            return ResponseEntity.status(ex.status())
                    .header(HttpHeaders.SET_COOKIE, cookies.clear())
                    .body(ApiErrorResponse.of(ex.code(), ex.getMessage(), null, RequestIdFilter.currentRequestId()));
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Sign out this device (revokes the session)",
            description = "Identifies the session by a body refreshToken, or by the cookie plus X-Requested-With")
    ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) RefreshRequest body,
                                             HttpServletRequest http) {
        auth.logout(refreshTokenOf(body, http));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.clear())
                .body(ApiResponse.message("Signed out"));
    }

    @PostMapping("/password/forgot")
    @Operation(summary = "Email a password reset link (always succeeds to avoid account enumeration)")
    ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                                     HttpServletRequest http) {
        auth.forgotPassword(request, ClientInfo.from(http));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.message("If the account exists, a reset link has been sent"));
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Set a new password using a reset link token; signs out all sessions")
    ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        auth.resetPassword(request);
        return ApiResponse.message("Password has been reset. Please sign in.");
    }

    @PostMapping("/invitations/accept")
    @Operation(summary = "Accept an invitation, set a password and sign in")
    ResponseEntity<ApiResponse<AuthResponse>> acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request,
                                                               HttpServletRequest http) {
        return withRefreshCookie(HttpStatus.OK, auth.acceptInvitation(request, ClientInfo.from(http)),
                "Welcome to OrbitCRM");
    }

    @PostMapping("/password/change")
    @Operation(summary = "Change password; signs out your other sessions")
    ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        auth.changePassword(request);
        return ApiResponse.message("Password changed");
    }

    @GetMapping("/me")
    @Operation(summary = "Current user, tenant, role and effective permissions")
    ApiResponse<MeResponse> me() {
        return ApiResponse.ok(auth.me());
    }

    @PatchMapping("/me")
    @Operation(summary = "Update your own profile")
    ApiResponse<MeResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(auth.updateProfile(request), "Profile updated");
    }

    @PostMapping("/mfa/setup")
    @Operation(summary = "Start two-factor enrolment; returns the secret and otpauth:// URI for a QR code")
    ApiResponse<MfaSetupResponse> mfaSetup() {
        return ApiResponse.ok(auth.startMfaSetup());
    }

    @PostMapping("/mfa/enable")
    @Operation(summary = "Confirm enrolment with a code from the authenticator app")
    ApiResponse<Void> mfaEnable(@Valid @RequestBody MfaCodeRequest request) {
        auth.enableMfa(request.code());
        return ApiResponse.message("Two-factor authentication enabled");
    }

    @PostMapping("/mfa/disable")
    @Operation(summary = "Turn off two-factor authentication (password and current code required)")
    ApiResponse<Void> mfaDisable(@Valid @RequestBody MfaDisableRequest request) {
        auth.disableMfa(request);
        return ApiResponse.message("Two-factor authentication disabled");
    }

    @GetMapping("/sessions")
    @Operation(summary = "List your active sessions (devices)")
    ApiResponse<List<SessionResponse>> sessions() {
        return ApiResponse.ok(auth.sessions());
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "Sign out one of your sessions")
    ApiResponse<Void> revokeSession(@PathVariable UUID sessionId) {
        auth.revokeSession(sessionId);
        return ApiResponse.message("Session revoked");
    }

    private ResponseEntity<ApiResponse<AuthResponse>> withRefreshCookie(HttpStatus status, AuthTokens tokens,
                                                                         String message) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.issue(tokens.session().refreshToken(),
                        tokens.session().refreshTokenExpiresAt()))
                .body(ApiResponse.ok(toResponse(tokens), message));
    }

    /**
     * A token in the body is only known to the client that received it, so it needs no CSRF check. The cookie is
     * attached by the browser automatically, so using it requires the custom CSRF header.
     */
    private String refreshTokenOf(RefreshRequest body, HttpServletRequest http) {
        if (body != null && body.refreshToken() != null && !body.refreshToken().isBlank()) {
            return body.refreshToken();
        }
        cookies.requireCsrfHeader(http);
        return cookies.read(http);
    }

    private static AuthResponse toResponse(AuthTokens tokens) {
        return new AuthResponse(tokens.accessToken().value(), "Bearer", tokens.session().refreshToken(),
                tokens.accessToken().expiresInSeconds(), tokens.me());
    }
}
