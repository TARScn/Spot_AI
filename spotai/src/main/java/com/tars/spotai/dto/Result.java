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
    /* 1. 响应三要素 */
    private boolean success;  // 是否成功
    private T data;           // 成功时的数据
    private String errorMsg;  // 失败时的错误信息

    /* 2. 工厂方法：成功响应 */
    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.success = true;
        result.data = data;
        return result;
    }

    /* 3. 工厂方法：失败响应 */
    public static <T> Result<T> fail(String errorMsg) {
        Result<T> result = new Result<>();
        result.success = false;
        result.errorMsg = errorMsg;
        return result;
    }
}
