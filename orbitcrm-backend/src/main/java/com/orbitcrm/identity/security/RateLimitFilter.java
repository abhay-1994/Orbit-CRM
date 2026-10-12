package com.orbitcrm.identity.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.json.JsonMapper;

import com.orbitcrm.shared.config.OrbitProperties;
import com.orbitcrm.shared.dto.ApiErrorResponse;
import com.orbitcrm.shared.security.AccessControl;
import com.orbitcrm.shared.web.RequestIdFilter;
import com.orbitcrm.shared.web.TokenBucketLimiter;

/**
 * Servlet filter that applies API rate limits (PRD sections 43 and 47).
 *
 * <p>Purpose: protect the API from abuse and runaway clients. For requests under {@code /api/} it consumes a token
 * from a per-user bucket once the caller is authenticated, or from a per-IP bucket otherwise (limits from
 * {@code orbitcrm.rate-limit}). When the bucket is empty it answers {@code 429 RATE_LIMITED} with a
 * {@code Retry-After} header. Registered in the security filter chain right after {@code TenantContextFilter};
 * can be switched off with {@code orbitcrm.rate-limit.enabled=false}.
 *
 * @author Abhay
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final TokenBucketLimiter authenticated;
    private final TokenBucketLimiter anonymous;
    private final JsonMapper json;
    private final boolean enabled;

    public RateLimitFilter(OrbitProperties.RateLimit config, JsonMapper json) {
        this.enabled = config.enabled();
        this.authenticated = new TokenBucketLimiter(config.authenticatedPerMinute());
        this.anonymous = new TokenBucketLimiter(config.anonymousPerMinute());
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long retryAfter = AccessControl.currentUser()
                .map(user -> authenticated.tryAcquire("u:" + user.userId()))
                .orElseGet(() -> anonymous.tryAcquire("ip:" + request.getRemoteAddr()));
        if (retryAfter > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            json.writeValue(response.getOutputStream(), ApiErrorResponse.of("RATE_LIMITED",
                    "Too many requests. Retry in " + retryAfter + " seconds.", null, RequestIdFilter.currentRequestId()));
            return;
        }
        chain.doFilter(request, response);
    }
}
