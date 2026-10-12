package com.orbitcrm.shared.dto;

import com.orbitcrm.shared.exception.ApiException;

/**
 * Validated pagination parameters for list endpoints.
 *
 * <p>Purpose: guarantee that no endpoint ever returns thousands of records by default (PRD section 51). Page numbers
 * are zero-based; the size defaults to 25 and may not exceed 200. Invalid values raise {@code INVALID_PAGE} or
 * {@code INVALID_PAGE_SIZE}. {@link #offset()} converts the page into a SQL offset.
 *
 * @author Abhay
 * @param page zero-based page number
 * @param size number of items per page (1 to 200)
 */
public record PageParams(int page, int size) {

    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 200;

    public PageParams {
        if (page < 0) {
            throw ApiException.badRequest("INVALID_PAGE", "page must be >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw ApiException.badRequest("INVALID_PAGE_SIZE", "size must be between 1 and " + MAX_SIZE);
        }
    }

    public static PageParams of(Integer page, Integer size) {
        return new PageParams(page == null ? 0 : page, size == null ? DEFAULT_SIZE : size);
    }

    public long offset() {
        return (long) page * size;
    }
}
