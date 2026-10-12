package com.orbitcrm.shared.dto;

import java.util.List;
import java.util.function.Function;

/**
 * Standard pagination payload (PRD section 51), returned inside {@link ApiResponse#data()}:
 * {@code {"content": [...], "page": 0, "size": 25, "totalElements": 1524, "totalPages": 61}}.
 *
 * <p>Purpose: give every paged list the same structure so the frontend can render pagination generically. Built
 * from raw values ({@code of}) or a Spring Data {@code Page} ({@code from}); {@code map} converts the items while
 * keeping the paging information.
 *
 * @author Abhay
 * @param content       the items of this page
 * @param page          zero-based page number
 * @param size          requested page size
 * @param totalElements total number of matching items
 * @param totalPages    total number of pages for this size
 * @param <T>           type of the items
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }

    public static <T> PageResponse<T> from(org.springframework.data.domain.Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    public <R> PageResponse<R> map(Function<T, R> mapper) {
        return new PageResponse<>(content.stream().map(mapper).toList(), page, size, totalElements, totalPages);
    }
}
