package com.orbitcrm.record.dto;

/**
 * One metric of an {@link AggregateRequest}, e.g. the sum of {@code amount}.
 *
 * <p>Purpose: describe what to compute per group. Supported functions are {@code count}, {@code sum}, {@code avg},
 * {@code min} and {@code max}; sum, avg, min and max require a numeric field.
 *
 * @author Abhay
 * @param function aggregate function name
 * @param field    field to aggregate; {@code null} for {@code count(*)}
 * @param alias    optional name of the metric in the result; defaults to {@code function_field}
 */
public record Metric(String function, String field, String alias) {

    public static Metric count() {
        return new Metric("count", null, "count");
    }

    public static Metric sum(String field) {
        return new Metric("sum", field, "sum_" + field);
    }

    public String name() {
        return alias != null ? alias : field == null ? function : function + "_" + field;
    }
}
