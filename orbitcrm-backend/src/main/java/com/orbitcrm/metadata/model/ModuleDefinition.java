package com.orbitcrm.metadata.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Describes a record module (Leads, Contacts, Companies, Deals, Activities, ...) to the generic record engine.
 *
 * <p>Purpose: one metadata object is all the engine needs to provide CRUD, validation, filtering, search, audit,
 * import/export and metadata endpoints for a module. It declares the table, labels, icon, record number prefix,
 * title and subtitle fields, fields, the picklists the module contributes, the default sort and an optional sharing
 * field. Modules contribute a definition as a Spring bean, built with the fluent {@link Builder}; the builder adds
 * the standard fields every module has ({@code ownerId}, {@code tags}, {@code createdBy}, {@code updatedBy},
 * {@code createdAt}, {@code updatedAt}) and validates title and subtitle fields.
 *
 * <p>Every module table carries the standard columns
 * {@code id, tenant_id, record_number, owner_id, created_by, updated_by, created_at, updated_at, deleted_at,
 * version, tags, custom_fields}.
 *
 * @author Abhay
 * @param key            module key, e.g. {@code leads}; used in URLs, permissions and events
 * @param table          database table
 * @param singularLabel  label for one record
 * @param pluralLabel    label for many records
 * @param recordPrefix   prefix of human-readable record numbers, e.g. {@code LEAD} gives {@code LEAD-1042}
 * @param icon           icon name for the UI
 * @param titleFields    fields joined with spaces to form the record's display title
 * @param subtitleFields fields shown under the title in search results
 * @param fields         all fields, including the standard system fields
 * @param picklists      tenant-editable picklists this module contributes, with their default values
 * @param defaultSort    default list sort, e.g. {@code createdAt,desc}
 * @param sharingField   optional MULTI_USER field whose users can see the record regardless of their OWN/TEAM
 *                       scope (e.g. meeting participants)
 */
public record ModuleDefinition(
        String key,
        String table,
        String singularLabel,
        String pluralLabel,
        String recordPrefix,
        String icon,
        List<String> titleFields,
        List<String> subtitleFields,
        List<FieldDefinition> fields,
        List<PicklistDefinition> picklists,
        String defaultSort,
        String sharingField) {

    public static final String OWNER = "ownerId";
    public static final String CREATED_BY = "createdBy";
    public static final String UPDATED_BY = "updatedBy";
    public static final String CREATED_AT = "createdAt";
    public static final String UPDATED_AT = "updatedAt";
    public static final String TAGS = "tags";

    public static Builder builder(String key) {
        return new Builder(key);
    }

    public Optional<FieldDefinition> field(String fieldKey) {
        return Optional.ofNullable(fieldIndex().get(fieldKey));
    }

    public FieldDefinition requireField(String fieldKey) {
        return field(fieldKey).orElseThrow(() -> new IllegalArgumentException(
                "Unknown field " + fieldKey + " in module " + key));
    }

    public Map<String, FieldDefinition> fieldIndex() {
        return fields.stream().collect(Collectors.toMap(FieldDefinition::key, f -> f, (a, b) -> a,
                LinkedHashMap::new));
    }

    /** Column of the sharing field, or {@code null}. */
    public String sharingColumn() {
        return sharingField == null ? null : requireField(sharingField).column();
    }

    public List<FieldDefinition> searchableFields() {
        return fields.stream().filter(FieldDefinition::searchable).toList();
    }

    /** SQL expression (table alias {@code t}) for the display title; built only from code-defined columns. */
    public String titleSql(String alias) {
        String columns = titleFields.stream()
                .map(f -> alias + "." + requireField(f).column())
                .collect(Collectors.joining(", "));
        return titleFields.size() == 1 ? alias + "." + requireField(titleFields.get(0)).column()
                : "concat_ws(' ', " + columns + ")";
    }

    /**
     * Fluent builder for {@link ModuleDefinition}.
     *
     * <p>Purpose: let modules declare their metadata readably in a Spring {@code @Bean} method. {@link #build()} appends
     * the standard fields every module has (owner, tags, created / modified by and at), requires title fields and
     * verifies that title and subtitle fields exist. Defaults: table named after the key, icon "record", sort by
     * creation date descending.
     */
    public static final class Builder {
        private final String key;
        private String table;
        private String singularLabel;
        private String pluralLabel;
        private String recordPrefix;
        private String icon = "record";
        private List<String> titleFields = List.of();
        private List<String> subtitleFields = List.of();
        private final List<FieldDefinition> fields = new ArrayList<>();
        private final List<PicklistDefinition> picklists = new ArrayList<>();
        private String defaultSort = "createdAt,desc";
        private String sharingField;

        private Builder(String key) {
            this.key = key;
            this.table = key;
        }

        public Builder table(String table) {
            this.table = table;
            return this;
        }

        public Builder labels(String singular, String plural) {
            this.singularLabel = singular;
            this.pluralLabel = plural;
            return this;
        }

        public Builder recordPrefix(String prefix) {
            this.recordPrefix = prefix;
            return this;
        }

        public Builder icon(String icon) {
            this.icon = icon;
            return this;
        }

        public Builder title(String... fieldKeys) {
            this.titleFields = List.of(fieldKeys);
            return this;
        }

        public Builder subtitle(String... fieldKeys) {
            this.subtitleFields = List.of(fieldKeys);
            return this;
        }

        public Builder field(FieldDefinition.Builder field) {
            this.fields.add(field.build());
            return this;
        }

        public Builder picklist(PicklistDefinition picklist) {
            this.picklists.add(picklist);
            return this;
        }

        public Builder defaultSort(String sort) {
            this.defaultSort = sort;
            return this;
        }

        /**
         * Users listed in this MULTI_USER field can see the record even when their scope is OWN/TEAM
         * (e.g. meeting participants).
         */
        public Builder sharedWith(String multiUserFieldKey) {
            this.sharingField = multiUserFieldKey;
            return this;
        }

        public ModuleDefinition build() {
            List<FieldDefinition> all = new ArrayList<>(fields);
            all.add(FieldDefinition.of(OWNER, "Owner", FieldType.USER).required().listDefault()
                    .group("system").build());
            all.add(FieldDefinition.of(TAGS, "Tags", FieldType.TAGS).group("system").build());
            all.add(FieldDefinition.of(CREATED_BY, "Created by", FieldType.USER).readOnly().group("system").build());
            all.add(FieldDefinition.of(UPDATED_BY, "Modified by", FieldType.USER).readOnly().group("system").build());
            all.add(FieldDefinition.of(CREATED_AT, "Created", FieldType.DATETIME).readOnly().group("system").build());
            all.add(FieldDefinition.of(UPDATED_AT, "Modified", FieldType.DATETIME).readOnly().group("system").build());
            if (titleFields.isEmpty()) {
                throw new IllegalStateException("Module " + key + " needs title fields");
            }
            ModuleDefinition definition = new ModuleDefinition(key, table, singularLabel, pluralLabel, recordPrefix,
                    icon, titleFields, subtitleFields, List.copyOf(all), List.copyOf(picklists), defaultSort,
                    sharingField);
            titleFields.forEach(definition::requireField);
            subtitleFields.forEach(definition::requireField);
            return definition;
        }
    }
}
