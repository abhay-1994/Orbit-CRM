package com.orbitcrm.identity.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body of {@code PATCH /api/v1/auth/me}.
 *
 * <p>Purpose: let users edit their own profile and personal preferences. {@code null} fields are left unchanged;
 * blank optional fields are cleared (time zone and locale then fall back to the tenant defaults).
 *
 * @author Abhay
 * @param firstName new first name
 * @param lastName  new last name
 * @param jobTitle  new job title (blank clears it)
 * @param phone     new phone number (blank clears it)
 * @param timezone  personal IANA time zone (blank reverts to the tenant's)
 * @param locale    personal locale (blank reverts to the tenant's)
 */
public record UpdateProfileRequest(
        @Size(min = 1, max = 80) String firstName,
        @Size(min = 1, max = 80) String lastName,
        @Size(max = 120) String jobTitle,
        @Size(max = 40) String phone,
        @Size(max = 64) String timezone,
        @Size(max = 35) String locale) {
}
