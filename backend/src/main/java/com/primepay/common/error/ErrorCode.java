package com.primepay.common.error;

import org.springframework.http.HttpStatus;

/** Stable error codes returned to clients. Add new codes; never rename existing ones. */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Some fields are missing or invalid."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request body could not be read."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Sign in to continue."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid username or password."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "You don't have permission to do this."),
    OUTSIDE_HIERARCHY(HttpStatus.FORBIDDEN, "This account is outside your network."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "The requested record was not found."),
    DUPLICATE(HttpStatus.CONFLICT, "A record with these details already exists."),
    INVALID_STATE(HttpStatus.CONFLICT, "This action isn't allowed in the record's current state."),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "This idempotency key was already used for a different request."),
    ACCOUNT_INACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "The account is not active."),
    INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_ENTITY, "Available balance is not enough."),
    LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "This exceeds a transaction limit."),
    BUSINESS_RULE(HttpStatus.UNPROCESSABLE_ENTITY, "The request breaks a business rule."),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "The account is temporarily locked."),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "The uploaded file is too large."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Try again shortly."),
    OTP_DELIVERY_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "The one-time code could not be sent. Try again in a moment."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. No changes were made.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    /** Maps a bare HTTP status (for example from {@code ResponseStatusException}) to the closest code. */
    public static ErrorCode fromStatus(int status) {
        return switch (status) {
            case 400 -> VALIDATION_FAILED;
            case 401 -> UNAUTHENTICATED;
            case 403 -> ACCESS_DENIED;
            case 404 -> NOT_FOUND;
            case 409 -> DUPLICATE;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 422 -> BUSINESS_RULE;
            case 423 -> ACCOUNT_LOCKED;
            case 429 -> RATE_LIMITED;
            default -> INTERNAL_ERROR;
        };
    }
}
