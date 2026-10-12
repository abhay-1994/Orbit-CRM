package com.orbitcrm.view.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body of {@code PATCH /api/v1/views/{id}}.
 *
 * <p>Purpose: partially update a saved view; {@code null} fields are left unchanged. The resulting filter and sort are
 * validated against the module before saving.
 *
 * @author Abhay
 * @param name       new name (1-120 characters)
 * @param visibility {@code PRIVATE} or {@code SHARED} (shared requires the settings permission)
 * @param filter     new filter in the filter language
 * @param sort       new sort, e.g. {@code [{"field":"amount","direction":"desc"}]}
 * @param columns    new column configuration (stored as given for the frontend)
 */
public record UpdateViewRequest(@Size(min = 1, max = 120) String name, String visibility, Object filter, Object sort,
                         Object columns) {
}
