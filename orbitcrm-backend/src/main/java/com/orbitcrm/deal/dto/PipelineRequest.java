package com.orbitcrm.deal.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or replacing a pipeline.
 *
 * <p>Purpose: define a pipeline and its complete, ordered list of stages. Stage names must be unique within the
 * pipeline, and the active stages must include at least one of each type (open, won, lost).
 *
 * @author Abhay
 * @param name      unique pipeline name (max 120 characters)
 * @param isDefault whether this becomes the default pipeline for new deals
 * @param active    whether the pipeline can be used for deals (the default pipeline cannot be deactivated)
 * @param stages    ordered stages (1 to 30)
 */
public record PipelineRequest(@NotBlank @Size(max = 120) String name, Boolean isDefault, Boolean active,
                       @NotEmpty @Size(max = 30) List<@Valid StageRequest> stages) {
}
