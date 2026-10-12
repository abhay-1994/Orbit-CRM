package com.orbitcrm.dataio.dto;

import java.util.List;

/**
 * Response body of the mapping step: the updated job and a validated preview.
 *
 * <p>Purpose: show users what the first rows will look like and which problems they have before anything is written.
 *
 * @author Abhay
 * @param job  the import job with the saved mapping and options
 * @param rows the first (up to 10) rows converted and validated
 */
public record ImportPreview(ImportJob job, List<ImportPreviewRow> rows) {
}
