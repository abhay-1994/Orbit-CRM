package com.orbitcrm.record.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;

/**
 * Request body of {@code POST /api/v1/<module>/bulk-delete}.
 *
 * <p>Purpose: delete (move to the recycle bin) many records in one call. Each record is processed in its own
 * transaction; records the caller cannot delete are reported as failures without affecting the others.
 *
 * @author Abhay
 * @param ids ids of the records to delete (1 to 200 distinct ids)
 */
public record BulkDeleteRequest(@NotEmpty List<UUID> ids) {
}
