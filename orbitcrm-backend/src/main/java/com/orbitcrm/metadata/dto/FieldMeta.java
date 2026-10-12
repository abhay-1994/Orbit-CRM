package com.orbitcrm.metadata.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.orbitcrm.metadata.model.FieldType;
import com.orbitcrm.metadata.model.FilterOperator;

/**
 * Metadata of one field, returned inside {@link ModuleDetail} by {@code GET /api/v1/metadata/modules/{key}};
 * {@code null} values are omitted from the JSON.
 *
 * <p>Purpose: everything a generic UI needs to render, validate, filter and sort a field.
 *
 * @author Abhay
 * @param key               field key used in API payloads and filters, e.g. {@code expectedCloseDate}
 * @param label             human-readable label
 * @param type              field type, which determines the input widget and the stored value format
 * @param required          whether a value is mandatory
 * @param readOnly          whether the field is maintained by the system and ignored in input
 * @param maxLength         maximum text length, if limited
 * @param min               minimum numeric value, if limited
 * @param max               maximum numeric value, if limited
 * @param group             form section the field belongs to (e.g. {@code details}, {@code address}, {@code system})
 * @param helpText          optional explanation for users
 * @param searchable        whether the field is included in quick and global search
 * @param filterable        whether the field can be used in filters
 * @param sortable          whether lists can be sorted by the field
 * @param listDefault       whether the field is a default list column
 * @param lookupModule      target module of a lookup field
 * @param lookupModuleField for polymorphic lookups: the field that holds the target module key
 * @param referenceType     for reference fields: the kind of configuration entity (e.g. {@code pipeline}, {@code stage})
 * @param picklist          key of the tenant picklist backing the field
 * @param options           selectable options (picklist values, fixed options or reference options)
 * @param operators         filter operators supported by the field (empty when not filterable)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldMeta(String key, String label, FieldType type, boolean required, boolean readOnly, Integer maxLength,
                 BigDecimal min, BigDecimal max, String group, String helpText, boolean searchable,
                 boolean filterable, boolean sortable, boolean listDefault, String lookupModule,
                 String lookupModuleField, String referenceType, String picklist, List<FieldOption> options,
                 Set<FilterOperator> operators) {
}
