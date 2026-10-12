package com.orbitcrm.metadata.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import com.orbitcrm.metadata.service.ReferenceResolver;

/**
 * Describes one field of a record module: storage column, type, validation rules and UI hints.
 *
 * <p>Purpose: the central metadata unit of the generic record engine. The engine uses it to map API values to
 * columns, validate and convert input, build filters and sorting, maintain the search index, write audit diffs and
 * publish field metadata to the frontend. Built-in fields are defined in code through the fluent {@link Builder}
 * (e.g. {@code FieldDefinition.of("email", "Email", FieldType.EMAIL).searchable().listDefault()}); tenant-defined
 * custom fields will produce the same structure (V3). The builder applies defaults (snake_case column, default
 * maximum lengths, sortability by type) and validates the definition, e.g. a picklist field needs a picklist or
 * fixed options and a lookup needs a target module.
 *
 * @author Abhay
 * @param key               field key used in the API, e.g. {@code expectedCloseDate}
 * @param column            database column; derived from the key in snake_case unless overridden
 * @param label             human-readable label
 * @param type              field type
 * @param required          whether a value is mandatory on create and cannot be cleared
 * @param readOnly          maintained by the system (timestamps, derived status); ignored in API input
 * @param maxLength         maximum text length (defaults per type, e.g. 255 for TEXT)
 * @param min               minimum numeric value
 * @param max               maximum numeric value
 * @param picklist          key of a tenant-editable picklist (PICKLIST / MULTI_PICKLIST)
 * @param fixedOptions      code-defined options that tenants cannot edit (e.g. which modules an activity can relate to)
 * @param lookupModule      target module of a LOOKUP field
 * @param lookupModuleField for polymorphic lookups: the field holding the target module key
 * @param referenceType     for REFERENCE fields: the {@link com.orbitcrm.metadata.service.ReferenceResolver} type (e.g. "pipeline")
 * @param searchable        whether the value is indexed for search
 * @param filterable        whether the field may be used in filters
 * @param sortable          whether lists may be sorted by the field
 * @param listDefault       whether the field is a default list column
 * @param sensitive         value is masked in audit logs
 * @param group             form section the field belongs to
 * @param helpText          optional explanation for users
 */
public record FieldDefinition(
        String key,
        String column,
        String label,
        FieldType type,
        boolean required,
        boolean readOnly,
        Integer maxLength,
        BigDecimal min,
        BigDecimal max,
        String picklist,
        List<PicklistOption> fixedOptions,
        String lookupModule,
        String lookupModuleField,
        String referenceType,
        boolean searchable,
        boolean filterable,
        boolean sortable,
        boolean listDefault,
        boolean sensitive,
        String group,
        String helpText) {

    public static Builder of(String key, String label, FieldType type) {
        return new Builder(key, label, type);
    }

    public boolean hasPicklist() {
        return picklist != null || (fixedOptions != null && !fixedOptions.isEmpty());
    }

    public boolean isPolymorphicLookup() {
        return type == FieldType.LOOKUP && lookupModuleField != null;
    }

    /**
     * Fluent builder for {@link FieldDefinition}.
     *
     * <p>Purpose: make field definitions short and readable in module definitions, e.g.
     * {@code FieldDefinition.of("amount", "Amount", FieldType.CURRENCY).min(0).listDefault()}. It applies defaults -
     * filterable, sortable except for text areas, multi-value and reference fields, group "details", snake_case column,
     * type-specific maximum lengths - and
     * {@link #build()} validates the definition (picklist fields need options, lookups a target module, reference fields a
     * reference type).
     */
    public static final class Builder {
        private final String key;
        private final String label;
        private final FieldType type;
        private String column;
        private boolean required;
        private boolean readOnly;
        private Integer maxLength;
        private BigDecimal min;
        private BigDecimal max;
        private String picklist;
        private List<PicklistOption> fixedOptions = List.of();
        private String lookupModule;
        private String lookupModuleField;
        private String referenceType;
        private boolean searchable;
        private boolean filterable = true;
        private boolean sortable;
        private boolean listDefault;
        private boolean sensitive;
        private String group = "details";
        private String helpText;

        private Builder(String key, String label, FieldType type) {
            this.key = key;
            this.label = label;
            this.type = type;
            this.sortable = !type.isArray() && type != FieldType.TEXTAREA && !type.isReference();
        }

        public Builder column(String column) {
            this.column = column;
            return this;
        }

        public Builder required() {
            this.required = true;
            return this;
        }

        public Builder readOnly() {
            this.readOnly = true;
            return this;
        }

        public Builder maxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public Builder range(long min, long max) {
            this.min = BigDecimal.valueOf(min);
            this.max = BigDecimal.valueOf(max);
            return this;
        }

        public Builder min(long min) {
            this.min = BigDecimal.valueOf(min);
            return this;
        }

        public Builder picklist(String picklistKey) {
            this.picklist = picklistKey;
            return this;
        }

        public Builder options(List<PicklistOption> options) {
            this.fixedOptions = List.copyOf(options);
            return this;
        }

        public Builder lookup(String module) {
            this.lookupModule = module;
            return this;
        }

        public Builder polymorphicLookup(String moduleField) {
            this.lookupModuleField = moduleField;
            return this;
        }

        public Builder reference(String referenceType) {
            this.referenceType = referenceType;
            return this;
        }

        public Builder searchable() {
            this.searchable = true;
            return this;
        }

        public Builder notFilterable() {
            this.filterable = false;
            return this;
        }

        public Builder sortable(boolean sortable) {
            this.sortable = sortable;
            return this;
        }

        public Builder listDefault() {
            this.listDefault = true;
            return this;
        }

        public Builder sensitive() {
            this.sensitive = true;
            return this;
        }

        public Builder group(String group) {
            this.group = group;
            return this;
        }

        public Builder help(String helpText) {
            this.helpText = helpText;
            return this;
        }

        public FieldDefinition build() {
            if ((type == FieldType.PICKLIST || type == FieldType.MULTI_PICKLIST)
                    && picklist == null && fixedOptions.isEmpty()) {
                throw new IllegalStateException("Picklist field " + key + " needs a picklist or fixed options");
            }
            if (type == FieldType.LOOKUP && lookupModule == null && lookupModuleField == null) {
                throw new IllegalStateException("Lookup field " + key + " needs a target module");
            }
            Integer length = maxLength != null ? maxLength : defaultMaxLength(type);
            if (type == FieldType.REFERENCE && referenceType == null) {
                throw new IllegalStateException("Reference field " + key + " needs a reference type");
            }
            return new FieldDefinition(key, column != null ? column : snakeCase(key), label, type, required,
                    readOnly, length, min, max, picklist, fixedOptions, lookupModule, lookupModuleField, referenceType,
                    searchable,
                    filterable, sortable, listDefault, sensitive, group, helpText);
        }

        private static Integer defaultMaxLength(FieldType type) {
            return switch (type) {
                case TEXT -> 255;
                case TEXTAREA -> 32_000;
                case EMAIL -> 254;
                case PHONE -> 40;
                case URL -> 500;
                default -> null;
            };
        }
    }

    public static String snakeCase(String camel) {
        StringBuilder out = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) {
                out.append('_').append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }
}
