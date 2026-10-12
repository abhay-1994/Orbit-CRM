package com.orbitcrm.shared.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed binding of all application-specific settings under the {@code orbitcrm.*} prefix.
 *
 * <p>Purpose: keep configuration in one validated, strongly typed structure instead of scattered
 * {@code @Value} strings. Defaults live in {@code application.yml}; environments override them with environment
 * variables (for example {@code ORBITCRM_JWT_SECRET}). Consumers include the JWT service, session and cookie
 * handling, login throttling, secret encryption, CORS, email links and API rate limiting.
 *
 * @author Abhay
 * @param version   application version shown in the API documentation
 * @param security  JWT, session lifetimes, refresh cookie, login protection, encryption key and principal cache
 * @param web       allowed CORS origins and the frontend base URL used to build invitation and reset links
 * @param mail      sender address and whether email bodies may be logged (development only)
 * @param rateLimit API rate limits per authenticated user and per anonymous client IP
 */
@ConfigurationProperties("orbitcrm")
public record OrbitProperties(String version, Security security, Web web, Mail mail, RateLimit rateLimit) {

    /**
     * API rate limit settings ({@code orbitcrm.rate-limit.*}).
     *
     * <p>Purpose: configure the token-bucket limits applied by the rate-limit filter.
     *
     * @param enabled                whether rate limiting is active
     * @param authenticatedPerMinute requests per minute per authenticated user (default 600)
     * @param anonymousPerMinute     requests per minute per client IP for unauthenticated calls (default 120)
     */
    public record RateLimit(boolean enabled, long authenticatedPerMinute, long anonymousPerMinute) {
    }

    /**
     * Security settings ({@code orbitcrm.security.*}).
     *
     * <p>Purpose: group everything related to authentication and secrets.
     *
     * @param jwt               token signing and lifetimes
     * @param session           session, refresh token and emailed token lifetimes
     * @param refreshCookie     refresh cookie attributes
     * @param login             brute-force protection limits
     * @param encryptionKey     base64 32-byte AES key for secrets at rest (required in production)
     * @param principalCacheTtl how long a resolved principal is cached (default 30 seconds)
     */
    public record Security(Jwt jwt, Session session, RefreshCookie refreshCookie, LoginProtection login,
                           String encryptionKey, Duration principalCacheTtl) {
    }

    /**
     * JWT settings ({@code orbitcrm.security.jwt.*}).
     *
     * <p>Purpose: configure how access and MFA challenge tokens are signed and how long they live.
     *
     * @param issuer         value of the {@code iss} claim (default {@code orbitcrm})
     * @param secret         base64 HMAC secret of at least 256 bits (required in production)
     * @param accessTokenTtl access token lifetime (default 15 minutes)
     * @param mfaTokenTtl    MFA challenge token lifetime (default 5 minutes)
     */
    public record Jwt(String issuer, String secret, Duration accessTokenTtl, Duration mfaTokenTtl) {
    }

    /**
     * Session lifetime settings ({@code orbitcrm.security.session.*}).
     *
     * <p>Purpose: configure how long users stay signed in and how long emailed links are valid.
     *
     * @param refreshTokenTtl    idle lifetime of one refresh token, rotated on every use (default 7 days)
     * @param absoluteTtl        hard cap on a login session regardless of activity (default 30 days)
     * @param refreshReuseLeeway window in which re-presenting a just-rotated token is tolerated (concurrent tabs)
     *                           instead of being treated as token theft (default 30 seconds)
     * @param inviteTtl          lifetime of invitation links (default 7 days)
     * @param passwordResetTtl   lifetime of password reset links (default 1 hour)
     */
    public record Session(Duration refreshTokenTtl, Duration absoluteTtl, Duration refreshReuseLeeway,
                          Duration inviteTtl, Duration passwordResetTtl) {
    }

    /**
     * Refresh cookie settings ({@code orbitcrm.security.refresh-cookie.*}).
     *
     * <p>Purpose: configure the HttpOnly cookie that carries the refresh token for browser apps.
     *
     * @param name     cookie name (default {@code orbitcrm_refresh})
     * @param secure   whether the cookie is sent over HTTPS only (default {@code true}; off in the local profile)
     * @param sameSite SameSite attribute (default {@code Strict})
     * @param path     cookie path (default {@code /api/v1/auth}, so it is only sent to the auth endpoints)
     */
    public record RefreshCookie(String name, boolean secure, String sameSite, String path) {
    }

    /**
     * Brute-force protection settings ({@code orbitcrm.security.login.*}).
     *
     * <p>Purpose: configure the per-account lockout and the per-IP login throttle.
     *
     * @param maxFailedAttempts failed logins before an account is locked (default 5)
     * @param lockDuration      how long a locked account stays locked (default 15 minutes)
     * @param ipMaxAttempts     failed attempts per client IP within the window before throttling (default 30)
     * @param ipWindow          sliding window of the per-IP throttle (default 10 minutes)
     */
    public record LoginProtection(int maxFailedAttempts, Duration lockDuration, int ipMaxAttempts,
                                  Duration ipWindow) {
    }

    /**
     * Web settings ({@code orbitcrm.web.*}).
     *
     * <p>Purpose: connect the API to its frontend.
     *
     * @param corsAllowedOrigins origins allowed to call the API from a browser (default {@code http://localhost:5173})
     * @param frontendBaseUrl    base URL used to build links in emails, e.g. invitation and password reset links
     */
    public record Web(List<String> corsAllowedOrigins, String frontendBaseUrl) {
    }

    /**
     * Mail settings ({@code orbitcrm.mail.*}).
     *
     * <p>Purpose: configure outgoing email.
     *
     * @param from    sender address, e.g. {@code OrbitCRM <no-reply@orbitcrm.local>}
     * @param logBody whether email bodies may be logged when no SMTP server is configured (development only, because
     *                bodies contain sensitive links)
     */
    public record Mail(String from, boolean logBody) {
    }
}
