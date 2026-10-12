package com.orbitcrm.identity.dto;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body of {@code POST /api/v1/users} (invite a user).
 *
 * <p>Purpose: create a user in the {@code INVITED} state and email them a link to set their password. The email
 * must be unique within the workspace; role and team must exist.
 *
 * @author Abhay
 * @param email     email address of the new user (unique per workspace)
 * @param firstName first name
 * @param lastName  last name
 * @param roleId    role to assign (required)
 * @param teamId    optional team to place the user in
 * @param jobTitle  optional job title
 * @param phone     optional phone number
 */
public record InviteUserRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @NotNull UUID roleId,
        UUID teamId,
        @Size(max = 120) String jobTitle,
        @Size(max = 40) String phone) {
}
