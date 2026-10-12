package com.orbitcrm.identity.dto;

/**
 * A freshly signed access token and its lifetime.
 *
 * <p>Purpose: returned by {@code JwtService} so callers can put both the token and its {@code expiresIn} value into
 * the response.
 *
 * @author Abhay
 * @param value            the signed JWT
 * @param expiresInSeconds seconds until the token expires
 */
public record IssuedToken(String value, long expiresInSeconds) {
}
