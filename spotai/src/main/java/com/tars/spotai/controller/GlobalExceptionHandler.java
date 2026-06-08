package com.tars.spotai.controller;

import com.tars.spotai.dto.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for the application.
 * Catches exceptions thrown by controllers and returns uniform Result responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles {@link MethodArgumentNotValidException} triggered by failed {@code @Valid} validation.
     * Extracts the first field error message, or a default message if none is present.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().isEmpty()
                ? "请求参数错误"
                : e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return Result.fail(message);
    }

    /**
     * Handles {@link MissingServletRequestParameterException} when a required request parameter is absent.
     * Returns a message indicating which parameter is missing.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail("缺少参数：" + e.getParameterName());
    }

    /**
     * Catch-all handler for any unexpected exception.
     * Logs the error and returns a generic system error message to the client.
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("Unexpected server error", e);
        return Result.fail("系统异常，请稍后重试");
    }
}
