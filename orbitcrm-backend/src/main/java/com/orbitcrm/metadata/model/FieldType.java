package com.orbitcrm.metadata.model;

import static com.orbitcrm.metadata.model.FilterOperator.BETWEEN;
import static com.orbitcrm.metadata.model.FilterOperator.CONTAINS;
import static com.orbitcrm.metadata.model.FilterOperator.CONTAINS_ALL;
import static com.orbitcrm.metadata.model.FilterOperator.EQ;
import static com.orbitcrm.metadata.model.FilterOperator.GT;
import static com.orbitcrm.metadata.model.FilterOperator.GTE;
import static com.orbitcrm.metadata.model.FilterOperator.IN;
import static com.orbitcrm.metadata.model.FilterOperator.IS_EMPTY;
import static com.orbitcrm.metadata.model.FilterOperator.IS_NOT_EMPTY;
import static com.orbitcrm.metadata.model.FilterOperator.LT;
import static com.orbitcrm.metadata.model.FilterOperator.LTE;
import static com.orbitcrm.metadata.model.FilterOperator.NEQ;
import static com.orbitcrm.metadata.model.FilterOperator.NOT_CONTAINS;
import static com.orbitcrm.metadata.model.FilterOperator.NOT_IN;
import static com.orbitcrm.metadata.model.FilterOperator.STARTS_WITH;
import static com.orbitcrm.metadata.model.FilterOperator.WITHIN_LAST_DAYS;
import static com.orbitcrm.metadata.model.FilterOperator.WITHIN_NEXT_DAYS;

import java.util.EnumSet;
import java.util.Set;

import com.orbitcrm.metadata.service.ReferenceResolver;

/**
 * Field types supported by the record engine (PRD section 34).
 *
 * <p>Purpose: define, for each kind of field, how values are stored and validated and which filter operators it
 * supports:
 * <ul>
 *   <li>text: {@code TEXT}, {@code TEXTAREA}, {@code EMAIL}, {@code PHONE}, {@code URL};</li>
 *   <li>numbers: {@code INTEGER}, {@code DECIMAL}, {@code CURRENCY}, {@code PERCENT};</li>
 *   <li>time: {@code DATE}, {@code DATETIME} (including relative operators such as "within the last N days");</li>
 *   <li>choices: {@code BOOLEAN}, {@code PICKLIST}, {@code MULTI_PICKLIST}, {@code TAGS};</li>
 *   <li>references: {@code USER}, {@code MULTI_USER}, {@code LOOKUP} (another record) and {@code REFERENCE}
 *   (a configuration entity such as a pipeline or stage).</li>
 * </ul>
 * Helper methods classify types (array, textual, numeric, temporal, reference) for the converter, the filter
 * compiler and the UI.
 *
 * @author Abhay
 */
public enum FieldType {
    TEXT(textOps()),
    TEXTAREA(EnumSet.of(CONTAINS, NOT_CONTAINS, IS_EMPTY, IS_NOT_EMPTY)),
    EMAIL(textOps()),
    PHONE(textOps()),
    URL(textOps()),
    INTEGER(numberOps()),
    DECIMAL(numberOps()),
    CURRENCY(numberOps()),
    PERCENT(numberOps()),
    DATE(dateOps()),
    DATETIME(dateOps()),
    BOOLEAN(EnumSet.of(EQ, NEQ, IS_EMPTY, IS_NOT_EMPTY)),
    PICKLIST(EnumSet.of(EQ, NEQ, IN, NOT_IN, IS_EMPTY, IS_NOT_EMPTY)),
    MULTI_PICKLIST(arrayOps()),
    TAGS(arrayOps()),
    USER(EnumSet.of(EQ, NEQ, IN, NOT_IN, IS_EMPTY, IS_NOT_EMPTY)),
    MULTI_USER(arrayOps()),
    LOOKUP(EnumSet.of(EQ, NEQ, IN, NOT_IN, IS_EMPTY, IS_NOT_EMPTY)),
    /** Reference to a configuration entity (e.g. pipeline, stage) resolved by a {@link ReferenceResolver}. */
    REFERENCE(EnumSet.of(EQ, NEQ, IN, NOT_IN, IS_EMPTY, IS_NOT_EMPTY));

    private final Set<FilterOperator> operators;

    FieldType(Set<FilterOperator> operators) {
        this.operators = Set.copyOf(operators);
    }

    public Set<FilterOperator> operators() {
        return operators;
    }

    public boolean isArray() {
        return this == MULTI_PICKLIST || this == TAGS || this == MULTI_USER;
    }

    public boolean isTextual() {
        return this == TEXT || this == TEXTAREA || this == EMAIL || this == PHONE || this == URL;
    }

    public boolean isNumeric() {
        return this == INTEGER || this == DECIMAL || this == CURRENCY || this == PERCENT;
    }

    public boolean isTemporal() {
        return this == DATE || this == DATETIME;
    }

    public boolean isReference() {
        return this == USER || this == LOOKUP || this == REFERENCE;
    }

    private static Set<FilterOperator> textOps() {
        return EnumSet.of(EQ, NEQ, CONTAINS, NOT_CONTAINS, STARTS_WITH, IN, NOT_IN, IS_EMPTY, IS_NOT_EMPTY);
    }

    private static Set<FilterOperator> numberOps() {
        return EnumSet.of(EQ, NEQ, GT, GTE, LT, LTE, BETWEEN, IN, IS_EMPTY, IS_NOT_EMPTY);
    }

    private static Set<FilterOperator> dateOps() {
        return EnumSet.of(EQ, NEQ, GT, GTE, LT, LTE, BETWEEN, IS_EMPTY, IS_NOT_EMPTY, WITHIN_LAST_DAYS,
                WITHIN_NEXT_DAYS);
    }

    private static Set<FilterOperator> arrayOps() {
        return EnumSet.of(CONTAINS, NOT_CONTAINS, CONTAINS_ALL, IN, IS_EMPTY, IS_NOT_EMPTY);
    }
}
