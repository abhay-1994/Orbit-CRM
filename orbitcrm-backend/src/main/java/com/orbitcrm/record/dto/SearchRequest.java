package com.orbitcrm.record.dto;

import java.util.List;
import java.util.Locale;

import com.orbitcrm.record.model.FilterNode;
import com.orbitcrm.shared.exception.ApiException;

/**
 * A list query against one module: filter, quick-search text, sort and page.
 *
 * <p>Purpose: the common input of list views, saved views, the pipeline board, exports and internal queries. The
 * filter uses the JSON form of the filter language ({@code FilterNode}); {@code q} searches the title and searchable
 * text fields (and matches record numbers such as {@code LEAD-12} exactly). At most three sort fields are allowed;
 * the module's default sort applies when none is given.
 *
 * @author Abhay
 * @param filter filter in the filter language, or {@code null}
 * @param q      quick-search text, or {@code null}
 * @param sort   sort fields and directions, e.g. {@code [{"field":"amount","direction":"desc"}]}
 * @param page   zero-based page number (default 0)
 * @param size   page size (default 25, at most 200)
 */
public record SearchRequest(Object filter, String q, List<Sort> sort, Integer page, Integer size) {

    /**
     * One sort criterion of a list query.
     *
     * <p>Purpose: sort lists by a sortable field in ascending or descending order. {@link #parse} reads the query-string
     * form {@code field,dir} (direction defaults to {@code asc}).
     *
     * @param field     field key to sort by
     * @param direction {@code asc} or {@code desc}
     */
    public record Sort(String field, String direction) {

        public boolean descending() {
            return "desc".equalsIgnoreCase(direction);
        }

        /** Parses {@code "createdAt,desc"}. */
        public static Sort parse(String value) {
            String[] parts = value.split(",");
            String direction = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "asc";
            if (!direction.equals("asc") && !direction.equals("desc")) {
                throw ApiException.validation("sort", "INVALID", "Sort direction must be asc or desc");
            }
            return new Sort(parts[0].trim(), direction);
        }
    }

    public static SearchRequest of(Object filter) {
        return new SearchRequest(filter, null, null, null, null);
    }
}
