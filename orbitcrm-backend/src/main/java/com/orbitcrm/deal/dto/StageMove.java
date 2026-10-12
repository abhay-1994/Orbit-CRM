package com.orbitcrm.deal.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One entry of a deal's stage history, returned by {@code GET /api/v1/deals/{id}/stage-history}.
 *
 * <p>Purpose: show how a deal progressed through the pipeline and how long it stayed in each stage.
 *
 * @author Abhay
 * @param fromStageId         previous stage id ({@code null} for the initial stage)
 * @param fromStage           previous stage name
 * @param toStageId           new stage id
 * @param toStage             new stage name
 * @param amount              deal amount at the time of the move
 * @param changedBy           user who moved the deal
 * @param changedAt           when the move happened
 * @param secondsInPriorStage time spent in the previous stage, or {@code null} for the first entry
 */
public record StageMove(UUID fromStageId, String fromStage, UUID toStageId, String toStage, BigDecimal amount,
                 UUID changedBy, Instant changedAt, Long secondsInPriorStage) {
}
