package com.orbitcrm.metadata.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body of {@code PATCH /api/v1/picklists/{key}/values/{valueId}}.
 *
 * <p>Purpose: partially update a picklist value; {@code null} fields are left unchanged. System values cannot be
 * deactivated; making a value default clears the flag on the others.
 *
 * @author Abhay
 * @param label     new label (1-120 characters)
 * @param color     new color; blank removes it
 * @param active    activate or deactivate the value
 * @param isDefault make the value the default ({@code true}) or remove the default flag ({@code false})
 */
public record UpdatePicklistValueRequest(@Size(min = 1, max = 120) String label, @Size(max = 20) String color, Boolean active,
                          Boolean isDefault) {
}
