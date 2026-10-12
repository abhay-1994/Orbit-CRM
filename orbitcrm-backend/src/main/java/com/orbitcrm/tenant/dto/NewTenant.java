package com.orbitcrm.tenant.dto;

import java.util.UUID;

/**
 * Input for provisioning a new tenant during self-service signup.
 *
 * <p>Purpose: carry the workspace details from the registration flow to the tenant service. The id is chosen by
 * the caller so it can bind the tenant context before the provisioning transaction starts.
 *
 * @author Abhay
 * @param id       id the new tenant will get
 * @param slug     workspace URL identifier (3-40 lowercase letters, digits or hyphens)
 * @param name     company / workspace display name
 * @param timezone IANA time zone id, e.g. {@code Asia/Kolkata}; defaults when blank
 * @param currency ISO 4217 currency code, e.g. {@code INR}; defaults when blank
 * @param locale   BCP 47 locale tag, e.g. {@code en-IN}; defaults when blank
 */
public record NewTenant(UUID id, String slug, String name, String timezone, String currency, String locale) {
}
