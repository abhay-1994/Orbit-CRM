package com.orbitcrm.record.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.orbitcrm.metadata.model.FilterOperator;
import com.orbitcrm.shared.exception.ApiException;

/**
 * The filter language as an abstract syntax tree.
 *
 * <p>Purpose: one structure for every filter in the product: list filters, saved views, the pipeline board,
 * dashboards, exports and later reports, marketing lists and workflow conditions. A node is either a {@code Group}
 * (AND / OR over children) or a {@code Condition} (field, operator, value). JSON form:
 * <pre>
 * {"and": [
 *    {"field": "status", "op": "eq", "value": "qualified"},
 *    {"or": [{"field": "industry", "op": "in", "value": ["it", "finance"]},
 *            {"field": "annualRevenue", "op": "gt", "value": 1000000}]}
 * ]}
 * </pre>
 * {@link #parse} performs the structural parse of that JSON (a plain list means AND, empty groups disappear) and
 * limits nesting to 6 levels and 100 conditions. Field names and operators are validated against module metadata
 * when the tree is compiled to SQL by {@code SqlFilterCompiler}; values are always bound as SQL parameters. Factory
 * methods such as {@code and}, {@code or} and {@code eq} let internal code build filters directly.
 *
 * @author Abhay
 */
public sealed interface FilterNode permits FilterNode.Group, FilterNode.Condition {

    int MAX_DEPTH = 6;
    int MAX_CONDITIONS = 100;

    /**
     * How a {@link Group} combines its children.
     *
     * <p>Purpose: {@code AND} requires all children to match, {@code OR} at least one.
     */
    enum Logic { AND, OR }

    /**
     * A group node of the filter language: children combined with AND or OR.
     *
     * <p>Purpose: build nested boolean filters, written in JSON as {@code {"and": [...]}} or {@code {"or": [...]}}.
     *
     * @param logic    how the children are combined
     * @param children child nodes (conditions or further groups)
     */
    record Group(Logic logic, List<FilterNode> children) implements FilterNode {
    }

    /**
     * A condition node of the filter language: one field compared with a value.
     *
     * <p>Purpose: the leaf of every filter, written in JSON as {@code {"field": ..., "op": ..., "value": ...}}. The field
     * and operator are validated against module metadata when compiled; the value may be a literal, a list, a range or a
     * dynamic token such as {@code $me} or {@code $today}.
     *
     * @param field field key
     * @param op    operator
     * @param value comparison value; ignored for {@code is_empty} / {@code is_not_empty}
     */
    record Condition(String field, FilterOperator op, Object value) implements FilterNode {
    }

    static FilterNode and(FilterNode... nodes) {
        return new Group(Logic.AND, Arrays.stream(nodes).filter(n -> n != null).toList());
    }

    static FilterNode or(FilterNode... nodes) {
        return new Group(Logic.OR, Arrays.stream(nodes).filter(n -> n != null).toList());
    }

    static Condition condition(String field, FilterOperator op, Object value) {
        return new Condition(field, op, value);
    }

    static Condition eq(String field, Object value) {
        return new Condition(field, FilterOperator.EQ, value);
    }

    /**
     * Structural parse of the JSON form (maps and lists as produced by a JSON library). An already-built
     * {@link FilterNode} is returned unchanged, so internal callers can pass ASTs where the wire format is
     * accepted. Returns {@code null} for an empty filter.
     */
    static FilterNode parse(Object json) {
        if (json instanceof FilterNode node) {
            return node;
        }
        int[] conditions = {0};
        return parse(json, 1, conditions);
    }

    @SuppressWarnings("unchecked")
    private static FilterNode parse(Object json, int depth, int[] conditions) {
        if (json == null) {
            return null;
        }
        if (depth > MAX_DEPTH) {
            throw invalid("Filter is nested too deeply (max " + MAX_DEPTH + ")");
        }
        if (json instanceof List<?> list) {
            return group(Logic.AND, list, depth, conditions);
        }
        if (!(json instanceof Map<?, ?> raw)) {
            throw invalid("Filter nodes must be objects");
        }
        Map<String, Object> map = (Map<String, Object>) raw;
        if (map.isEmpty()) {
            return null;
        }
        if (map.containsKey("and") || map.containsKey("or")) {
            if (map.size() != 1) {
                throw invalid("A group must contain exactly one of 'and' / 'or'");
            }
            Logic logic = map.containsKey("and") ? Logic.AND : Logic.OR;
            Object children = map.get(logic == Logic.AND ? "and" : "or");
            if (!(children instanceof List<?> list)) {
                throw invalid("'" + logic.name().toLowerCase() + "' must be an array");
            }
            return group(logic, list, depth, conditions);
        }
        Object field = map.get("field");
        Object op = map.get("op");
        if (!(field instanceof String fieldName) || fieldName.isBlank() || !(op instanceof String opName)) {
            throw invalid("A condition needs 'field' and 'op'");
        }
        if (++conditions[0] > MAX_CONDITIONS) {
            throw invalid("Filter has too many conditions (max " + MAX_CONDITIONS + ")");
        }
        FilterOperator operator;
        try {
            operator = FilterOperator.fromWire(opName);
        } catch (IllegalArgumentException ex) {
            throw invalid("Unknown operator: " + opName);
        }
        return new Condition(fieldName, operator, map.get("value"));
    }

    private static FilterNode group(Logic logic, List<?> items, int depth, int[] conditions) {
        List<FilterNode> children = new ArrayList<>();
        for (Object item : items) {
            FilterNode child = parse(item, depth + 1, conditions);
            if (child != null) {
                children.add(child);
            }
        }
        return children.isEmpty() ? null : new Group(logic, List.copyOf(children));
    }

    private static ApiException invalid(String message) {
        return ApiException.validation("filter", "INVALID_FILTER", message);
    }
}
