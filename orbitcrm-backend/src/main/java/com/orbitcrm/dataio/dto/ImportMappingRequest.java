package com.orbitcrm.dataio.dto;

import java.util.Map;

/**
 * Request body of {@code PUT /api/v1/imports/{id}/mapping}.
 *
 * <p>Purpose: configure an uploaded import before it starts. Only existing, writable fields can be mapped and at least
 * one column must be mapped; the import can be reconfigured until it is started.
 *
 * @author Abhay
 * @param mapping           CSV column to field key; {@code null} or blank values leave a column unmapped
 * @param duplicateField    field used to detect existing records (e.g. {@code email}), or {@code null} for no check
 * @param duplicateStrategy what to do with duplicates (default {@code SKIP})
 * @param defaults          field values applied to every row unless the row provides its own (e.g. a lead source)
 */
public record ImportMappingRequest(Map<String, String> mapping, String duplicateField, DuplicateStrategy duplicateStrategy,
                      Map<String, Object> defaults) {
}
