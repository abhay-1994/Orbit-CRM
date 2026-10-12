package com.orbitcrm.shared.exception;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.orbitcrm.shared.dto.ApiErrorResponse;
import com.orbitcrm.shared.dto.FieldErrorDetail;
import com.orbitcrm.shared.web.RequestIdFilter;

/**
 * Central translation of exceptions into the standard error envelope ({@code @RestControllerAdvice}).
 *
 * <p>Purpose: guarantee that every error response has the same shape and never leaks internals. It maps:
 * <ul>
 *   <li>{@link ApiException} to its own status and code;</li>
 *   <li>bean validation failures to {@code 400 VALIDATION_FAILED} with field details;</li>
 *   <li>unreadable JSON, type mismatches and missing parameters to {@code 400};</li>
 *   <li>unsupported method / media type and unknown paths to {@code 405}, {@code 415} and {@code 404};</li>
 *   <li>oversized uploads to {@code 413}, access denied to {@code 403}, missing authentication to {@code 401};</li>
 *   <li>optimistic locking failures to {@code 409 CONCURRENT_MODIFICATION} and constraint violations to
 *   {@code 409 DATA_CONFLICT};</li>
 *   <li>a client that disconnected mid-response (for example a closed live stream) to nothing at all;</li>
 *   <li>anything unexpected to {@code 500 INTERNAL_ERROR} with a generic message; the stack trace is logged
 *   together with the request id.</li>
 * </ul>
 *
 * @author Abhay
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorResponse> handleApi(ApiException ex) {
        if (ex.status().is5xxServerError()) {
            log.error("API error {}: {}", ex.code(), ex.getMessage(), ex);
        }
        return build(ex.status(), ex.code(), ex.getMessage(), ex.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorDetail> details = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            details.add(new FieldErrorDetail(fe.getField(), codeOf(fe.getCode()), fe.getDefaultMessage()));
        }
        ex.getBindingResult().getGlobalErrors().forEach(ge ->
                details.add(new FieldErrorDetail(ge.getObjectName(), codeOf(ge.getCode()), ge.getDefaultMessage())));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        List<FieldErrorDetail> details = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(err ->
                    details.add(new FieldErrorDetail(name, "INVALID", err.getDefaultMessage())));
        });
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorDetail> details = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorDetail(v.getPropertyPath().toString(), "INVALID", v.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is missing or not valid JSON", null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Parameter '" + ex.getName() + "' has an invalid value", null);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingRequestHeaderException.class,
            MissingServletRequestPartException.class})
    public ResponseEntity<ApiErrorResponse> handleMissing(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", ex.getMessage(), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage(), null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", ex.getMessage(), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiErrorResponse> handleUploadSize(MaxUploadSizeExceededException ex) {
        return build(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", "Uploaded file exceeds the maximum size", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access is denied", null);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required", null);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "The record was modified by someone else. Reload and try again.", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "The request conflicts with existing data (duplicate or invalid reference)", null);
    }

    /** The client went away mid-response (typically a closed live stream): nothing can or should be written. */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    void handleClientGone(AsyncRequestNotUsableException ex) {
        log.debug("Client disconnected: {}", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Quote the request id when contacting support.", null);
    }

    private static String codeOf(String constraintCode) {
        if (constraintCode == null) {
            return "INVALID";
        }
        return switch (constraintCode) {
            case "NotNull", "NotBlank", "NotEmpty" -> "REQUIRED";
            case "Email" -> "INVALID_EMAIL";
            case "Size", "Length" -> "INVALID_LENGTH";
            case "Pattern" -> "INVALID_FORMAT";
            default -> "INVALID";
        };
    }

    public static ResponseEntity<ApiErrorResponse> build(HttpStatus status, String code, String message,
                                                   List<FieldErrorDetail> details) {
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(code, message, details, RequestIdFilter.currentRequestId()));
    }
}
