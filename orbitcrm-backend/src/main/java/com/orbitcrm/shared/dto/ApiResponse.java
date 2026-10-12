package com.orbitcrm.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard success envelope for every API response (PRD section 50):
 * {@code {"success": true, "data": ..., "message": "Lead created successfully"}}.
 *
 * <p>Purpose: keep all successful responses uniform so the frontend can handle them generically. The payload goes
 * in {@code data} (a single object, a list or a {@link PageResponse}); {@code message} is an optional,
 * user-facing confirmation and is omitted when {@code null}. Created through the {@code ok(...)} and
 * {@code message(...)} factory methods by every controller.
 *
 * @author Abhay
 * @param success always {@code true}
 * @param data    the response payload, or {@code null} for message-only responses
 * @param message optional confirmation text for the user
 * @param <T>     type of the payload
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, String message) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message);
    }

    public static ApiResponse<Void> message(String message) {
        return new ApiResponse<>(true, null, message);
    }
}
