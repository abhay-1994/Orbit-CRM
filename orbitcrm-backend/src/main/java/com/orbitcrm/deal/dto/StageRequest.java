package com.orbitcrm.deal.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.orbitcrm.deal.model.StageType;

/**
 * One stage in a {@link PipelineRequest}.
 *
 * <p>Purpose: define or update a stage. Stages with an id update the existing stage; stages without an id are created.
 *
 * @author Abhay
 * @param id          existing stage id, or {@code null} to create a stage
 * @param name        stage name (unique within the pipeline, max 120 characters)
 * @param probability default win probability for deals in this stage (0-100)
 * @param type        {@code OPEN}, {@code WON} or {@code LOST}
 * @param active      whether the stage can receive deals (default {@code true})
 */
public record StageRequest(UUID id, @NotBlank @Size(max = 120) String name,
                    @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal probability,
                    @NotNull StageType type, Boolean active) {
}
