package com.orbitcrm.dataio.dto;

import java.util.List;

import com.orbitcrm.record.dto.SearchRequest;

/**
 * Optional request body of {@code POST /api/v1/{module}/export}.
 *
 * <p>Purpose: select which records and fields to export - typically the same filter and sort as the list view the
 * user is looking at.
 *
 * @author Abhay
 * @param filter filter in the filter language, or {@code null} for all visible records
 * @param q      quick-search text, or {@code null}
 * @param sort   sort fields and directions
 * @param fields field keys to include as columns; {@code null} or empty for all business fields
 */
public record ExportRequest(Object filter, String q, List<SearchRequest.Sort> sort, List<String> fields) {
}
