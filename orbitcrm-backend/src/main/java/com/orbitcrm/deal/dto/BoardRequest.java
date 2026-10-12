package com.orbitcrm.deal.dto;

import java.util.UUID;

/**
 * Request body of {@code POST /api/v1/deals/board}.
 *
 * <p>Purpose: build a filtered pipeline board, e.g. only my deals or deals closing this quarter.
 *
 * @author Abhay
 * @param pipelineId    pipeline to show; {@code null} for the default pipeline
 * @param filter        optional additional filter in the filter language
 * @param limitPerStage number of cards per stage (default 25, 0 to 100)
 */
public record BoardRequest(UUID pipelineId, Object filter, Integer limitPerStage) {
}
