package com.orbitcrm.deal.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.orbitcrm.deal.dto.PipelineView;
import com.orbitcrm.deal.dto.StageView;

/**
 * Public API of the deal module for pipelines and stages.
 *
 * <p>Purpose: let other modules (for example the dashboard funnel) read pipelines without depending on deal
 * internals: list all pipelines with stages, get one pipeline, get the default pipeline and look up a single stage.
 *
 * @author Abhay
 */
public interface PipelineService {

    List<PipelineView> list();

    PipelineView get(UUID pipelineId);

    PipelineView defaultPipeline();

    Optional<StageView> stage(UUID stageId);
}
