package com.orbitcrm.metadata.model;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Operators of the filter language (PRD section 33).
 *
 * <p>Purpose: the shared vocabulary of all filtering: list views, saved views, the pipeline board, dashboards,
 * exports and, later, reports, marketing lists and workflow conditions. Operators are written in lower case on the
 * wire ({@code eq}, {@code starts_with}, {@code within_last_days}, ...). Which operators a field supports depends on
 * its {@link FieldType}; {@code is_empty} and {@code is_not_empty} take no value.
 *
 * @author Abhay
 */
public enum FilterOperator {
    EQ,
    NEQ,
    CONTAINS,
    NOT_CONTAINS,
    STARTS_WITH,
    GT,
    GTE,
    LT,
    LTE,
    BETWEEN,
    IN,
    NOT_IN,
    CONTAINS_ALL,
    IS_EMPTY,
    IS_NOT_EMPTY,
    WITHIN_LAST_DAYS,
    WITHIN_NEXT_DAYS;

    @JsonValue
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static FilterOperator fromWire(String value) {
        if (value == null) {
            return null;
        }
        return FilterOperator.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public boolean takesNoValue() {
        return this == IS_EMPTY || this == IS_NOT_EMPTY;
    }
}
