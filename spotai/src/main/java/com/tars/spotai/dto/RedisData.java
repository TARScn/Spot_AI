package com.tars.spotai.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Wrapper for logical-expiration Redis cache values.
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
