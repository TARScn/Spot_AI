package com.tars.spotai.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Redis-based global ID generator.
 *
 * <pre>
 * 0        1                                32                                64
 * +--------+--------------------------------+----------------------------------+
 * | sign   | timestamp seconds from epoch   | per-second sequence from Redis   |
 * | 1 bit  | 31 bits                        | 32 bits                          |
 * +--------+--------------------------------+----------------------------------+
 * </pre>
 */
@Component
public class RedisIdWorker {
    /* 1. 常量定义 */
    private static final long BEGIN_TIMESTAMP = LocalDateTime.of(2025, 1, 1, 0, 0, 0)
            .toEpochSecond(ZoneOffset.UTC);   // 起始时间戳（2025-01-01 00:00:00 UTC）
    private static final int COUNT_BITS = 32;  // 序列号占用位数
    private static final long MAX_SEQUENCE = (1L << COUNT_BITS) - 1;  // 序列号最大值
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd");
    private static final Duration COUNTER_TTL = Duration.ofDays(2);   // Redis 计数器 key 的过期时间

    /* 2. 依赖 */
    private final StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 生成全局唯一 ID。
     * <p>ID 格式：高 32 位为时间戳偏移（秒），低 32 位为 Redis 自增序列。</p>
     *
     * 1. 获取当前 UTC 时间，计算相对于 BEGIN_TIMESTAMP 的秒数偏移
     * 2. 用 "icr:{keyPrefix}:{yyyy:MM:dd}:{unixSeconds}" 作为 Redis key 执行 INCR
     * 3. 首次递增时设置 key 的 TTL（2 天）
     * 4. 将时间戳左移 32 位后与序列号按位或，组合成 64 位 ID
     */
    public long nextId(String keyPrefix) {
        /* 2.1 计算时间戳偏移 */
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        long currentTimestamp = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = currentTimestamp - BEGIN_TIMESTAMP;

        /* 2.2 构建 Redis key 并递增 */
        String date = now.format(DATE_FORMATTER);
        String key = "icr:" + keyPrefix + ":" + date + ":" + currentTimestamp;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count == null) {
            throw new IllegalStateException("Redis increment returned null for key: " + key);
        }

        /* 2.3 首次创建时设置过期时间 */
        if (count == 1L) {
            stringRedisTemplate.expire(key, COUNTER_TTL);
        }

        /* 2.4 校验序列号上限 */
        long sequence = count - 1;
        if (sequence > MAX_SEQUENCE) {
            throw new IllegalStateException("Redis ID sequence overflow for key: " + key);
        }

        /* 2.5 组装并返回 64 位 ID */
        return (timestamp << COUNT_BITS) | sequence;
    }
}
