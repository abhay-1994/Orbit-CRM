package com.orbitcrm.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response body of {@code GET /api/v1/dashboard}; sections for modules the user cannot read are {@code null} and
 * omitted from the JSON.
 *
 * <p>Purpose: all KPIs and charts of the home screen in one payload.
 *
 * @author Abhay
 * @param range          the requested range key
 * @param from           first day of the range (tenant time zone)
 * @param to             last day of the range (tenant time zone)
 * @param leads          lead KPIs for the range
 * @param deals          deal KPIs (open pipeline now, won / lost in the range)
 * @param funnel         deals per stage of the default pipeline
 * @param revenueByMonth won revenue for each of the last 12 months
 * @param leadSources    leads created in the range by source
 * @param activities     the user's activity workload
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Dashboard(String range, LocalDate from, LocalDate to, LeadStats leads, DealStats deals,
                 List<FunnelStage> funnel, List<MonthlyRevenue> revenueByMonth, List<LeadSourceStat> leadSources,
                 ActivityStats activities) {
}
