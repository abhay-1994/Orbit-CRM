package com.orbitcrm.identity.dto;

/**
 * Internal result of a refresh token rotation in {@code SessionService}.
 *
 * <p>Purpose: distinguish a successful rotation (a new {@link IssuedSession}) from a rejected one (unknown, expired
 * or reused token, or revoked session) without throwing inside the rotation transaction, so that a session
 * revocation caused by token reuse is committed.
 *
 * @author Abhay
 * @param session the session with its new refresh token, or {@code null} when the rotation was rejected
 */
public record SessionRotation(IssuedSession session) {
    public static SessionRotation invalid() {
        return new SessionRotation(null);
    }

    public boolean succeeded() {
        return session != null;
    }
}
