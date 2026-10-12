package com.orbitcrm.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body of {@code POST /api/v1/auth/invitations/accept}.
 *
 * <p>Purpose: let an invited user activate their account from the emailed link by choosing a password and,
 * optionally, correcting their name. On success the user is signed in immediately.
 *
 * @author Abhay
 * @param token     the raw invitation token from the email link
 * @param password  the new password; must satisfy {@code PasswordPolicy}
 * @param firstName optional corrected first name (1-80 characters)
 * @param lastName  optional corrected last name (1-80 characters)
 */
public record AcceptInvitationRequest(
        @NotBlank String token,
        @NotBlank String password,
        @Size(min = 1, max = 80) String firstName,
        @Size(min = 1, max = 80) String lastName) {
}
