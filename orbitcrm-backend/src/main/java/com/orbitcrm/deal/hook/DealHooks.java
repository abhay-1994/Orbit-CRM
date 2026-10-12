package com.orbitcrm.deal.hook;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.orbitcrm.deal.config.DealModule;
import com.orbitcrm.deal.dto.PipelineView;
import com.orbitcrm.deal.dto.StageView;
import com.orbitcrm.deal.model.StageType;
import com.orbitcrm.deal.service.impl.PipelineServiceImpl;
import com.orbitcrm.record.dto.RecordChange;
import com.orbitcrm.record.dto.RecordView;
import com.orbitcrm.record.hook.RecordHooks;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.tenant.TenantContext;
import com.orbitcrm.shared.util.UuidV7;
import com.orbitcrm.tenant.service.TenantService;

/**
 * Record hooks that keep a deal's pipeline, stage, probability, status and close date consistent.
 *
 * <p>Purpose: encode the pipeline rules of PRD section 14 around the generic deal writes:
 * <ul>
 *   <li>No pipeline given: the stage's pipeline or else the default pipeline. No stage given: the pipeline's first
 *   active open stage. The pipeline and stage must be active and the stage must belong to the pipeline.</li>
 *   <li>Changing the stage sets the probability to the stage's value unless the request sets it explicitly.</li>
 *   <li>Status mirrors the stage type (open / won / lost); {@code closedAt} is set when a deal closes and cleared when
 *   it is reopened.</li>
 *   <li>The deal currency is the tenant currency.</li>
 *   <li>Every stage move (including the initial stage) is recorded in {@code deal_stage_history} with the amount, the
 *   user and the time spent in the previous stage.</li>
 * </ul>
 *
 * @author Abhay
 */
@Component
public class DealHooks implements RecordHooks {

    private final PipelineServiceImpl pipelines;
    private final TenantService tenants;
    private final NamedParameterJdbcTemplate jdbc;

    DealHooks(PipelineServiceImpl pipelines, TenantService tenants, NamedParameterJdbcTemplate jdbc) {
        this.pipelines = pipelines;
        this.tenants = tenants;
        this.jdbc = jdbc;
    }

    @Override
    public String moduleKey() {
        return DealModule.KEY;
    }

    @Override
    public void beforeCreate(HookContext context, Map<String, Object> values) {
        UUID pipelineId = (UUID) values.get("pipelineId");
        if (pipelineId == null && values.get("stageId") != null) {
            // A stage alone identifies its pipeline.
            pipelineId = pipelines.stage((UUID) values.get("stageId")).map(StageView::pipelineId).orElse(null);
        }
        PipelineView pipeline = pipelineId == null ? pipelines.defaultPipeline() : activePipeline(pipelineId);
        StageView stage = resolveStage(pipeline, (UUID) values.get("stageId"));
        values.put("pipelineId", pipeline.id());
        values.put("stageId", stage.id());
        if (values.get("probability") == null) {
            values.put("probability", stage.probability());
        }
        values.put("status", status(stage));
        values.put("closedAt", stage.type() == StageType.OPEN ? null : Instant.now());
        values.put("currency", tenants.current().currency());
    }

    @Override
    public void beforeUpdate(HookContext context, RecordView existing, Map<String, Object> changes) {
        boolean pipelineChanged = changes.containsKey("pipelineId")
                && !Objects.equals(changes.get("pipelineId"), existing.value("pipelineId"));
        boolean stageChanged = changes.containsKey("stageId")
                && !Objects.equals(changes.get("stageId"), existing.value("stageId"));
        if (!pipelineChanged && !stageChanged) {
            changes.remove("pipelineId");
            changes.remove("stageId");
            return;
        }
        UUID pipelineId = pipelineChanged ? (UUID) changes.get("pipelineId") : existing.uuid("pipelineId");
        PipelineView pipeline = activePipeline(pipelineId);
        UUID requestedStage = stageChanged ? (UUID) changes.get("stageId") : pipelineChanged ? null : existing.uuid("stageId");
        StageView stage = resolveStage(pipeline, requestedStage);
        changes.put("pipelineId", pipeline.id());
        changes.put("stageId", stage.id());
        if (!changes.containsKey("probability")) {
            changes.put("probability", stage.probability());
        }
        String status = status(stage);
        changes.put("status", status);
        if (!status.equals(existing.value("status"))) {
            changes.put("closedAt", stage.type() == StageType.OPEN ? null : Instant.now());
        }
    }

    @Override
    public void afterCreate(HookContext context, RecordView created) {
        recordStageMove(created, null, context.user().userId());
    }

    @Override
    public void afterUpdate(HookContext context, RecordView before, RecordView after, List<RecordChange> changes) {
        if (changes.stream().anyMatch(c -> c.field().equals("stageId"))) {
            recordStageMove(after, before.uuid("stageId"), context.user().userId());
        }
    }

    private void recordStageMove(RecordView deal, UUID fromStage, UUID actorId) {
        UUID tenantId = TenantContext.requireTenantId();
        Instant now = Instant.now();
        Long secondsInPrior = null;
        if (fromStage != null) {
            List<OffsetDateTime> last = jdbc.queryForList("""
                    SELECT max(changed_at) FROM deal_stage_history WHERE tenant_id = :tenantId AND deal_id = :dealId
                    """, new MapSqlParameterSource("tenantId", tenantId).addValue("dealId", deal.id()), OffsetDateTime.class);
            if (!last.isEmpty() && last.get(0) != null) {
                secondsInPrior = Duration.between(last.get(0).toInstant(), now).toSeconds();
            }
        }
        jdbc.update("""
                INSERT INTO deal_stage_history (id, tenant_id, deal_id, pipeline_id, from_stage_id, to_stage_id, amount,
                                                changed_by, changed_at, seconds_in_prior_stage)
                VALUES (:id, :tenantId, :dealId, :pipelineId, :fromStage, :toStage, :amount, :actor, :now, :seconds)
                """, new MapSqlParameterSource()
                .addValue("id", UuidV7.generate())
                .addValue("tenantId", tenantId)
                .addValue("dealId", deal.id())
                .addValue("pipelineId", deal.uuid("pipelineId"))
                .addValue("fromStage", fromStage)
                .addValue("toStage", deal.uuid("stageId"))
                .addValue("amount", (BigDecimal) deal.value("amount"))
                .addValue("actor", actorId)
                .addValue("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .addValue("seconds", secondsInPrior));
    }

    private PipelineView activePipeline(UUID pipelineId) {
        PipelineView pipeline;
        try {
            pipeline = pipelines.get(pipelineId);
        } catch (ApiException ex) {
            throw ApiException.validation("pipelineId", "INVALID_REFERENCE", "Pipeline does not exist");
        }
        if (!pipeline.active()) {
            throw ApiException.validation("pipelineId", "PIPELINE_INACTIVE", "This pipeline is no longer active");
        }
        return pipeline;
    }

    private static StageView resolveStage(PipelineView pipeline, UUID stageId) {
        if (stageId == null) {
            return pipeline.firstOpenStage();
        }
        StageView stage = pipeline.stage(stageId).orElseThrow(() -> ApiException.validation("stageId",
                "STAGE_NOT_IN_PIPELINE", "The stage does not belong to pipeline '" + pipeline.name() + "'"));
        if (!stage.active()) {
            throw ApiException.validation("stageId", "STAGE_INACTIVE", "This stage is no longer active");
        }
        return stage;
    }

    private static String status(StageView stage) {
        return stage.type().name().toLowerCase(Locale.ROOT);
    }
}
