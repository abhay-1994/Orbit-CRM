package com.orbitcrm.identity.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Full representation of a user returned by the user administration endpoints.
 *
 * <p>Purpose: show everything an administrator needs about a user, including role and team names and security
 * status.
 *
 * @author Abhay
 * @param id          user id
 * @param email       email address
 * @param firstName   first name
 * @param lastName    last name
 * @param fullName    first and last name
 * @param status      {@code INVITED}, {@code ACTIVE} or {@code DISABLED}
 * @param roleId      role id
 * @param roleName    role name
 * @param teamId      team id, or {@code null}
 * @param teamName    team name, or {@code null}
 * @param jobTitle    job title
 * @param phone       phone number
 * @param mfaEnabled  whether two-factor authentication is on
 * @param lastLoginAt last successful sign-in
 * @param createdAt   creation (invitation) time
 * @param updatedAt   last modification time
 */
public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String fullName,
        String status,
        UUID roleId,
        String roleName,
        UUID teamId,
        String teamName,
        String jobTitle,
        String phone,
        boolean mfaEnabled,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt) {
}
