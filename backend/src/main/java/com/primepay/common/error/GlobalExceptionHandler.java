package com.primepay.common.error;

import com.primepay.common.api.ApiError;
import com.primepay.common.api.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Converts every exception thrown by a controller into the standard error envelope.
 * Messages for unexpected errors never include internal details; those go to the log with the request ID.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return respond(ex.code(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleInvalidBody(MethodArgumentNotValidException ex) {
        List<ApiError.FieldMessage> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldMessage(error.getField(), error.getDefaultMessage()))
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        List<ApiError.FieldMessage> fields = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldMessage(lastPathNode(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), fields);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiResponse<Void>> handleBadParameter(Exception ex) {
        return respond(ErrorCode.VALIDATION_FAILED, ex.getMessage(), List.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        return respond(ErrorCode.MALFORMED_REQUEST, ErrorCode.MALFORMED_REQUEST.defaultMessage(), List.of());
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    ResponseEntity<ApiResponse<Void>> handleAccessDenied(RuntimeException ex) {
        String message = ex.getMessage() == null || ex.getMessage().isBlank() || "Access Denied".equals(ex.getMessage())
                ? ErrorCode.ACCESS_DENIED.defaultMessage()
                : ex.getMessage();
        return respond(ErrorCode.ACCESS_DENIED, message, List.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException ex) {
        ErrorCode code = ErrorCode.fromStatus(ex.getStatusCode().value());
        String message = ex.getReason() != null && ex.getStatusCode().is4xxClientError()
                ? ex.getReason()
                : code.defaultMessage();
        return ResponseEntity.status(ex.getStatusCode())
                .body(ApiResponse.failure(new ApiError(code.name(), message, List.of())));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return respond(ErrorCode.DUPLICATE, ErrorCode.DUPLICATE.defaultMessage(), List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Void>> handleUploadSize(MaxUploadSizeExceededException ex) {
        return respond(ErrorCode.PAYLOAD_TOO_LARGE, ErrorCode.PAYLOAD_TOO_LARGE.defaultMessage(), List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return respond(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage(), List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(405)
                .body(ApiResponse.failure(ApiError.of("METHOD_NOT_ALLOWED", ex.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled error", ex);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), List.of());
    }

    private static ResponseEntity<ApiResponse<Void>> respond(ErrorCode code, String message, List<ApiError.FieldMessage> fields) {
        return ResponseEntity.status(code.status())
                .body(ApiResponse.failure(new ApiError(code.name(), message, fields)));
    }

    private static String lastPathNode(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }
}
