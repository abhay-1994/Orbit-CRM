package com.orbitcrm.dashboard.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbitcrm.dashboard.dto.ActivityStats;
import com.orbitcrm.dashboard.dto.Dashboard;
import com.orbitcrm.dashboard.dto.DealStats;
import com.orbitcrm.dashboard.dto.FunnelStage;
import com.orbitcrm.dashboard.dto.LeadSourceStat;
import com.orbitcrm.dashboard.dto.LeadStats;
import com.orbitcrm.dashboard.dto.MonthlyRevenue;
import com.orbitcrm.dashboard.dto.UpcomingMeeting;
import com.orbitcrm.deal.dto.PipelineView;
import com.orbitcrm.deal.dto.StageView;
import com.orbitcrm.deal.service.PipelineService;
import com.orbitcrm.metadata.model.FilterOperator;
import com.orbitcrm.record.dto.AggregateRequest;
import com.orbitcrm.record.dto.AggregateRow;
import com.orbitcrm.record.dto.Metric;
import com.orbitcrm.record.dto.RecordView;
import com.orbitcrm.record.dto.SearchRequest;
import com.orbitcrm.record.model.FilterNode;
import com.orbitcrm.record.service.RecordService;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.security.AccessControl;
import com.orbitcrm.shared.security.Action;
import com.orbitcrm.shared.security.CurrentUser;
import com.orbitcrm.tenant.service.TenantService;

/**
 * Computes the dashboard KPIs (PRD section 8) live through the record engine.
 *
 * <p>Purpose: produce trustworthy numbers that respect the viewer's scope - a sales user sees their own pipeline, a
 * manager their team's, management everything - because every count and aggregate goes through
 * {@code RecordService} with the caller's permissions. Sections for modules the user cannot read are omitted. It
 * computes lead KPIs, deal KPIs, the default pipeline funnel, won revenue for the last 12 months, leads by source and
 * the user's activity workload. Date ranges are resolved in the tenant's time zone, and quarters and years follow the
 * tenant's fiscal year start (e.g. April in India), implemented by the nested {@code Range} record.
 *
 * @author Abhay
 */
@Service
public class DashboardService {

    private static final String LEADS = "leads";
    private static final String DEALS = "deals";
    private static final String ACTIVITIES = "activities";

    private final RecordService records;
    private final PipelineService pipelines;
    private final TenantService tenants;

    DashboardService(RecordService records, PipelineService pipelines, TenantService tenants) {
        this.records = records;
        this.pipelines = pipelines;
        this.tenants = tenants;
    }

    @Transactional(readOnly = true)
    public Dashboard build(String rangeKey) {
        CurrentUser user = AccessControl.requireUser();
        ZoneId zone = ZoneId.of(tenants.current().timezone());
        Range range = Range.of(rangeKey, LocalDate.now(zone), tenants.current().fiscalYearStartMonth());
        return new Dashboard(range.key(), range.from(), range.to(),
                user.can(LEADS, Action.READ) ? leads(range) : null,
                user.can(DEALS, Action.READ) ? deals(range) : null,
                user.can(DEALS, Action.READ) ? funnel() : null,
                user.can(DEALS, Action.READ) ? revenueByMonth(zone) : null,
                user.can(LEADS, Action.READ) ? leadSources(range) : null,
                user.can(ACTIVITIES, Action.READ) ? activities(zone) : null);
    }

    private LeadStats leads(Range range) {
        long open = records.count(LEADS, FilterNode.condition("status", FilterOperator.NOT_IN,
                List.of("converted", "unqualified")));
        long created = records.count(LEADS, inRange("createdAt", range));
        long converted = records.count(LEADS, inRange("convertedAt", range));
        BigDecimal rate = created == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(converted * 100).divide(BigDecimal.valueOf(created), 1, java.math.RoundingMode.HALF_UP);
        return new LeadStats(open, created, converted, rate);
    }

    private DealStats deals(Range range) {
        AggregateRow open = single(records.aggregate(DEALS, new AggregateRequest(FilterNode.eq("status", "open"), null,
                List.of(Metric.count(), Metric.sum("amount"), Metric.sum("weightedAmount")), 1)));
        FilterNode wonInRange = FilterNode.and(FilterNode.eq("status", "won"), inRange("closedAt", range));
        AggregateRow won = single(records.aggregate(DEALS, new AggregateRequest(wonInRange, null,
                List.of(Metric.count(), Metric.sum("amount")), 1)));
        long lost = records.count(DEALS, FilterNode.and(FilterNode.eq("status", "lost"), inRange("closedAt", range)));
        long wonCount = count(won);
        BigDecimal winRate = wonCount + lost == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(wonCount * 100).divide(BigDecimal.valueOf(wonCount + lost), 1, java.math.RoundingMode.HALF_UP);
        return new DealStats(count(open), decimal(open, "sum_amount"), decimal(open, "sum_weightedAmount"), wonCount,
                decimal(won, "sum_amount"), lost, winRate);
    }

    private List<FunnelStage> funnel() {
        PipelineView pipeline;
        try {
            pipeline = pipelines.defaultPipeline();
        } catch (ApiException ex) {
            return List.of();
        }
        Map<Object, AggregateRow> byStage = new HashMap<>();
        records.aggregate(DEALS, new AggregateRequest(FilterNode.eq("pipelineId", pipeline.id().toString()),
                        List.of("stageId"), List.of(Metric.count(), Metric.sum("amount")), 100))
                .forEach(row -> byStage.put(row.groups().get("stageId"), row));
        List<FunnelStage> stages = new ArrayList<>();
        for (StageView stage : pipeline.stages()) {
            if (!stage.active()) {
                continue;
            }
            AggregateRow row = byStage.get(stage.id());
            stages.add(new FunnelStage(stage.id(), stage.name(), stage.type(), count(row), decimal(row, "sum_amount")));
        }
        return stages;
    }

    private List<MonthlyRevenue> revenueByMonth(ZoneId zone) {
        LocalDate firstMonth = LocalDate.now(zone).withDayOfMonth(1).minusMonths(11);
        FilterNode won = FilterNode.and(FilterNode.eq("status", "won"),
                FilterNode.condition("closedAt", FilterOperator.GTE, firstMonth.toString()));
        Map<LocalDate, AggregateRow> byMonth = new HashMap<>();
        records.aggregate(DEALS, new AggregateRequest(won, List.of("closedAt:month"),
                        List.of(Metric.count(), Metric.sum("amount")), 12))
                .forEach(row -> byMonth.put((LocalDate) row.groups().get("closedAt:month"), row));
        List<MonthlyRevenue> months = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            LocalDate month = firstMonth.plusMonths(i);
            AggregateRow row = byMonth.get(month);
            months.add(new MonthlyRevenue(month.toString().substring(0, 7), count(row), decimal(row, "sum_amount")));
        }
        return months;
    }

    private List<LeadSourceStat> leadSources(Range range) {
        return records.aggregate(LEADS, new AggregateRequest(inRange("createdAt", range), List.of("leadSource"),
                        List.of(Metric.count()), 50)).stream()
                .map(row -> new LeadSourceStat((String) row.groups().get("leadSource"),
                        row.groupLabels().getOrDefault("leadSource", "Not set"), count(row)))
                .sorted((a, b) -> Long.compare(b.count(), a.count()))
                .toList();
    }

    private ActivityStats activities(ZoneId zone) {
        FilterNode open = FilterNode.condition("status", FilterOperator.IN, List.of("not_started", "in_progress"));
        FilterNode mine = FilterNode.eq("ownerId", "$me");
        long dueToday = records.count(ACTIVITIES, FilterNode.and(open, mine, FilterNode.eq("endAt", "$today")));
        long overdue = records.count(ACTIVITIES, FilterNode.and(open, mine,
                FilterNode.condition("endAt", FilterOperator.LT, "$now")));
        List<RecordView> upcoming = records.search(ACTIVITIES, new SearchRequest(FilterNode.and(open,
                FilterNode.eq("type", "meeting"),
                FilterNode.condition("startAt", FilterOperator.WITHIN_NEXT_DAYS, 7)), null,
                List.of(new SearchRequest.Sort("startAt", "asc")), 0, 5)).content();
        return new ActivityStats(dueToday, overdue, upcoming.stream()
                .map(a -> new UpcomingMeeting(a.id(), a.title(), (Instant) a.value("startAt"), a.labels().get("relatedId")))
                .toList());
    }

    private static FilterNode inRange(String field, Range range) {
        return FilterNode.condition(field, FilterOperator.BETWEEN, List.of(range.from().toString(), range.to().toString()));
    }

    private static AggregateRow single(List<AggregateRow> rows) {
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static long count(AggregateRow row) {
        return row == null || row.metrics().get("count") == null ? 0 : ((Number) row.metrics().get("count")).longValue();
    }

    private static BigDecimal decimal(AggregateRow row, String metric) {
        return row == null || row.metrics().get(metric) == null ? BigDecimal.ZERO
                : new BigDecimal(row.metrics().get(metric).toString());
    }

    /**
     * A dashboard date range resolved to concrete first and last days.
     *
     * <p>Purpose: turn range keys such as {@code this_month} or {@code this_quarter} into calendar dates in the tenant's
     * time zone. Quarters and years follow the tenant's fiscal year (e.g. April to March), and unknown keys are rejected
     * with a validation error.
     *
     * @param key  the normalized range key
     * @param from first day of the range (inclusive)
     * @param to   last day of the range (inclusive)
     */
    public record Range(String key, LocalDate from, LocalDate to) {

        static Range of(String key, LocalDate today, int fiscalYearStartMonth) {
            String normalized = key == null ? "this_month" : key.toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "today" -> new Range(normalized, today, today);
                case "this_week" -> new Range(normalized, today.with(java.time.DayOfWeek.MONDAY), today);
                case "this_month" -> new Range(normalized, today.withDayOfMonth(1),
                        today.with(TemporalAdjusters.lastDayOfMonth()));
                case "last_30_days" -> new Range(normalized, today.minusDays(29), today);
                case "this_quarter" -> {
                    LocalDate fiscalStart = fiscalYearStart(today, fiscalYearStartMonth);
                    int monthsIn = (int) java.time.temporal.ChronoUnit.MONTHS.between(fiscalStart, today.withDayOfMonth(1));
                    LocalDate quarterStart = fiscalStart.plusMonths((monthsIn / 3) * 3L);
                    yield new Range(normalized, quarterStart, quarterStart.plusMonths(3).minusDays(1));
                }
                case "this_year" -> {
                    LocalDate fiscalStart = fiscalYearStart(today, fiscalYearStartMonth);
                    yield new Range(normalized, fiscalStart, fiscalStart.plusYears(1).minusDays(1));
                }
                default -> throw ApiException.validation("range", "INVALID",
                        "Use today, this_week, this_month, last_30_days, this_quarter or this_year");
            };
        }

        private static LocalDate fiscalYearStart(LocalDate today, int startMonth) {
            LocalDate start = today.withMonth(startMonth).withDayOfMonth(1);
            return start.isAfter(today) ? start.minusYears(1) : start;
        }
    }
}
