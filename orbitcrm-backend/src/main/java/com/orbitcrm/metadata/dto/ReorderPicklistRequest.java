package com.orbitcrm.metadata.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;

/**
 * Request body of {@code PUT /api/v1/picklists/{key}/order}.
 *
 * <p>Purpose: set the display order of a picklist in one call. The list must contain every value id of the picklist
 * exactly once.
 *
 * @author Abhay
 * @param valueIds all value ids of the picklist in the desired order
 */
public record ReorderPicklistRequest(@NotEmpty List<UUID> valueIds) {
}
