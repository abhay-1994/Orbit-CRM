package com.orbitcrm.record.mapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.orbitcrm.identity.service.UserDirectory;
import com.orbitcrm.metadata.model.FieldDefinition;
import com.orbitcrm.metadata.model.FieldType;
import com.orbitcrm.metadata.model.ModuleDefinition;
import com.orbitcrm.metadata.model.PicklistOption;
import com.orbitcrm.metadata.service.ModuleRegistry;
import com.orbitcrm.metadata.service.PicklistService;
import com.orbitcrm.metadata.service.ReferenceResolvers;
import com.orbitcrm.record.dto.RecordView;
import com.orbitcrm.record.repository.RecordRepository;
import com.orbitcrm.search.dto.IndexDocument;

/**
 * Turns raw database rows into {@link com.orbitcrm.record.dto.RecordView}s and search index documents.
 *
 * <p>Purpose: resolve everything a client needs to display records without N+1 queries. For a page of rows it
 * collects all referenced user ids, lookup ids (per target module, including polymorphic lookups) and reference ids,
 * resolves their names in one batch each, and adds picklist labels. It also builds the display title and the
 * human-readable record number (e.g. {@code LEAD-1042}), and produces the {@code IndexDocument} for the global search
 * index (title, record number, searchable values and their labels, phone numbers also as plain digits, and a
 * subtitle).
 *
 * @author Abhay
 */
@Component
public class RecordPresenter {

    private final ModuleRegistry registry;
    private final RecordRepository repository;
    private final UserDirectory users;
    private final PicklistService picklists;
    private final ReferenceResolvers references;

    RecordPresenter(ModuleRegistry registry, RecordRepository repository, UserDirectory users,
                    PicklistService picklists, ReferenceResolvers references) {
        this.registry = registry;
        this.repository = repository;
        this.users = users;
        this.picklists = picklists;
        this.references = references;
    }

    public RecordView present(ModuleDefinition module, RecordRepository.Row row) {
        return present(module, List.of(row)).get(0);
    }

    public List<RecordView> present(ModuleDefinition module, List<RecordRepository.Row> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<UUID> userIds = new HashSet<>();
        Map<String, Set<UUID>> lookupIds = new HashMap<>();
        Map<String, Set<UUID>> referenceIds = new HashMap<>();
        for (RecordRepository.Row row : rows) {
            for (FieldDefinition field : module.fields()) {
                Object value = row.values().get(field.key());
                if (value == null) {
                    continue;
                }
                switch (field.type()) {
                    case USER -> userIds.add((UUID) value);
                    case MULTI_USER -> ((List<?>) value).forEach(v -> userIds.add((UUID) v));
                    case LOOKUP -> {
                        String target = targetModule(field, row.values());
                        if (target != null && registry.find(target).isPresent()) {
                            lookupIds.computeIfAbsent(target, k -> new HashSet<>()).add((UUID) value);
                        }
                    }
                    case REFERENCE -> referenceIds.computeIfAbsent(field.referenceType(), k -> new HashSet<>())
                            .add((UUID) value);
                    default -> {
                    }
                }
            }
        }
        Map<UUID, String> userNames = users.displayNames(userIds);
        Map<String, Map<UUID, String>> lookupTitles = new HashMap<>();
        lookupIds.forEach((target, ids) -> lookupTitles.put(target, repository.titles(registry.require(target), ids)));
        referenceIds.forEach((type, ids) -> lookupTitles.put("ref:" + type, references.require(type).labels(ids)));

        List<RecordView> views = new ArrayList<>(rows.size());
        for (RecordRepository.Row row : rows) {
            Map<String, String> labels = new LinkedHashMap<>();
            for (FieldDefinition field : module.fields()) {
                String label = label(field, row.values(), userNames, lookupTitles);
                if (label != null) {
                    labels.put(field.key(), label);
                }
            }
            views.add(new RecordView(row.id(), module.key(), recordNumber(module, row.recordNumber()), row.version(),
                    title(module, row.values()), row.values(), labels));
        }
        return views;
    }

    public IndexDocument indexDocument(ModuleDefinition module, RecordView view) {
        List<String> content = new ArrayList<>();
        content.add(view.title());
        content.add(view.recordNumber());
        for (FieldDefinition field : module.searchableFields()) {
            Object value = view.values().get(field.key());
            if (value == null) {
                continue;
            }
            String label = view.labels().get(field.key());
            content.add(label != null ? label : value.toString());
            if (field.type() == FieldType.PHONE) {
                content.add(value.toString().replaceAll("\\D", ""));
            }
        }
        String subtitle = module.subtitleFields().stream()
                .map(f -> {
                    String label = view.labels().get(f);
                    Object value = view.values().get(f);
                    return label != null ? label : value == null ? null : value.toString();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" · "));
        return new IndexDocument(module.key(), view.id(), view.uuid(ModuleDefinition.OWNER), view.recordNumber(),
                view.title(), subtitle.isBlank() ? null : subtitle,
                content.stream().filter(Objects::nonNull).collect(Collectors.joining(" ")));
    }

    public static String title(ModuleDefinition module, Map<String, Object> values) {
        String title = module.titleFields().stream()
                .map(values::get)
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.joining(" "))
                .trim();
        return title.isEmpty() ? "(untitled)" : title;
    }

    public static String recordNumber(ModuleDefinition module, long number) {
        return module.recordPrefix() == null ? String.valueOf(number) : module.recordPrefix() + "-" + number;
    }

    public static String targetModule(FieldDefinition field, Map<String, Object> values) {
        if (field.lookupModuleField() != null) {
            Object target = values.get(field.lookupModuleField());
            return target == null ? null : target.toString();
        }
        return field.lookupModule();
    }

    private String label(FieldDefinition field, Map<String, Object> values, Map<UUID, String> userNames,
                         Map<String, Map<UUID, String>> lookupTitles) {
        Object value = values.get(field.key());
        if (value == null) {
            return null;
        }
        return switch (field.type()) {
            case USER -> userNames.get((UUID) value);
            case MULTI_USER -> ((List<?>) value).stream().map(v -> userNames.get((UUID) v)).filter(Objects::nonNull)
                    .collect(Collectors.joining(", "));
            case LOOKUP -> {
                String target = targetModule(field, values);
                yield target == null ? null : lookupTitles.getOrDefault(target, Map.of()).get((UUID) value);
            }
            case REFERENCE -> lookupTitles.getOrDefault("ref:" + field.referenceType(), Map.of()).get((UUID) value);
            case PICKLIST -> optionLabel(field, (String) value);
            case MULTI_PICKLIST -> ((List<?>) value).stream().map(v -> optionLabel(field, (String) v))
                    .collect(Collectors.joining(", "));
            default -> null;
        };
    }

    private String optionLabel(FieldDefinition field, String key) {
        if (field.picklist() != null) {
            return picklists.label(field.picklist(), key);
        }
        return field.fixedOptions().stream().filter(o -> o.key().equals(key)).map(PicklistOption::label)
                .findFirst().orElse(key);
    }
}
