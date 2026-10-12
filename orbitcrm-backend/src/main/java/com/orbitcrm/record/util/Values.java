package com.orbitcrm.record.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Lenient conversions from JSON-shaped values (strings, numbers, booleans, lists) to typed Java values.
 *
 * <p>Purpose: shared parsing rules for the field converter, the filter compiler and CSV import, so the same input is
 * accepted everywhere. Examples: numbers may arrive as strings with thousands separators, booleans as
 * {@code yes/no/1/0}, dates as {@code yyyy-MM-dd} (longer strings are cut to the date part), date-times as ISO-8601
 * with offset, and a single value where a list is expected. Blank strings become {@code null}. Invalid input throws an
 * {@code IllegalArgumentException} whose message completes a user-facing sentence, e.g. "must be a number".
 *
 * @author Abhay
 */
public final class Values {

    private Values() {
    }

    public static String text(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            String trimmed = s.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        throw new IllegalArgumentException("must be a string");
    }

    public static BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal d) {
            return d;
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof BigInteger) {
            return new BigDecimal(value.toString());
        }
        if (value instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("must be a finite number");
            }
            return new BigDecimal(value.toString());
        }
        if (value instanceof String s) {
            String trimmed = s.trim().replace(",", "");
            if (trimmed.isEmpty()) {
                return null;
            }
            try {
                return new BigDecimal(trimmed);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("must be a number");
            }
        }
        throw new IllegalArgumentException("must be a number");
    }

    public static Long integer(Object value) {
        BigDecimal decimal = decimal(value);
        if (decimal == null) {
            return null;
        }
        try {
            return decimal.longValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("must be a whole number");
        }
    }

    public static Boolean bool(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "true", "yes", "1" -> true;
                case "false", "no", "0" -> false;
                case "" -> null;
                default -> throw new IllegalArgumentException("must be true or false");
            };
        }
        throw new IllegalArgumentException("must be true or false");
    }

    public static LocalDate date(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate d) {
            return d;
        }
        String s = text(value);
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("must be a date (yyyy-MM-dd)");
        }
    }

    public static Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant i) {
            return i;
        }
        String s = text(value);
        if (s == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (DateTimeParseException ex) {
            try {
                return Instant.parse(s);
            } catch (DateTimeParseException ex2) {
                throw new IllegalArgumentException("must be an ISO-8601 date-time with offset, e.g. 2026-09-28T10:30:00Z");
            }
        }
    }

    public static boolean isDateOnly(Object value) {
        return value instanceof LocalDate || (value instanceof String s && s.trim().matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    public static UUID uuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID u) {
            return u;
        }
        String s = text(value);
        if (s == null) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("must be a valid id");
        }
    }

    public static List<Object> list(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> c) {
            return new ArrayList<>(c);
        }
        if (value instanceof Object[] array) {
            return new ArrayList<>(List.of(array));
        }
        return new ArrayList<>(List.of(value));
    }
}
