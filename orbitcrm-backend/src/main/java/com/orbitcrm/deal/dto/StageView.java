package com.orbitcrm.deal.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.orbitcrm.deal.model.StageType;

/**
 * A pipeline stage as exposed to clients and other modules.
 *
 * <p>Purpose: describe a stage for the board, pipeline settings, deal hooks and dashboard funnel.
 *
 * @author Abhay
 * @param id          stage id
 * @param pipelineId  pipeline the stage belongs to
 * @param name        stage name
 * @param position    order within the pipeline (1-based)
 * @param probability default win probability (0-100)
 * @param type        {@code OPEN}, {@code WON} or {@code LOST}
 * @param active      whether the stage can receive deals
 */
public record StageView(UUID id, UUID pipelineId, String name, int position, BigDecimal probability, StageType type,
                 boolean active) {
}
