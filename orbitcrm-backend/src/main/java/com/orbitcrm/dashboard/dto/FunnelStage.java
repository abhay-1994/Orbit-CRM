package com.orbitcrm.dashboard.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.orbitcrm.deal.model.StageType;

/**
 * One stage of the dashboard's sales funnel chart.
 *
 * <p>Purpose: show how many deals, and how much value, sit in each stage of the default pipeline.
 *
 * @author Abhay
 * @param stageId stage id
 * @param stage   stage name
 * @param type    {@code OPEN}, {@code WON} or {@code LOST}
 * @param count   number of visible deals in the stage
 * @param amount  their total amount
 */
public record FunnelStage(UUID stageId, String stage, StageType type, long count, BigDecimal amount) {
}
