package com.tars.spotai.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Wrapper for logical-expiration Redis cache values.
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;  // 逻辑过期时间
    private Object data;               // 实际缓存数据
}
