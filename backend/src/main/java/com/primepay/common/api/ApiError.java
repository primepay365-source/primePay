package com.primepay.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Machine-readable error code, a safe human message, and optional per-field validation messages. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String code, String message, List<FieldMessage> fields) {

    public record FieldMessage(String field, String message) {
    }

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, List.of());
    }
}
