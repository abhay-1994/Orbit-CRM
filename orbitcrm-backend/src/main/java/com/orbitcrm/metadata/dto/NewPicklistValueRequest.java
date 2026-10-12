package com.orbitcrm.metadata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body of {@code POST /api/v1/picklists/{key}/values}.
 *
 * <p>Purpose: add a value to a tenant picklist. The stored key is derived from the label (lower-case ASCII slug,
 * made unique); labels must be unique within the picklist (case-insensitive).
 *
 * @author Abhay
 * @param label     the text users see (max 120 characters)
 * @param color     optional display color (max 20 characters, e.g. {@code #22c55e})
 * @param isDefault whether the new value becomes the picklist's default
 */
public record NewPicklistValueRequest(@NotBlank @Size(max = 120) String label, @Size(max = 20) String color, Boolean isDefault) {
}
