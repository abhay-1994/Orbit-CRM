package com.orbitcrm.identity.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.orbitcrm.shared.config.OrbitProperties;
import com.orbitcrm.shared.exception.ApiException;

/**
 * Sliding-window limit on failed authentication attempts per client IP address.
 *
 * <p>Purpose: slow down credential stuffing and password spraying across many accounts, complementing the
 * per-account lockout. Failures (bad logins, bad MFA codes, password reset requests) are remembered per IP for a
 * window (10 minutes by default); after too many (30 by default) further attempts are rejected with
 * {@code 429 TOO_MANY_ATTEMPTS}. The state is in memory per instance; a shared store such as Redis is needed when
 * running several instances.
 *
 * @author Abhay
 */
@Component
public class LoginThrottle {

    private final Cache<String, Deque<Instant>> failures;
    private final int maxAttempts;
    private final Duration window;

    LoginThrottle(OrbitProperties properties) {
        OrbitProperties.LoginProtection login = properties.security().login();
        this.maxAttempts = login.ipMaxAttempts();
        this.window = login.ipWindow();
        this.failures = Caffeine.newBuilder().expireAfterAccess(window).maximumSize(100_000).build();
    }

    public void check(String ip) {
        Deque<Instant> attempts = failures.getIfPresent(key(ip));
        if (attempts == null) {
            return;
        }
        synchronized (attempts) {
            prune(attempts);
            if (attempts.size() >= maxAttempts) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                        "Too many failed attempts. Please wait a few minutes and try again.");
            }
        }
    }

    public void recordFailure(String ip) {
        Deque<Instant> attempts = failures.get(key(ip), k -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts);
            attempts.addLast(Instant.now());
        }
    }

    private void prune(Deque<Instant> attempts) {
        Instant cutoff = Instant.now().minus(window);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
            attempts.pollFirst();
        }
    }

    private static String key(String ip) {
        return ip == null ? "unknown" : ip;
    }
}
