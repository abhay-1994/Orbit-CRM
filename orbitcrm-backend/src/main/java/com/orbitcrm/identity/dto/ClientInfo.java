package com.orbitcrm.identity.dto;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Network details of the caller, captured from the HTTP request.
 *
 * <p>Purpose: record where a session was created (shown in the "active sessions" list) and key the per-IP login
 * throttle. With {@code server.forward-headers-strategy=native}, {@code getRemoteAddr()} already reflects the
 * client address forwarded by a trusted reverse proxy.
 *
 * @author Abhay
 * @param ipAddress client IP address
 * @param userAgent the {@code User-Agent} header, used to describe the device
 */
public record ClientInfo(String ipAddress, String userAgent) {

    public static ClientInfo from(HttpServletRequest request) {
        return new ClientInfo(request.getRemoteAddr(), request.getHeader("User-Agent"));
    }
}
