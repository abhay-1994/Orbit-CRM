package com.orbitcrm.shared.dto;

/**
 * One field-level validation problem inside {@link ApiErrorResponse}, e.g.
 * {@code {"field":"email","code":"INVALID_EMAIL","message":"Email must be a valid email address"}}.
 *
 * <p>Purpose: let forms highlight exactly which input failed and why. Produced by bean validation, by the record
 * engine's metadata-driven validation, by the filter compiler and by import mapping.
 *
 * @author Abhay
 * @param field   name or dotted path of the offending input (for example {@code email}, {@code filter.status})
 * @param code    stable machine-readable reason such as {@code REQUIRED}, {@code INVALID_OPTION}, {@code UNKNOWN_FIELD}
 * @param message human-readable explanation, safe to show to users
 */
public record FieldErrorDetail(String field, String code, String message) {
}
