package com.orbitcrm.identity.dto;

import java.util.UUID;

/**
 * The tenant part of {@link MeResponse}.
 *
 * <p>Purpose: give the frontend the workspace identity and the regional settings it needs to format dates,
 * amounts and fiscal periods.
 *
 * @author Abhay
 * @param id                   tenant id
 * @param slug                 workspace slug
 * @param name                 workspace name
 * @param timezone             IANA time zone
 * @param currency             ISO 4217 currency
 * @param locale               BCP 47 locale
 * @param fiscalYearStartMonth month (1-12) in which the fiscal year starts
 */
public record TenantSummary(UUID id, String slug, String name, String timezone, String currency,
                            String locale, int fiscalYearStartMonth) {
}
