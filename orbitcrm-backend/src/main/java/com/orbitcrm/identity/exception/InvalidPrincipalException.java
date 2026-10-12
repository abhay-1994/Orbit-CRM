package com.orbitcrm.identity.exception;

/**
 * Thrown when a signature-valid access or refresh token no longer maps to a usable principal.
 *
 * <p>Purpose: signal that authentication must be rejected because the tenant is suspended, the session was revoked
 * or expired, the user is no longer active or the role disappeared. The JWT converter turns it into
 * {@code 401 INVALID_TOKEN}; during refresh it also revokes the session.
 *
 * @author Abhay
 */
public class InvalidPrincipalException extends RuntimeException {
    public InvalidPrincipalException(String message) {
        super(message);
    }
}
