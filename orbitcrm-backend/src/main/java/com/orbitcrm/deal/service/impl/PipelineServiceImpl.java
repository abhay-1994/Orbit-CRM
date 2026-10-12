package com.orbitcrm.deal.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbitcrm.deal.dto.PipelineRequest;
import com.orbitcrm.deal.dto.PipelineView;
import com.orbitcrm.deal.dto.StageRequest;
import com.orbitcrm.deal.dto.StageView;
import com.orbitcrm.deal.entity.Pipeline;
import com.orbitcrm.deal.entity.PipelineStage;
import com.orbitcrm.deal.model.StageType;
import com.orbitcrm.deal.repository.PipelineRepository;
import com.orbitcrm.deal.repository.PipelineStageRepository;
import com.orbitcrm.deal.service.PipelineService;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.tenant.TenantContext;
import com.orbitcrm.tenant.event.TenantCreatedEvent;

/**
 * Implementation of {@link com.orbitcrm.deal.service.PipelineService} plus pipeline administration and seeding.
 *
 * <p>Purpose: manage pipelines and stages consistently:
 * <ul>
 *   <li>reads for the API, the board, deal hooks and reference resolvers;</li>
 *   <li>create and update with validation: unique pipeline names, unique stage names, at least one active open, won
 *   and lost stage, exactly one default pipeline (which must be active), stages reordered by their position in the
 *   request;</li>
 *   <li>stages and pipelines that deals still reference - including deals in the recycle bin - cannot be deleted;</li>
 *   <li>on {@code TenantCreatedEvent} it seeds the default "Sales Pipeline": New 10%, Qualification 20%, Needs
 *   Analysis 40%, Proposal 60%, Negotiation 80%, Won 100%, Lost 0%.</li>
 * </ul>
 *
 * @author Abhay
 */
@Service
public class PipelineServiceImpl implements PipelineService {

    private final PipelineRepository pipelines;
    private final PipelineStageRepository stages;
    private final NamedParameterJdbcTemplate jdbc;

    PipelineServiceImpl(PipelineRepository pipelines, PipelineStageRepository stages, NamedParameterJdbcTemplate jdbc) {
        this.pipelines = pipelines;
        this.stages = stages;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ reads

    @Override
    @Transactional(readOnly = true)
    public List<PipelineView> list() {
        List<Pipeline> all = pipelines.findAllByOrderByPositionAscNameAsc();
        Map<UUID, List<PipelineStage>> byPipeline = stages.findAllByOrderByPositionAsc().stream()
                .collect(Collectors.groupingBy(PipelineStage::getPipelineId));
        return all.stream().map(p -> view(p, byPipeline.getOrDefault(p.getId(), List.of()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PipelineView get(UUID pipelineId) {
        Pipeline pipeline = pipelines.findById(pipelineId).orElseThrow(PipelineServiceImpl::pipelineNotFound);
        return view(pipeline, stages.findByPipelineIdOrderByPositionAsc(pipelineId));
    }

    @Override
    @Transactional(readOnly = true)
    public PipelineView defaultPipeline() {
        Pipeline pipeline = pipelines.findFirstByIsDefaultTrue()
                .orElseThrow(() -> ApiException.conflict("NO_DEFAULT_PIPELINE", "No default pipeline is configured"));
        return view(pipeline, stages.findByPipelineIdOrderByPositionAsc(pipeline.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StageView> stage(UUID stageId) {
        return stages.findById(stageId).map(PipelineServiceImpl::stageView);
    }

    @Transactional(readOnly = true)
    public List<PipelineStage> stagesById(Set<UUID> ids) {
        return stages.findAllById(ids);
    }

    @Transactional(readOnly = true)
    public List<Pipeline> pipelinesById(Set<UUID> ids) {
        return pipelines.findAllById(ids);
    }

    // ------------------------------------------------------------------ administration

    @Transactional
    public PipelineView create(PipelineRequest request) {
        String name = request.name().trim();
        if (pipelines.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("PIPELINE_NAME_TAKEN", "A pipeline with this name already exists");
        }
        validateStages(request.stages());
        boolean makeDefault = Boolean.TRUE.equals(request.isDefault()) || pipelines.findFirstByIsDefaultTrue().isEmpty();
        if (makeDefault) {
            clearDefault();
        }
        int position = pipelines.findAll().stream().mapToInt(Pipeline::getPosition).max().orElse(0) + 1;
        Pipeline pipeline = pipelines.saveAndFlush(new Pipeline(name, makeDefault, position));
        int stagePosition = 1;
        for (StageRequest stage : request.stages()) {
            stages.save(new PipelineStage(pipeline.getId(), stage.name().trim(), stagePosition++,
                    stage.probability(), stage.type()));
        }
        stages.flush();
        return get(pipeline.getId());
    }

    @Transactional
    public PipelineView update(UUID id, PipelineRequest request) {
        Pipeline pipeline = pipelines.findById(id).orElseThrow(PipelineServiceImpl::pipelineNotFound);
        String name = request.name().trim();
        if (pipelines.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw ApiException.conflict("PIPELINE_NAME_TAKEN", "A pipeline with this name already exists");
        }
        validateStages(request.stages());
        pipeline.rename(name);
        if (request.active() != null) {
            if (!request.active() && pipeline.isDefault()) {
                throw ApiException.conflict("PIPELINE_IS_DEFAULT", "Make another pipeline the default first");
            }
            pipeline.setActive(request.active());
        }
        if (Boolean.TRUE.equals(request.isDefault()) && !pipeline.isDefault()) {
            if (!pipeline.isActive()) {
                throw ApiException.conflict("PIPELINE_INACTIVE", "An inactive pipeline cannot be the default");
            }
            clearDefault();
            pipeline.setDefault(true);
        }

        Map<UUID, PipelineStage> existing = stages.findByPipelineIdOrderByPositionAsc(id).stream()
                .collect(Collectors.toMap(PipelineStage::getId, Function.identity()));
        Set<UUID> kept = new HashSet<>();
        int position = 1;
        for (StageRequest stageRequest : request.stages()) {
            boolean active = stageRequest.active() == null || stageRequest.active();
            if (stageRequest.id() != null) {
                PipelineStage stage = existing.get(stageRequest.id());
                if (stage == null) {
                    throw ApiException.validation("stages", "UNKNOWN_STAGE", "Stage does not belong to this pipeline");
                }
                stage.update(stageRequest.name().trim(), position++, stageRequest.probability(), stageRequest.type(),
                        active);
                kept.add(stage.getId());
            } else {
                PipelineStage created = new PipelineStage(id, stageRequest.name().trim(), position++,
                        stageRequest.probability(), stageRequest.type());
                if (!active) {
                    created.update(created.getName(), created.getPosition(), created.getProbability(),
                            created.getStageType(), false);
                }
                stages.save(created);
            }
        }
        for (PipelineStage removed : existing.values()) {
            if (!kept.contains(removed.getId())) {
                if (dealsReferencing("stage_id", removed.getId()) > 0) {
                    throw ApiException.conflict("STAGE_IN_USE",
                            "Stage '" + removed.getName() + "' has deals; move them or deactivate the stage instead");
                }
                stages.delete(removed);
            }
        }
        pipelines.flush();
        stages.flush();
        return get(id);
    }

    @Transactional
    public void delete(UUID id) {
        Pipeline pipeline = pipelines.findById(id).orElseThrow(PipelineServiceImpl::pipelineNotFound);
        if (pipeline.isDefault()) {
            throw ApiException.conflict("PIPELINE_IS_DEFAULT", "The default pipeline cannot be deleted");
        }
        if (dealsReferencing("pipeline_id", id) > 0) {
            throw ApiException.conflict("PIPELINE_IN_USE", "Move or delete the pipeline's deals first");
        }
        stages.deleteAll(stages.findByPipelineIdOrderByPositionAsc(id));
        pipelines.delete(pipeline);
    }

    @EventListener
    void seedDefaultPipeline(TenantCreatedEvent event) {
        create(new PipelineRequest("Sales Pipeline", true, null, List.of(
                new StageRequest(null, "New", new BigDecimal("10"), StageType.OPEN, null),
                new StageRequest(null, "Qualification", new BigDecimal("20"), StageType.OPEN, null),
                new StageRequest(null, "Needs Analysis", new BigDecimal("40"), StageType.OPEN, null),
                new StageRequest(null, "Proposal", new BigDecimal("60"), StageType.OPEN, null),
                new StageRequest(null, "Negotiation", new BigDecimal("80"), StageType.OPEN, null),
                new StageRequest(null, "Won", new BigDecimal("100"), StageType.WON, null),
                new StageRequest(null, "Lost", BigDecimal.ZERO, StageType.LOST, null))));
    }

    // ------------------------------------------------------------------ helpers

    private void clearDefault() {
        pipelines.findFirstByIsDefaultTrue().ifPresent(p -> {
            p.setDefault(false);
            pipelines.saveAndFlush(p);
        });
    }

    /** Includes soft-deleted deals: they still reference the stage through a foreign key. */
    private long dealsReferencing(String column, UUID id) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM deals WHERE tenant_id = :tenantId AND " + column + " = :id",
                new MapSqlParameterSource("tenantId", TenantContext.requireTenantId()).addValue("id", id), Long.class);
        return count == null ? 0 : count;
    }

    private static void validateStages(List<StageRequest> requested) {
        Set<String> names = new HashSet<>();
        for (StageRequest stage : requested) {
            if (!names.add(stage.name().trim().toLowerCase(Locale.ROOT))) {
                throw ApiException.validation("stages", "DUPLICATE_STAGE", "Stage names must be unique: " + stage.name());
            }
        }
        List<StageRequest> active = requested.stream().filter(s -> s.active() == null || s.active()).toList();
        for (StageType type : StageType.values()) {
            if (active.stream().noneMatch(s -> s.type() == type)) {
                throw ApiException.validation("stages", "MISSING_STAGE_TYPE",
                        "A pipeline needs at least one active " + type + " stage");
            }
        }
    }

    private static PipelineView view(Pipeline pipeline, List<PipelineStage> pipelineStages) {
        List<StageView> views = new ArrayList<>();
        pipelineStages.forEach(s -> views.add(stageView(s)));
        return new PipelineView(pipeline.getId(), pipeline.getName(), pipeline.isDefault(), pipeline.isActive(),
                pipeline.getPosition(), List.copyOf(views));
    }

    private static StageView stageView(PipelineStage stage) {
        return new StageView(stage.getId(), stage.getPipelineId(), stage.getName(), stage.getPosition(),
                stage.getProbability(), stage.getStageType(), stage.isActive());
    }

    private static ApiException pipelineNotFound() {
        return ApiException.notFound("PIPELINE_NOT_FOUND", "Pipeline does not exist");
    }
}
