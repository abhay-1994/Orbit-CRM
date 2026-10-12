package com.orbitcrm.search.dto;

import java.util.List;

/**
 * Response body of {@code GET /api/v1/search}.
 *
 * <p>Purpose: the complete global search result, grouped by module in navigation order.
 *
 * @author Abhay
 * @param query  the (trimmed) search text
 * @param total  total number of visible matches across all modules
 * @param groups per-module results; modules without matches are omitted
 */
public record SearchResult(String query, long total, List<SearchGroup> groups) {
}
