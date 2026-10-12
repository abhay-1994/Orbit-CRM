package com.orbitcrm.tenant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Request body of {@code PATCH /api/v1/tenant}.
 *
 * <p>Purpose: partially update workspace settings; {@code null} fields are left unchanged. Time zone, currency and
 * locale are validated against the Java time zone database, ISO 4217 and BCP 47.
 *
 * @author Abhay
 * @param name                 new display name (2-120 characters)
 * @param timezone             new IANA time zone id
 * @param currency             new ISO 4217 currency code
 * @param locale               new BCP 47 locale tag
 * @param fiscalYearStartMonth new fiscal year start month (1-12)
 */
public record TenantSettingsUpdate(
        @Size(min = 2, max = 120) String name,
        String timezone,
        String currency,
        String locale,
        @Min(1) @Max(12) Integer fiscalYearStartMonth) {
}
