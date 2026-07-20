package com.yhk.aistudyplanner.common.response;

public record ApiResponse<T>(int code, String message, T data, long timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data, System.currentTimeMillis());
    }

    public static ApiResponse<Void> error(int code, String message) {
        return new ApiResponse<>(code, message, null, System.currentTimeMillis());
    }
}

