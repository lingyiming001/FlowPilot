package com.flowpilot.common.dto;

public record ApiResponse<T>(boolean success, T data, String code, String message) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, "OK", "");
    }
}
