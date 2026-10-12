package com.orbitcrm.identity.dto;

import java.util.UUID;

import jakarta.validation.constraints.Size;

import com.orbitcrm.identity.entity.User;

/**
 * Request body of {@code PATCH /api/v1/users/{id}} (administrative user update).
 *
 * <p>Purpose: partially update another user; {@code null} fields are left unchanged. Guard rails: administrators
 * cannot disable themselves or remove their own administrator role, the last active administrator cannot be
 * demoted or disabled, users cannot be moved back to {@code INVITED}, and an invited user cannot be activated before
 * accepting the invitation. Disabling a user revokes all their sessions.
 *
 * @author Abhay
 * @param firstName new first name
 * @param lastName  new last name
 * @param roleId    new role
 * @param teamId    new team ({@code null} means unchanged)
 * @param clearTeam {@code true} removes the team assignment
 * @param jobTitle  new job title
 * @param phone     new phone number
 * @param status    new status: {@code ACTIVE} or {@code DISABLED}
 */
public record UpdateUserRequest(
        @Size(min = 1, max = 80) String firstName,
        @Size(min = 1, max = 80) String lastName,
        UUID roleId,
        UUID teamId,
        Boolean clearTeam,
        @Size(max = 120) String jobTitle,
        @Size(max = 40) String phone,
        User.Status status) {
}
