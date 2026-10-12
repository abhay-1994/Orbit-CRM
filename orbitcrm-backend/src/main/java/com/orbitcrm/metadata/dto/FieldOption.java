package com.orbitcrm.metadata.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One selectable option of a field, part of {@link FieldMeta#options()}.
 *
 * <p>Purpose: describe picklist values, code-defined fixed options and reference options (pipelines, stages) in one
 * shape for dropdowns and filter chips.
 *
 * @author Abhay
 * @param key       the value stored in records (for references: the entity id)
 * @param label     the text shown to users
 * @param color     optional display color
 * @param active    whether the option may be chosen for new values (inactive values still display on old records)
 * @param isDefault whether the option is preselected for new records
 * @param group     optional grouping, e.g. the pipeline a stage belongs to
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldOption(String key, String label, String color, boolean active, boolean isDefault, String group) {
}
