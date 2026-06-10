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
    private static final long BEGIN_TIMESTAMP = LocalDateTime.of(2025, 1, 1, 0, 0, 0)
            .toEpochSecond(ZoneOffset.UTC);
    private static final int COUNT_BITS = 32;
    private static final long MAX_SEQUENCE = (1L << COUNT_BITS) - 1;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd");
    private static final Duration COUNTER_TTL = Duration.ofDays(2);

    private final StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        long currentTimestamp = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = currentTimestamp - BEGIN_TIMESTAMP;
        String date = now.format(DATE_FORMATTER);
        String key = "icr:" + keyPrefix + ":" + date + ":" + currentTimestamp;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count == null) {
            throw new IllegalStateException("Redis increment returned null for key: " + key);
        }
        if (count == 1L) {
            stringRedisTemplate.expire(key, COUNTER_TTL);
        }
        long sequence = count - 1;
        if (sequence > MAX_SEQUENCE) {
            throw new IllegalStateException("Redis ID sequence overflow for key: " + key);
        }
        return (timestamp << COUNT_BITS) | sequence;
    }
}
