package com.primepay.common.error;

/** Thrown by services for expected, client-facing failures. The message must be safe to show to users. */
public class BusinessException extends RuntimeException {
    private final ErrorCode code;

    public BusinessException(ErrorCode code) {
        this(code, code.defaultMessage());
    }

    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
