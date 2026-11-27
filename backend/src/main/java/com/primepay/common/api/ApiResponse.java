package com.primepay.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.primepay.common.web.RequestIdFilter;
import java.time.Instant;

/**
 * Standard response envelope for every PrimePay API response.
 * Successful responses carry {@code data}; failed responses carry {@code error}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        ApiError error,
        Instant timestamp,
        String requestId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null, Instant.now(), RequestIdFilter.currentRequestId());
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, null, Instant.now(), RequestIdFilter.currentRequestId());
    }

    public static ApiResponse<Void> failure(ApiError error) {
        return new ApiResponse<>(false, null, null, error, Instant.now(), RequestIdFilter.currentRequestId());
    }
}
