package com.orbitcrm.dataio.mapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.orbitcrm.identity.service.UserDirectory;
import com.orbitcrm.metadata.model.FieldDefinition;
import com.orbitcrm.metadata.model.FieldType;
import com.orbitcrm.metadata.model.ModuleDefinition;
import com.orbitcrm.metadata.model.PicklistOption;
import com.orbitcrm.metadata.service.ModuleRegistry;
import com.orbitcrm.metadata.service.PicklistService;
import com.orbitcrm.record.dto.RecordView;
import com.orbitcrm.record.dto.SearchRequest;
import com.orbitcrm.record.model.FilterNode;
import com.orbitcrm.record.service.RecordService;

/**
 * Turns spreadsheet cell text into API values for a field, and suggests column mappings.
 *
 * <p>Purpose: be forgiving about what people put in spreadsheets, so exports and typical files re-import without
 * editing:
 * <ul>
 *   <li>picklist values by label as well as by key (case-insensitive);</li>
 *   <li>owners and other users by id, email or unique full name;</li>
 *   <li>related records (lookups) by id or by exact, unique title;</li>
 *   <li>dates as {@code yyyy-MM-dd}, {@code dd/MM/yyyy}, {@code dd-MM-yyyy} or {@code d/M/yyyy};</li>
 *   <li>multi-values (tags, multi-selects, multiple users) separated by semicolons.</li>
 * </ul>
 * Other values are passed through to the record engine's converter. {@link #suggestMapping} matches column headers to
 * fields by normalized key or label ("E-mail Address" matches {@code email}, "Owner" or "Assigned to" match the
 * owner).
 *
 * @author Abhay
 */
@Component
public class CsvValueMapper {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ofPattern("dd-MM-uuuu"),
            DateTimeFormatter.ofPattern("d/M/uuuu"));

    private final PicklistService picklists;
    private final UserDirectory users;
    private final RecordService records;
    private final ModuleRegistry registry;

    CsvValueMapper(PicklistService picklists, UserDirectory users, RecordService records, ModuleRegistry registry) {
        this.picklists = picklists;
        this.users = users;
        this.records = records;
        this.registry = registry;
    }

    /** Returns the API value, or throws {@link IllegalArgumentException} with a user-facing message. */
    public Object map(FieldDefinition field, String cell) {
        String text = cell == null ? "" : cell.trim();
        if (text.isEmpty()) {
            return null;
        }
        return switch (field.type()) {
            case PICKLIST -> option(field, text);
            case MULTI_PICKLIST -> split(text).stream().map(v -> option(field, v)).toList();
            case TAGS -> split(text);
            case USER -> user(text).toString();
            case MULTI_USER -> split(text).stream().map(v -> user(v).toString()).toList();
            case LOOKUP -> lookup(field, text).toString();
            case DATE -> date(text).toString();
            default -> text;
        };
    }

    private String option(FieldDefinition field, String text) {
        List<PicklistOption> options = field.picklist() != null
                ? picklists.values(field.picklist()).stream()
                        .map(v -> new PicklistOption(v.key(), v.label(), v.color(), v.isDefault(), v.system())).toList()
                : field.fixedOptions();
        return options.stream()
                .filter(o -> o.key().equalsIgnoreCase(text) || o.label().equalsIgnoreCase(text))
                .map(PicklistOption::key)
                .findFirst()
                .orElse(text);
    }

    private UUID user(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException notAnId) {
            return users.findActiveId(text)
                    .orElseThrow(() -> new IllegalArgumentException("no active user matches '" + text + "'"));
        }
    }

    private UUID lookup(FieldDefinition field, String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException notAnId) {
            if (field.lookupModule() == null) {
                throw new IllegalArgumentException("needs a record id");
            }
            ModuleDefinition target = registry.require(field.lookupModule());
            String titleField = target.titleFields().get(target.titleFields().size() - 1);
            List<RecordView> matches = records.search(target.key(), new SearchRequest(
                    FilterNode.eq(titleField, text), null, null, 0, 2)).content();
            Optional<RecordView> exact = matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
            return exact.map(RecordView::id).orElseThrow(() -> new IllegalArgumentException(matches.isEmpty()
                    ? "no " + target.singularLabel().toLowerCase(Locale.ROOT) + " named '" + text + "'"
                    : "more than one " + target.singularLabel().toLowerCase(Locale.ROOT) + " named '" + text + "'"));
        }
    }

    private static LocalDate date(String text) {
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(text, format);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        throw new IllegalArgumentException("'" + text + "' is not a date (use yyyy-MM-dd or dd/MM/yyyy)");
    }

    private static List<String> split(String text) {
        return new ArrayList<>(Arrays.stream(text.split(";")).map(String::trim).filter(s -> !s.isEmpty()).toList());
    }

    /** Header matching: "E-mail Address" ~ "email", "Lead Source" ~ "leadSource". */
    public static String normalize(String header) {
        return header == null ? "" : header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static Map<String, String> suggestMapping(List<String> columns, ModuleDefinition module) {
        Map<String, String> mapping = new java.util.LinkedHashMap<>();
        for (String column : columns) {
            String normalized = normalize(column);
            String match = module.fields().stream()
                    .filter(f -> !f.readOnly() || f.key().equals(ModuleDefinition.OWNER))
                    .filter(f -> normalize(f.key()).equals(normalized) || normalize(f.label()).equals(normalized)
                            || (f.type() == FieldType.USER && f.key().equals(ModuleDefinition.OWNER)
                            && (normalized.equals("owner") || normalized.equals("assignedto"))))
                    .map(FieldDefinition::key)
                    .findFirst().orElse(null);
            mapping.put(column, match);
        }
        return mapping;
    }
}
