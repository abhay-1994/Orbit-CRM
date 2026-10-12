package com.orbitcrm.deal.dto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.orbitcrm.deal.model.StageType;

/**
 * A pipeline with its ordered stages.
 *
 * <p>Purpose: returned by the pipeline endpoints and the {@code PipelineService} API; used by the board, the deal hooks
 * and reference resolution. Helper methods find a stage by id and the first active open stage (the default stage for
 * new deals).
 *
 * @author Abhay
 * @param id        pipeline id
 * @param name      pipeline name
 * @param isDefault whether new deals use it when no pipeline is given
 * @param active    whether it can be used for deals
 * @param position  display order
 * @param stages    all stages in order, including inactive ones
 */
public record PipelineView(UUID id, String name, boolean isDefault, boolean active, int position, List<StageView> stages) {

    public Optional<StageView> stage(UUID stageId) {
        return stages.stream().filter(s -> s.id().equals(stageId)).findFirst();
    }

    public StageView firstOpenStage() {
        return stages.stream().filter(s -> s.active() && s.type() == StageType.OPEN).findFirst()
                .orElseThrow(() -> new IllegalStateException("Pipeline " + name + " has no open stage"));
    }
}
