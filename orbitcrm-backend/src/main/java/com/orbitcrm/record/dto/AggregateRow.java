package com.orbitcrm.record.dto;

import java.util.Map;

/**
 * One result row of {@code RecordService.aggregate}.
 *
 * <p>Purpose: return the group values of a row both raw (for further filtering and links) and as display labels
 * (for charts and tables), together with the computed metrics.
 *
 * @author Abhay
 * @param groups      group field to raw value (id, picklist key, date bucket)
 * @param groupLabels group field to display label (user name, picklist label, record title, pipeline name)
 * @param metrics     metric name (alias, or {@code function_field}) to computed value
 */
public record AggregateRow(Map<String, Object> groups, Map<String, String> groupLabels, Map<String, Object> metrics) {
}
