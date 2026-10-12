package com.orbitcrm.record.dto;

import java.util.UUID;

/**
 * One record that could not be processed in a bulk operation.
 *
 * <p>Purpose: tell the client exactly which records failed and why, so it can show them to the user or retry.
 *
 * @author Abhay
 * @param id      id of the failed record
 * @param code    error code, e.g. {@code ACCESS_DENIED}, {@code VALIDATION_FAILED}, {@code LEAD_NOT_FOUND}
 * @param message human-readable reason
 */
public record BulkFailure(UUID id, String code, String message) {
}
