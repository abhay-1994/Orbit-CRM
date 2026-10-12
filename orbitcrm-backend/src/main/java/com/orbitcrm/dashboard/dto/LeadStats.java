package com.orbitcrm.dashboard.dto;

import java.math.BigDecimal;

/**
 * The lead KPIs of the dashboard.
 *
 * <p>Purpose: summarize lead generation and conversion.
 *
 * @author Abhay
 * @param open                  leads that are neither converted nor unqualified (regardless of range)
 * @param created               leads created in the range
 * @param converted             leads converted in the range
 * @param conversionRatePercent converted / created in the range, as a percentage with one decimal
 */
public record LeadStats(long open, long created, long converted, BigDecimal conversionRatePercent) {
}
