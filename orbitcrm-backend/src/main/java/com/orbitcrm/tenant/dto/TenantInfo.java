package com.orbitcrm.tenant.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Read model of a tenant (customer organization / workspace) and its regional settings.
 *
 * <p>Purpose: the tenant module's public view of a tenant, used by other modules (for time zone aware filters,
 * currency on deals, the "me" payload, login) and returned by {@code GET /api/v1/tenant}.
 *
 * @author Abhay
 * @param id                   tenant id
 * @param slug                 unique workspace URL identifier used at login
 * @param name                 display name
 * @param status               {@code ACTIVE} or {@code SUSPENDED}
 * @param timezone             IANA time zone used for "today", date filters and dashboard ranges
 * @param currency             ISO 4217 currency of monetary fields
 * @param locale               BCP 47 locale for formatting
 * @param fiscalYearStartMonth month (1-12) in which the fiscal year starts; drives quarter and year ranges
 * @param createdAt            when the tenant was created
 */
public record TenantInfo(
        UUID id,
        String slug,
        String name,
        String status,
        String timezone,
        String currency,
        String locale,
        int fiscalYearStartMonth,
        Instant createdAt) {

    public boolean active() {
        return "ACTIVE".equals(status);
    }
}
