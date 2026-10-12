package com.orbitcrm.identity.security;

import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.orbitcrm.shared.config.OrbitProperties;
import com.orbitcrm.shared.exception.ApiException;

/**
 * Builds, reads and clears the refresh token cookie and enforces the CSRF header for cookie-based requests.
 *
 * <p>Purpose: keep the refresh token out of reach of JavaScript in browser apps. The cookie
 * ({@code orbitcrm_refresh} by default) is HttpOnly, SameSite=Strict, Secure outside local development and
 * scoped to {@code /api/v1/auth}, and it expires together with the refresh token. Because browsers attach cookies
 * automatically, endpoints that use it require the {@code X-Requested-With} header: a cross-site form or image
 * request cannot add it, and a cross-site fetch that does triggers a CORS preflight that the policy rejects.
 *
 * @author Abhay
 */
@Component
public class RefreshCookies {

    public static final String CSRF_HEADER = "X-Requested-With";

    private final OrbitProperties.RefreshCookie config;

    RefreshCookies(OrbitProperties properties) {
        this.config = properties.security().refreshCookie();
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (config.name().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public String issue(String rawToken, Instant expiresAt) {
        long maxAge = Math.max(0, Duration.between(Instant.now(), expiresAt).toSeconds());
        return build(rawToken, maxAge);
    }

    public String clear() {
        return build("", 0);
    }

    /**
     * Cookie-authenticated endpoints require a custom header. A cross-site form or image request cannot
     * add it, and a cross-site fetch that does triggers a CORS preflight our policy rejects.
     */
    public void requireCsrfHeader(HttpServletRequest request) {
        if (request.getHeader(CSRF_HEADER) == null) {
            throw ApiException.forbidden("CSRF_HEADER_MISSING", "The " + CSRF_HEADER + " header is required");
        }
    }

    public static String headerName() {
        return HttpHeaders.SET_COOKIE;
    }

    private String build(String value, long maxAgeSeconds) {
        return ResponseCookie.from(config.name(), value)
                .httpOnly(true)
                .secure(config.secure())
                .sameSite(config.sameSite())
                .path(config.path())
                .maxAge(maxAgeSeconds)
                .build()
                .toString();
    }
}
