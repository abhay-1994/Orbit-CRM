package com.orbitcrm.shared.web;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Access to the current HTTP request from code that has no request parameter.
 *
 * <p>Purpose: let infrastructure code such as audit logging record the client IP address of the request that
 * caused a change. Returns empty / {@code null} outside of an HTTP request (for example in background jobs).
 *
 * @author Abhay
 */
public final class CurrentRequest {

    private CurrentRequest() {
    }

    public static Optional<HttpServletRequest> get() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return Optional.of(attributes.getRequest());
        }
        return Optional.empty();
    }

    public static String clientIp() {
        return get().map(HttpServletRequest::getRemoteAddr).orElse(null);
    }
}
