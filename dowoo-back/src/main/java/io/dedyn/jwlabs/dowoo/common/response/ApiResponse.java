package io.dedyn.jwlabs.dowoo.common.response;

public record ApiResponse<T>(int status, T data, String message, ApiError error) {

    public static <T> ApiResponse<T> success(int status, T data, String message) {
        return new ApiResponse<>(status, data, message, null);
    }

    public static ApiResponse<Void> failure(int status, String code, String message, String details) {
        return new ApiResponse<>(status, null, message, new ApiError(code, details));
    }
}
