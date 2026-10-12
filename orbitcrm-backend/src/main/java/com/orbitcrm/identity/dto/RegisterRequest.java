package com.orbitcrm.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body of {@code POST /api/v1/auth/register} (self-service signup).
 *
 * <p>Purpose: create a new workspace (tenant) and its first administrator in one step. Regional settings are
 * optional and default to Asia/Kolkata, INR and en-IN.
 *
 * @author Abhay
 * @param companyName workspace / company name (2-120 characters)
 * @param tenantSlug  workspace URL identifier used at login (3-40 lowercase letters, digits or hyphens)
 * @param firstName   administrator's first name
 * @param lastName    administrator's last name
 * @param email       administrator's email address
 * @param password    administrator's password; must satisfy {@code PasswordPolicy}
 * @param timezone    optional IANA time zone, e.g. {@code Asia/Kolkata}
 * @param currency    optional ISO 4217 currency code, e.g. {@code INR}
 * @param locale      optional BCP 47 locale tag, e.g. {@code en-IN}
 */
public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 120) String companyName,
        @NotBlank @Size(min = 3, max = 40) String tenantSlug,
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank String password,
        String timezone,
        String currency,
        String locale) {
}
