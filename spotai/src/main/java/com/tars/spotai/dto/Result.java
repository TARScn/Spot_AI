package com.tars.spotai.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic API response wrapper that standardizes success/failure responses
 * returned to the client.
 */
@Data
@NoArgsConstructor
public class Result<T> {
    private boolean success;
    private T data;
    private String errorMsg;

    /** Creates a success response with the given data. */
    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.success = true;
        result.data = data;
        return result;
    }

    /** Creates a failure response with the given error message. */
    public static <T> Result<T> fail(String errorMsg) {
        Result<T> result = new Result<>();
        result.success = false;
        result.errorMsg = errorMsg;
        return result;
    }
}
