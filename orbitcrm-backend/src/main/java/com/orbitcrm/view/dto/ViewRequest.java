package com.orbitcrm.view.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body of {@code POST /api/v1/views}.
 *
 * <p>Purpose: save a new list view for a module. The filter and sort are validated by running them once through the
 * record engine; filters may use dynamic values such as {@code $me} or {@code $startOfMonth} so one shared view works
 * for every user.
 *
 * @author Abhay
 * @param module     module key the view belongs to
 * @param name       view name (max 120 characters)
 * @param visibility {@code PRIVATE} (default) or {@code SHARED}
 * @param filter     filter in the filter language
 * @param sort       sort definition
 * @param columns    column configuration (stored as given for the frontend)
 */
public record ViewRequest(@NotBlank String module, @NotBlank @Size(max = 120) String name, String visibility,
                   Object filter, Object sort, Object columns) {
}
