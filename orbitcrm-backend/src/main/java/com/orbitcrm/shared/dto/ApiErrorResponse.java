package com.orbitcrm.shared.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard error envelope returned by every failing API call (PRD section 50), for example
 * {@code {"success":false,"error":{"code":"LEAD_NOT_FOUND","message":"Lead does not exist"},"timestamp":...}}.
 *
 * <p>Purpose: give clients one predictable error shape with a stable machine-readable code, a human-readable
 * message, optional field-level details for validation problems, and the request id that correlates the
 * response with server logs. Produced by {@link com.orbitcrm.shared.exception.GlobalExceptionHandler} and by the
 * security layer (401/403) and rate limiter (429).
 *
 * @author Abhay
 * @param success   always {@code false}
 * @param error     code, message and optional field-level details
 * @param timestamp when the error was produced (UTC)
 * @param requestId correlation id of the request (also returned in the {@code X-Request-Id} header)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(boolean success, Error error, Instant timestamp, String requestId) {

    /**
     * The {@code error} object inside {@link ApiErrorResponse}; empty details are omitted from the JSON.
     *
     * <p>Purpose: describe what went wrong in a stable, machine-readable and human-readable way.
     *
     * @param code    stable error code, e.g. {@code VALIDATION_FAILED}, {@code LEAD_NOT_FOUND}
     * @param message human-readable message, safe to show to users
     * @param details field-level problems, for validation errors
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Error(String code, String message, List<FieldErrorDetail> details) {
    }

    public static ApiErrorResponse of(String code, String message, List<FieldErrorDetail> details, String requestId) {
        return new ApiErrorResponse(false, new Error(code, message, details), Instant.now(), requestId);
    }
}
