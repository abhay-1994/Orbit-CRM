package com.orbitcrm.record.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.orbitcrm.metadata.model.FieldDefinition;
import com.orbitcrm.metadata.model.FieldType;
import com.orbitcrm.metadata.model.ModuleDefinition;
import com.orbitcrm.metadata.model.PicklistOption;
import com.orbitcrm.metadata.service.PicklistService;
import com.orbitcrm.record.util.Values;
import com.orbitcrm.shared.dto.FieldErrorDetail;

/**
 * Converts and validates API input against field metadata, producing typed values.
 *
 * <p>Purpose: turn loosely typed JSON input into the exact Java types the repository stores - String, Long,
 * BigDecimal, LocalDate, Instant, Boolean, UUID, {@code List<String>} and {@code List<UUID>} - and collect every
 * problem as a field-level error instead of failing on the first. Rules include:
 * <ul>
 *   <li>unknown fields are errors; reserved keys ({@code id}, {@code version}, {@code recordNumber}, {@code title},
 *   {@code module}) and read-only fields are silently ignored, so clients can send whole records back;</li>
 *   <li>text length limits, email and phone formats, http(s) URLs (a missing scheme becomes {@code https://});</li>
 *   <li>numeric ranges, currency and percent rounded to two decimals, percent between 0 and 100;</li>
 *   <li>picklist values must be active options - unless the value is unchanged, so deactivating an option never
 *   blocks editing old records;</li>
 *   <li>tags are lower-cased and limited (20 tags, 40 characters, letters, digits, space, {@code _.-}); multi-value
 *   fields are de-duplicated and limited to 50 values.</li>
 * </ul>
 * Reference existence (users, lookups, references) is checked separately by the engine.
 *
 * @author Abhay
 */
@Component
public class FieldValueConverter {

    public static final Set<String> RESERVED_INPUT_KEYS = Set.of("id", "version", "recordNumber", "title", "module");

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^[+0-9 ()\\-./x]{3,40}$");
    private static final Pattern TAG = Pattern.compile("^[\\p{L}\\p{N} _.\\-]{1,40}$");
    private static final int MAX_TAGS = 20;
    private static final int MAX_MULTI = 50;

    private final PicklistService picklists;

    FieldValueConverter(PicklistService picklists) {
        this.picklists = picklists;
    }

    /**
     * @param existing current values (update) or {@code null} (create); unchanged values skip the "active
     *                 picklist value" rule so deactivating a value never blocks editing old records
     */
    public Map<String, Object> convert(ModuleDefinition module, Map<String, Object> input, Map<String, Object> existing,
                                boolean allowReadOnly, List<FieldErrorDetail> errors) {
        Map<String, Object> typed = new LinkedHashMap<>();
        if (input == null) {
            return typed;
        }
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            if (RESERVED_INPUT_KEYS.contains(key)) {
                continue;
            }
            FieldDefinition field = module.field(key).orElse(null);
            if (field == null) {
                errors.add(new FieldErrorDetail(key, "UNKNOWN_FIELD", "Unknown field"));
                continue;
            }
            if (field.readOnly() && !allowReadOnly) {
                continue; // system-maintained; ignored so clients can send back whole records
            }
            try {
                Object value = convertValue(field, entry.getValue());
                Object previous = existing == null ? null : existing.get(key);
                validatePicklist(field, value, previous);
                typed.put(key, value);
            } catch (IllegalArgumentException ex) {
                errors.add(new FieldErrorDetail(key, codeFor(field), field.label() + " " + ex.getMessage()));
            }
        }
        return typed;
    }

    public Object convertValue(FieldDefinition field, Object raw) {
        return switch (field.type()) {
            case TEXT, TEXTAREA -> length(field, Values.text(raw));
            case EMAIL -> email(field, Values.text(raw));
            case PHONE -> phone(field, Values.text(raw));
            case URL -> url(field, Values.text(raw));
            case INTEGER -> range(field, Values.integer(raw));
            case DECIMAL -> range(field, Values.decimal(raw));
            case CURRENCY -> range(field, scale(Values.decimal(raw), 2));
            case PERCENT -> percent(field, Values.decimal(raw));
            case DATE -> Values.date(raw);
            case DATETIME -> Values.instant(raw);
            case BOOLEAN -> Values.bool(raw);
            case PICKLIST -> Values.text(raw);
            case MULTI_PICKLIST -> distinctStrings(raw, false);
            case TAGS -> tags(raw);
            case USER, LOOKUP, REFERENCE -> Values.uuid(raw);
            case MULTI_USER -> distinctUuids(raw);
        };
    }

    private void validatePicklist(FieldDefinition field, Object value, Object previous) {
        if (value == null || !field.hasPicklist()) {
            return;
        }
        if (field.type() == FieldType.PICKLIST) {
            if (!Objects.equals(value, previous) && !isAllowed(field, (String) value)) {
                throw new IllegalArgumentException("has an invalid value '" + value + "'");
            }
        } else if (field.type() == FieldType.MULTI_PICKLIST) {
            @SuppressWarnings("unchecked")
            List<String> values = (List<String>) value;
            @SuppressWarnings("unchecked")
            List<String> old = previous instanceof List<?> l ? (List<String>) l : List.of();
            for (String v : values) {
                if (!old.contains(v) && !isAllowed(field, v)) {
                    throw new IllegalArgumentException("has an invalid value '" + v + "'");
                }
            }
        }
    }

    private boolean isAllowed(FieldDefinition field, String value) {
        if (field.picklist() != null) {
            return picklists.isActiveValue(field.picklist(), value);
        }
        return field.fixedOptions().stream().map(PicklistOption::key).anyMatch(value::equals);
    }

    private static String length(FieldDefinition field, String value) {
        if (value != null && field.maxLength() != null && value.length() > field.maxLength()) {
            throw new IllegalArgumentException("must be at most " + field.maxLength() + " characters");
        }
        return value;
    }

    private static String email(FieldDefinition field, String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(normalized).matches()) {
            throw new IllegalArgumentException("must be a valid email address");
        }
        return length(field, normalized);
    }

    private static String phone(FieldDefinition field, String value) {
        if (value == null) {
            return null;
        }
        long digits = value.chars().filter(Character::isDigit).count();
        if (!PHONE.matcher(value).matches() || digits < 3 || digits > 20) {
            throw new IllegalArgumentException("must be a valid phone number");
        }
        return length(field, value);
    }

    private static String url(FieldDefinition field, String value) {
        if (value == null) {
            return null;
        }
        String candidate = value.contains("://") ? value : "https://" + value;
        try {
            URI uri = new URI(candidate);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
                throw new IllegalArgumentException("must be an http(s) URL");
            }
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("must be a valid URL");
        }
        return length(field, candidate);
    }

    private static <T extends Comparable<T>> T range(FieldDefinition field, T value) {
        if (value == null) {
            return null;
        }
        BigDecimal number = new BigDecimal(value.toString());
        if (field.min() != null && number.compareTo(field.min()) < 0) {
            throw new IllegalArgumentException("must be at least " + field.min().toPlainString());
        }
        if (field.max() != null && number.compareTo(field.max()) > 0) {
            throw new IllegalArgumentException("must be at most " + field.max().toPlainString());
        }
        if (number.abs().compareTo(new BigDecimal("1000000000000000")) >= 0) {
            throw new IllegalArgumentException("is too large");
        }
        return value;
    }

    private static BigDecimal percent(FieldDefinition field, BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("must be between 0 and 100");
        }
        return scale(value, 2);
    }

    private static BigDecimal scale(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP);
    }

    private static List<String> distinctStrings(Object raw, boolean lower) {
        List<Object> items = Values.list(raw);
        if (items.size() > MAX_MULTI) {
            throw new IllegalArgumentException("accepts at most " + MAX_MULTI + " values");
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object item : items) {
            String text = Values.text(item);
            if (text != null) {
                result.add(lower ? text.toLowerCase(Locale.ROOT) : text);
            }
        }
        return result.isEmpty() ? null : List.copyOf(result);
    }

    private static List<String> tags(Object raw) {
        List<String> tags = distinctStrings(raw, true);
        if (tags == null) {
            return null;
        }
        if (tags.size() > MAX_TAGS) {
            throw new IllegalArgumentException("accepts at most " + MAX_TAGS + " tags");
        }
        for (String tag : tags) {
            if (!TAG.matcher(tag).matches()) {
                throw new IllegalArgumentException("contains an invalid tag '" + tag + "'");
            }
        }
        return tags;
    }

    private static List<UUID> distinctUuids(Object raw) {
        List<Object> items = Values.list(raw);
        if (items.size() > MAX_MULTI) {
            throw new IllegalArgumentException("accepts at most " + MAX_MULTI + " values");
        }
        Set<UUID> result = new LinkedHashSet<>();
        for (Object item : items) {
            UUID id = Values.uuid(item);
            if (id != null) {
                result.add(id);
            }
        }
        return result.isEmpty() ? null : new ArrayList<>(result);
    }

    private static String codeFor(FieldDefinition field) {
        return switch (field.type()) {
            case EMAIL -> "INVALID_EMAIL";
            case PHONE -> "INVALID_PHONE";
            case URL -> "INVALID_URL";
            case TEXT, TEXTAREA -> "INVALID_LENGTH";
            case PICKLIST, MULTI_PICKLIST -> "INVALID_OPTION";
            default -> "INVALID";
        };
    }
}
