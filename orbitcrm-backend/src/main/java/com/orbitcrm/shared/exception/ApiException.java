package com.orbitcrm.shared.exception;

import java.util.List;

import org.springframework.http.HttpStatus;

import com.orbitcrm.shared.dto.FieldErrorDetail;

/**
 * Base exception for every expected, client-facing failure.
 *
 * <p>Purpose: let services signal business and validation errors with an HTTP status, a stable machine-readable
 * code (for example {@code LEAD_NOT_FOUND}, {@code TENANT_SLUG_TAKEN}), a message that is safe to show to users and
 * optional field-level details. {@link GlobalExceptionHandler} turns it into the standard error envelope. Static
 * factories cover the common cases: {@code badRequest}, {@code validation}, {@code notFound}, {@code conflict},
 * {@code forbidden} and {@code unauthorized}.
 *
 * @author Abhay
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<FieldErrorDetail> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    public ApiException(HttpStatus status, String code, String message, List<FieldErrorDetail> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public List<FieldErrorDetail> details() {
        return details;
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException validation(List<FieldErrorDetail> details) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", details);
    }

    public static ApiException validation(String field, String code, String message) {
        return validation(List.of(new FieldErrorDetail(field, code, message)));
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }
}
