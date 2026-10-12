package com.orbitcrm.dashboard.dto;

import java.math.BigDecimal;

/**
 * The deal KPIs of the dashboard.
 *
 * <p>Purpose: summarize the sales pipeline and results.
 *
 * @author Abhay
 * @param openCount        number of open deals (regardless of range)
 * @param pipelineValue    total amount of open deals
 * @param weightedPipeline total weighted amount of open deals (amount x probability)
 * @param wonCount         deals won in the range
 * @param wonRevenue       amount of deals won in the range
 * @param lostCount        deals lost in the range
 * @param winRatePercent   won / (won + lost) in the range, as a percentage with one decimal
 */
public record DealStats(long openCount, BigDecimal pipelineValue, BigDecimal weightedPipeline, long wonCount,
                 BigDecimal wonRevenue, long lostCount, BigDecimal winRatePercent) {
}
