package com.orbitcrm.record.dto;

import java.util.List;

/**
 * Input of {@code RecordService.aggregate}: a grouped aggregation over the records of one module.
 *
 * <p>Purpose: the reporting primitive behind the pipeline board totals, the dashboard widgets and, later, the report
 * builder. Only records visible to the caller are aggregated.
 *
 * @author Abhay
 * @param filter  optional filter in the filter language (JSON form or a {@code FilterNode})
 * @param groupBy up to two fields; date fields accept a bucket suffix, e.g. {@code closedAt:month}
 *                (day, week, month, quarter, year), bucketed in the tenant's time zone
 * @param metrics up to six metrics, e.g. {@code {"function":"sum","field":"amount"}}; count needs no field;
 *                defaults to a single count
 * @param limit   maximum number of result rows (default 100, at most 1000)
 */
public record AggregateRequest(Object filter, List<String> groupBy, List<Metric> metrics, Integer limit) {
}
