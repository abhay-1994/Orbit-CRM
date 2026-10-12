package com.orbitcrm.deal.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.orbitcrm.audit.service.AuditService;
import com.orbitcrm.deal.config.DealModule;
import com.orbitcrm.deal.dto.BoardColumn;
import com.orbitcrm.deal.dto.BoardRequest;
import com.orbitcrm.deal.dto.PipelineBoard;
import com.orbitcrm.deal.dto.PipelineView;
import com.orbitcrm.deal.dto.StageMove;
import com.orbitcrm.deal.dto.StageView;
import com.orbitcrm.deal.service.impl.PipelineServiceImpl;
import com.orbitcrm.record.controller.AbstractRecordController;
import com.orbitcrm.record.dto.AggregateRequest;
import com.orbitcrm.record.dto.AggregateRow;
import com.orbitcrm.record.dto.Metric;
import com.orbitcrm.record.dto.RecordView;
import com.orbitcrm.record.dto.SearchRequest;
import com.orbitcrm.record.model.FilterNode;
import com.orbitcrm.record.service.RecordService;
import com.orbitcrm.shared.dto.ApiResponse;
import com.orbitcrm.shared.dto.PageResponse;
import com.orbitcrm.shared.tenant.TenantContext;

/**
 * REST controller for deals at {@code /api/v1/deals}.
 *
 * <p>Purpose: expose the standard record API for deals (inherited from
 * {@link com.orbitcrm.record.controller.AbstractRecordController}) plus the pipeline views:
 * <ul>
 *   <li>{@code GET /board} and {@code POST /board} - the Kanban board of a pipeline (the default pipeline when none
 *   is given): for every active stage the number of deals, total amount, weighted amount and the first deals ordered
 *   by expected close date (up to 100 per stage). The POST form accepts an additional filter, e.g. owner or close
 *   date. Totals and cards respect the user's scope.</li>
 *   <li>{@code GET /{id}/stage-history} - every stage the deal moved through, with amount, who moved it, when, and
 *   the time spent in the previous stage (for sales velocity analysis).</li>
 * </ul>
 *
 * @author Abhay
 */
@RestController
@RequestMapping("/api/v1/deals")
@Tag(name = "Deals", description = "Opportunities, pipeline board and stage history")
public class DealController extends AbstractRecordController {

    private static final int MAX_PER_STAGE = 100;

    private final PipelineServiceImpl pipelines;
    private final NamedParameterJdbcTemplate jdbc;

    DealController(RecordService records, AuditService audit, PipelineServiceImpl pipelines,
                   NamedParameterJdbcTemplate jdbc) {
        super(DealModule.KEY, records, audit);
        this.pipelines = pipelines;
        this.jdbc = jdbc;
    }

    @Override
    protected String singular() {
        return "Deal";
    }

    @GetMapping("/board")
    @Operation(summary = "Kanban board for a pipeline: per stage count, total, weighted total and top deals")
    ApiResponse<PipelineBoard> board(@RequestParam(required = false) UUID pipelineId,
                             @RequestParam(defaultValue = "25") int limitPerStage) {
        return ApiResponse.ok(buildBoard(new BoardRequest(pipelineId, null, limitPerStage)));
    }

    @PostMapping("/board")
    @Operation(summary = "Kanban board with an additional filter (e.g. owner, close date)")
    ApiResponse<PipelineBoard> filteredBoard(@RequestBody BoardRequest request) {
        return ApiResponse.ok(buildBoard(request));
    }

    @GetMapping("/{id}/stage-history")
    @Operation(summary = "Every stage the deal moved through, with time spent in the previous stage")
    @Transactional(readOnly = true)
    public ApiResponse<List<StageMove>> stageHistory(@PathVariable UUID id) {
        records.get(DealModule.KEY, id); // enforces read access
        Map<UUID, String> names = new HashMap<>();
        pipelines.list().forEach(p -> p.stages().forEach(s -> names.put(s.id(), s.name())));
        List<StageMove> moves = jdbc.query("""
                SELECT from_stage_id, to_stage_id, amount, changed_by, changed_at, seconds_in_prior_stage
                FROM deal_stage_history WHERE tenant_id = :tenantId AND deal_id = :dealId ORDER BY changed_at, id
                """, new MapSqlParameterSource("tenantId", TenantContext.requireTenantId()).addValue("dealId", id),
                (rs, n) -> {
                    UUID from = rs.getObject("from_stage_id", UUID.class);
                    UUID to = rs.getObject("to_stage_id", UUID.class);
                    long seconds = rs.getLong("seconds_in_prior_stage");
                    return new StageMove(from, from == null ? null : names.get(from), to, names.get(to),
                            rs.getBigDecimal("amount"), rs.getObject("changed_by", UUID.class),
                            rs.getObject("changed_at", OffsetDateTime.class).toInstant(),
                            rs.wasNull() ? null : seconds);
                });
        return ApiResponse.ok(moves);
    }

    private PipelineBoard buildBoard(BoardRequest request) {
        PipelineView pipeline = request.pipelineId() == null ? pipelines.defaultPipeline()
                : pipelines.get(request.pipelineId());
        int limit = Math.min(Math.max(request.limitPerStage() == null ? 25 : request.limitPerStage(), 0), MAX_PER_STAGE);
        FilterNode userFilter = FilterNode.parse(request.filter());
        FilterNode inPipeline = FilterNode.and(FilterNode.eq("pipelineId", pipeline.id().toString()), userFilter);

        Map<Object, AggregateRow> totals = new HashMap<>();
        for (AggregateRow row : records.aggregate(DealModule.KEY, new AggregateRequest(inPipeline,
                List.of("stageId"), List.of(Metric.count(), Metric.sum("amount"), Metric.sum("weightedAmount")), 100))) {
            totals.put(row.groups().get("stageId"), row);
        }

        List<BoardColumn> columns = new ArrayList<>();
        for (StageView stage : pipeline.stages()) {
            if (!stage.active()) {
                continue;
            }
            AggregateRow total = totals.get(stage.id());
            long count = total == null ? 0 : ((Number) total.metrics().get("count")).longValue();
            List<RecordView> deals = List.of();
            if (limit > 0 && count > 0) {
                FilterNode inStage = FilterNode.and(inPipeline, FilterNode.eq("stageId", stage.id().toString()));
                PageResponse<RecordView> page = records.search(DealModule.KEY, new SearchRequest(inStage, null,
                        List.of(new SearchRequest.Sort("expectedCloseDate", "asc")), 0, limit));
                deals = page.content();
            }
            columns.add(new BoardColumn(stage, count, decimal(total, "sum_amount"), decimal(total, "sum_weightedAmount"),
                    deals));
        }
        return new PipelineBoard(pipeline.id(), pipeline.name(), columns);
    }

    private static BigDecimal decimal(AggregateRow row, String metric) {
        if (row == null || row.metrics().get(metric) == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(row.metrics().get(metric).toString());
    }
}
