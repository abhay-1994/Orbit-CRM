package com.orbitcrm.record.query;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.support.SqlArrayValue;

import com.orbitcrm.metadata.model.FieldDefinition;
import com.orbitcrm.metadata.model.FieldType;
import com.orbitcrm.metadata.model.FilterOperator;
import com.orbitcrm.metadata.model.ModuleDefinition;
import com.orbitcrm.record.model.FilterNode;
import com.orbitcrm.record.util.Values;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.security.CurrentUser;

/**
 * Compiles a {@link com.orbitcrm.record.model.FilterNode} into a SQL predicate over the table alias {@code t}.
 *
 * <p>Purpose: execute the filter language safely and consistently. Column names come only from module metadata and
 * every value is a bound parameter, so filters cannot inject SQL. For each condition it checks that the field exists
 * and is filterable and that the operator is supported by the field type, then generates type-specific SQL:
 * case-insensitive text comparison and {@code ILIKE} with escaped wildcards, numeric ranges, array overlap and
 * containment for tags and multi-selects, and date / date-time comparisons.
 * <ul>
 *   <li>Dynamic values: {@code $me} and {@code $myTeam} for user fields; {@code $today}, {@code $now},
 *   {@code $startOfWeek}, {@code $startOfMonth}, {@code $endOfMonth} and {@code $startOfYear} for dates.</li>
 *   <li>A date-only value on a date-time field means the whole calendar day in the tenant's time zone.</li>
 *   <li>"Not equal" and "not in" also match empty values, which is what users expect.</li>
 * </ul>
 * Invalid filters produce {@code 400} validation errors pointing at {@code filter.<field>}.
 *
 * @author Abhay
 */
public final class SqlFilterCompiler {

    private final ModuleDefinition module;
    private final CurrentUser user;
    private final ZoneId zone;
    private final MapSqlParameterSource params;
    private final String prefix;
    private int counter;

    public SqlFilterCompiler(ModuleDefinition module, CurrentUser user, ZoneId zone, MapSqlParameterSource params,
                      String prefix) {
        this.module = module;
        this.user = user;
        this.zone = zone;
        this.params = params;
        this.prefix = prefix;
    }

    public String compile(FilterNode node) {
        if (node == null) {
            return null;
        }
        return switch (node) {
            case FilterNode.Group group -> group(group);
            case FilterNode.Condition condition -> condition(condition);
        };
    }

    private String group(FilterNode.Group group) {
        List<String> parts = new ArrayList<>();
        for (FilterNode child : group.children()) {
            String sql = compile(child);
            if (sql != null) {
                parts.add("(" + sql + ")");
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join(group.logic() == FilterNode.Logic.AND ? " AND " : " OR ", parts);
    }

    private String condition(FilterNode.Condition c) {
        FieldDefinition field = module.field(c.field())
                .filter(FieldDefinition::filterable)
                .orElseThrow(() -> invalid(c.field(), "UNKNOWN_FIELD", "Cannot filter on unknown field '" + c.field() + "'"));
        FilterOperator op = c.op();
        if (!field.type().operators().contains(op)) {
            throw invalid(c.field(), "UNSUPPORTED_OPERATOR",
                    "Operator '" + op.wireName() + "' is not supported for " + field.type() + " fields");
        }
        String col = "t." + field.column();
        try {
            if (op == FilterOperator.IS_EMPTY) {
                return empty(field, col);
            }
            if (op == FilterOperator.IS_NOT_EMPTY) {
                return "NOT " + empty(field, col);
            }
            if (c.value() == null) {
                throw new IllegalArgumentException("a value is required");
            }
            FieldType type = field.type();
            if (type.isArray()) {
                return array(field, col, op, c.value());
            }
            if (type.isTextual() || type == FieldType.PICKLIST) {
                return text(field, col, op, c.value());
            }
            if (type.isNumeric()) {
                return number(field, col, op, c.value());
            }
            if (type == FieldType.DATE) {
                return date(col, op, c.value());
            }
            if (type == FieldType.DATETIME) {
                return dateTime(col, op, c.value());
            }
            if (type == FieldType.BOOLEAN) {
                String p = bind(Values.bool(c.value()));
                return op == FilterOperator.EQ ? col + " = " + p : "(" + col + " IS NULL OR " + col + " <> " + p + ")";
            }
            if (type.isReference()) {
                return reference(field, col, op, c.value());
            }
            throw new IllegalArgumentException("unsupported field type");
        } catch (IllegalArgumentException ex) {
            throw invalid(c.field(), "INVALID_FILTER_VALUE", "Filter on '" + c.field() + "': value " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ by type

    private static String empty(FieldDefinition field, String col) {
        if (field.type().isArray()) {
            return "(" + col + " IS NULL OR cardinality(" + col + ") = 0)";
        }
        if (field.type().isTextual() || field.type() == FieldType.PICKLIST) {
            return "(" + col + " IS NULL OR " + col + " = '')";
        }
        return "(" + col + " IS NULL)";
    }

    private String text(FieldDefinition field, String col, FilterOperator op, Object value) {
        boolean exact = field.type() == FieldType.PICKLIST;
        String lhs = exact ? col : "lower(" + col + ")";
        return switch (op) {
            case EQ -> lhs + " = " + bind(normalizeText(Values.text(value), exact));
            case NEQ -> "(" + col + " IS NULL OR " + lhs + " <> " + bind(normalizeText(Values.text(value), exact)) + ")";
            case CONTAINS -> col + " ILIKE " + bind("%" + escapeLike(Values.text(value)) + "%") + " ESCAPE '\\'";
            case NOT_CONTAINS -> "(" + col + " IS NULL OR " + col + " NOT ILIKE "
                    + bind("%" + escapeLike(Values.text(value)) + "%") + " ESCAPE '\\')";
            case STARTS_WITH -> col + " ILIKE " + bind(escapeLike(Values.text(value)) + "%") + " ESCAPE '\\'";
            case IN, NOT_IN -> {
                List<String> items = Values.list(value).stream().map(Values::text)
                        .filter(v -> v != null).map(v -> normalizeText(v, exact)).toList();
                if (items.isEmpty()) {
                    yield op == FilterOperator.IN ? "FALSE" : "TRUE";
                }
                String p = bind(items);
                yield op == FilterOperator.IN ? lhs + " IN (" + p + ")"
                        : "(" + col + " IS NULL OR " + lhs + " NOT IN (" + p + "))";
            }
            default -> throw new IllegalArgumentException("unsupported operator");
        };
    }

    private String number(FieldDefinition field, String col, FilterOperator op, Object value) {
        boolean integer = field.type() == FieldType.INTEGER;
        return switch (op) {
            case EQ -> col + " = " + bind(num(value, integer));
            case NEQ -> "(" + col + " IS NULL OR " + col + " <> " + bind(num(value, integer)) + ")";
            case GT -> col + " > " + bind(num(value, integer));
            case GTE -> col + " >= " + bind(num(value, integer));
            case LT -> col + " < " + bind(num(value, integer));
            case LTE -> col + " <= " + bind(num(value, integer));
            case BETWEEN -> {
                List<Object> range = pair(value);
                yield col + " BETWEEN " + bind(num(range.get(0), integer)) + " AND " + bind(num(range.get(1), integer));
            }
            case IN -> {
                List<Object> items = Values.list(value).stream().map(v -> num(v, integer)).toList();
                yield items.isEmpty() ? "FALSE" : col + " IN (" + bind(items) + ")";
            }
            default -> throw new IllegalArgumentException("unsupported operator");
        };
    }

    private String date(String col, FilterOperator op, Object value) {
        LocalDate today = LocalDate.now(zone);
        return switch (op) {
            case EQ -> col + " = " + bind(localDate(value));
            case NEQ -> "(" + col + " IS NULL OR " + col + " <> " + bind(localDate(value)) + ")";
            case GT -> col + " > " + bind(localDate(value));
            case GTE -> col + " >= " + bind(localDate(value));
            case LT -> col + " < " + bind(localDate(value));
            case LTE -> col + " <= " + bind(localDate(value));
            case BETWEEN -> {
                List<Object> range = pair(value);
                yield col + " BETWEEN " + bind(localDate(range.get(0))) + " AND " + bind(localDate(range.get(1)));
            }
            case WITHIN_LAST_DAYS -> col + " BETWEEN " + bind(today.minusDays(days(value))) + " AND " + bind(today);
            case WITHIN_NEXT_DAYS -> col + " BETWEEN " + bind(today) + " AND " + bind(today.plusDays(days(value)));
            default -> throw new IllegalArgumentException("unsupported operator");
        };
    }

    private String dateTime(String col, FilterOperator op, Object value) {
        Instant now = Instant.now();
        if (op == FilterOperator.WITHIN_LAST_DAYS) {
            return col + " >= " + bind(ts(now.minus(days(value), ChronoUnit.DAYS))) + " AND " + col + " <= " + bind(ts(now));
        }
        if (op == FilterOperator.WITHIN_NEXT_DAYS) {
            return col + " >= " + bind(ts(now)) + " AND " + col + " <= " + bind(ts(now.plus(days(value), ChronoUnit.DAYS)));
        }
        if (op == FilterOperator.BETWEEN) {
            List<Object> range = pair(value);
            Bound from = bound(range.get(0));
            Bound to = bound(range.get(1));
            String upper = to.day() ? col + " < " + bind(ts(to.dayEnd())) : col + " <= " + bind(ts(to.instant()));
            return col + " >= " + bind(ts(from.day() ? from.dayStart() : from.instant())) + " AND " + upper;
        }
        Bound b = bound(value);
        if (b.day()) {
            String start = bind(ts(b.dayStart()));
            String end = bind(ts(b.dayEnd()));
            return switch (op) {
                case EQ -> col + " >= " + start + " AND " + col + " < " + end;
                case NEQ -> "(" + col + " IS NULL OR " + col + " < " + start + " OR " + col + " >= " + end + ")";
                case GT -> col + " >= " + end;
                case GTE -> col + " >= " + start;
                case LT -> col + " < " + start;
                case LTE -> col + " < " + end;
                default -> throw new IllegalArgumentException("unsupported operator");
            };
        }
        String p = bind(ts(b.instant()));
        return switch (op) {
            case EQ -> col + " = " + p;
            case NEQ -> "(" + col + " IS NULL OR " + col + " <> " + p + ")";
            case GT -> col + " > " + p;
            case GTE -> col + " >= " + p;
            case LT -> col + " < " + p;
            case LTE -> col + " <= " + p;
            default -> throw new IllegalArgumentException("unsupported operator");
        };
    }

    private String reference(FieldDefinition field, String col, FilterOperator op, Object value) {
        if (op == FilterOperator.EQ || op == FilterOperator.NEQ) {
            String predicate = referencePredicate(field, col, List.of(value));
            return op == FilterOperator.EQ ? predicate : "(" + col + " IS NULL OR NOT (" + predicate + "))";
        }
        List<Object> items = Values.list(value);
        if (items.isEmpty()) {
            return op == FilterOperator.IN ? "FALSE" : "TRUE";
        }
        String predicate = referencePredicate(field, col, items);
        return op == FilterOperator.IN ? predicate : "(" + col + " IS NULL OR NOT (" + predicate + "))";
    }

    /** Expands $me / $myTeam and explicit ids into one predicate. */
    private String referencePredicate(FieldDefinition field, String col, List<Object> items) {
        List<UUID> ids = new ArrayList<>();
        List<String> parts = new ArrayList<>();
        for (Object item : items) {
            if ("$me".equals(item)) {
                ids.add(user.userId());
            } else if ("$myTeam".equals(item) && field.type() == FieldType.USER) {
                Set<UUID> teams = user.teamIds();
                if (teams.isEmpty()) {
                    ids.add(user.userId());
                } else {
                    parts.add(col + " IN (SELECT fu.id FROM users fu WHERE fu.team_id IN (" + bind(List.copyOf(teams)) + "))");
                }
            } else {
                ids.add(Values.uuid(item));
            }
        }
        if (!ids.isEmpty()) {
            parts.add(ids.size() == 1 ? col + " = " + bind(ids.get(0)) : col + " IN (" + bind(ids) + ")");
        }
        return parts.size() == 1 ? parts.get(0) : "(" + String.join(" OR ", parts) + ")";
    }

    private String array(FieldDefinition field, String col, FilterOperator op, Object value) {
        List<Object> items = Values.list(value);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("needs at least one value");
        }
        SqlArrayValue array;
        if (field.type() == FieldType.MULTI_USER) {
            UUID[] ids = items.stream().map(i -> "$me".equals(i) ? user.userId() : Values.uuid(i)).toArray(UUID[]::new);
            array = new SqlArrayValue("uuid", (Object[]) ids);
        } else {
            boolean lower = field.type() == FieldType.TAGS;
            String[] values = items.stream().map(Values::text).filter(v -> v != null)
                    .map(v -> lower ? v.toLowerCase(Locale.ROOT) : v).toArray(String[]::new);
            array = new SqlArrayValue("text", (Object[]) values);
        }
        String p = bind(array);
        return switch (op) {
            case CONTAINS, IN -> col + " && " + p;
            case CONTAINS_ALL -> col + " @> " + p;
            case NOT_CONTAINS -> "NOT (coalesce(" + col + ", '{}') && " + p + ")";
            default -> throw new IllegalArgumentException("unsupported operator");
        };
    }

    // ------------------------------------------------------------------ values

    private Object num(Object value, boolean integer) {
        return integer ? Values.integer(value) : Values.decimal(value);
    }

    private LocalDate localDate(Object value) {
        LocalDate token = dateToken(value);
        return token != null ? token : Values.date(value);
    }

    private LocalDate dateToken(Object value) {
        if (!(value instanceof String s) || !s.startsWith("$")) {
            return null;
        }
        LocalDate today = LocalDate.now(zone);
        return switch (s) {
            case "$today", "$now" -> today;
            case "$startOfWeek" -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "$startOfMonth" -> today.withDayOfMonth(1);
            case "$endOfMonth" -> today.with(TemporalAdjusters.lastDayOfMonth());
            case "$startOfYear" -> today.withDayOfYear(1);
            default -> throw new IllegalArgumentException("uses unknown token " + s);
        };
    }

    private Bound bound(Object value) {
        if ("$now".equals(value)) {
            return Bound.instant(Instant.now());
        }
        LocalDate token = dateToken(value);
        if (token != null) {
            return Bound.day(token, zone);
        }
        if (Values.isDateOnly(value)) {
            return Bound.day(Values.date(value), zone);
        }
        return Bound.instant(Values.instant(value));
    }

    /**
     * A date-time filter bound: either an exact instant or a whole calendar day in the tenant's time zone.
     *
     * <p>Purpose: give date-only values on date-time fields their intuitive meaning, e.g. "created on 2026-09-30" matches
     * the whole day from midnight to midnight in the tenant's zone.
     *
     * @param day      whether the bound is a whole day
     * @param instant  the exact instant (when not a day)
     * @param dayStart start of the day (inclusive, when a day)
     * @param dayEnd   start of the next day (exclusive, when a day)
     */
    private record Bound(boolean day, Instant instant, Instant dayStart, Instant dayEnd) {

        static Bound instant(Instant instant) {
            return new Bound(false, instant, null, null);
        }

        static Bound day(LocalDate date, ZoneId zone) {
            return new Bound(true, null, date.atStartOfDay(zone).toInstant(),
                    date.plusDays(1).atStartOfDay(zone).toInstant());
        }
    }

    private static List<Object> pair(Object value) {
        List<Object> range = Values.list(value);
        if (range.size() != 2 || range.get(0) == null || range.get(1) == null) {
            throw new IllegalArgumentException("must be an array of two values [from, to]");
        }
        return range;
    }

    private static long days(Object value) {
        Long days = Values.integer(value);
        if (days == null || days < 0 || days > 3650) {
            throw new IllegalArgumentException("must be a number of days between 0 and 3650");
        }
        return days;
    }

    private static String normalizeText(String value, boolean exact) {
        return exact || value == null ? value : value.toLowerCase(Locale.ROOT);
    }

    public static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static OffsetDateTime ts(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private String bind(Object value) {
        String name = prefix + (counter++);
        params.addValue(name, value);
        return ":" + name;
    }

    private static ApiException invalid(String field, String code, String message) {
        return ApiException.validation("filter." + field, code, message);
    }

}
