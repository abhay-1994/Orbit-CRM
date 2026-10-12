package com.orbitcrm.dataio.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The state of a CSV import, stored in {@code import_jobs} and returned by the import endpoints.
 *
 * <p>Purpose: track an import through its lifecycle - {@code UPLOADED}, {@code MAPPED}, {@code QUEUED},
 * {@code RUNNING}, then {@code COMPLETED} or {@code FAILED} - and report its progress and results. The storage key
 * of the uploaded file is internal and never serialized.
 *
 * @author Abhay
 * @param id            import id
 * @param module        target module
 * @param fileName      name of the uploaded file
 * @param storageKey    object storage key of the uploaded file (not exposed in JSON)
 * @param status        lifecycle status
 * @param columns       column names detected in the header row
 * @param totalRows     number of data rows in the file
 * @param processedRows rows processed so far
 * @param created       records created
 * @param updated       existing records updated
 * @param skipped       rows skipped as duplicates
 * @param failed        rows that failed
 * @param mapping       CSV column to field key
 * @param options       duplicate field, duplicate strategy and default values
 * @param errors        per-row errors (at most 500 are stored)
 * @param createdBy     user who uploaded the file
 * @param createdAt     upload time
 * @param startedAt     when processing started
 * @param finishedAt    when processing finished
 */
public record ImportJob(UUID id, String module, String fileName, @com.fasterxml.jackson.annotation.JsonIgnore String storageKey,
                 String status, List<String> columns, int totalRows, int processedRows, int created, int updated,
                 int skipped, int failed, Map<String, String> mapping, Map<String, Object> options,
                 List<ImportRowError> errors, UUID createdBy, Instant createdAt, Instant startedAt, Instant finishedAt) {
}
