package com.orbitcrm.record.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;

/**
 * Request body of {@code POST /api/v1/<module>/bulk-update}.
 *
 * <p>Purpose: apply the same field values to many records at once, e.g. assign a new owner or change a status. Every
 * record is validated and updated separately with the same rules as a single update.
 *
 * @author Abhay
 * @param ids    ids of the records to update (1 to 200 distinct ids)
 * @param values field key to new value
 */
public record BulkUpdateRequest(@NotEmpty List<UUID> ids, @NotEmpty Map<String, Object> values) {
}
