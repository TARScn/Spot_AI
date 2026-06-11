package com.tars.spotai.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.dto.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Reusable cache helper based on StringRedisTemplate.
 */
@Component
public class CacheClient {
    /* 1. 常量：缓存异步重建线程池 */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /* 2. 依赖注入 */
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public CacheClient(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /* ========== 写入操作 ========== */

    /* 3. 写入普通缓存：Java 对象序列化为 JSON，设置 TTL（含随机偏移防雪崩） */
    public void set(String key, Object value, long ttl, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, writeValue(value), ttlWithRandomSeconds(ttl, unit), TimeUnit.SECONDS);
    }

    /* 4. 写入逻辑过期缓存：将数据和过期时间一起存入 RedisData 包装 */
    public void setWithLogicalExpire(String key, Object value, long ttl, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(ttl)));
        stringRedisTemplate.opsForValue().set(key, writeValue(redisData));
    }

    /* ========== 查询操作 ========== */

    /* 5. 穿透保护查询（ID 入参版）：命中缓存直接返回，未命中查 DB 并回填。空结果缓存短时空值 */
    public <R, ID> R queryWithPassThrough(String keyPrefix,
                                          ID id,
                                          Class<R> type,
                                          Function<ID, R> dbFallback,
                                          long ttl,
                                          TimeUnit unit) {
        String key = keyPrefix + id;
        return queryWithPassThrough(key, type, () -> dbFallback.apply(id), ttl, unit);
    }

    /* 6. 穿透保护查询（key 入参版）：同上但接受完整 key */
    public <R> R queryWithPassThrough(String key,
                                      Class<R> type,
                                      Supplier<R> dbFallback,
                                      long ttl,
                                      TimeUnit unit) {
        /* 6.1 查缓存 */
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.hasText(json)) {
            return readValue(json, type);
        }
        /* 6.2 缓存存在但为空字符串 => 说明之前已缓存空值，直接返回 null */
        if (json != null) {
            return null;
        }

        /* 6.3 缓存未命中，查数据库 */
        R value = dbFallback.get();
        if (value == null) {
            /* 6.4 数据库无记录 => 缓存空值防穿透 */
            stringRedisTemplate.opsForValue().set(
                    key,
                    "",
                    RedisConstants.CACHE_NULL_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
            return null;
        }

        /* 6.5 数据库有记录 => 回填缓存 */
        set(key, value, ttl, unit);
        return value;
    }

    /* 7. 逻辑过期查询（ID + lockKey 前缀版）：过期时异步重建，同步返回旧值 */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix,
                                            ID id,
                                            Class<R> type,
                                            Function<ID, R> dbFallback,
                                            long ttl,
                                            TimeUnit unit,
                                            String lockKeyPrefix) {
        String key = keyPrefix + id;
        String lockKey = lockKeyPrefix + id;
        return queryWithLogicalExpire(key, type, () -> dbFallback.apply(id), ttl, unit, lockKey);
    }

    /* 8. 逻辑过期查询（完整 key 版） */
    public <R> R queryWithLogicalExpire(String key,
                                        Class<R> type,
                                        Supplier<R> dbFallback,
                                        long ttl,
                                        TimeUnit unit,
                                        String lockKey) {
        /* 8.1 读取缓存 */
        String json = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(json)) {
            /* 8.2 缓存不存在或空字符串 */
            if (json != null) {
                return null; // 已缓存空值
            }
            /* 8.3 完全不存在 => 同步回源并设置逻辑过期 */
            R value = dbFallback.get();
            if (value == null) {
                stringRedisTemplate.opsForValue().set(
                        key,
                        "",
                        RedisConstants.CACHE_NULL_TTL_MINUTES,
                        TimeUnit.MINUTES
                );
                return null;
            }
            setWithLogicalExpire(key, value, ttl, unit);
            return value;
        }

        /* 8.4 缓存有数据 => 检查是否过期 */
        RedisData redisData = readValue(json, RedisData.class);
        R value = objectMapper.convertValue(redisData.getData(), type);
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            return value; // 未过期，直接返回
        }

        /* 8.5 已过期 => 尝试获取锁，异步重建 */
        if (tryLock(lockKey)) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R freshValue = dbFallback.get();
                    if (freshValue == null) {
                        stringRedisTemplate.opsForValue().set(
                                key,
                                "",
                                RedisConstants.CACHE_NULL_TTL_MINUTES,
                                TimeUnit.MINUTES
                        );
                    } else {
                        setWithLogicalExpire(key, freshValue, ttl, unit);
                    }
                } finally {
                    unlock(lockKey);
                }
            });
        }
        /* 8.6 返回旧值（无论是否抢到锁） */
        return value;
    }

    /* 9. 删除缓存 */
    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    /* ========== 私有工具方法 ========== */

    /* 10. 尝试获取 Redis 分布式锁（set if absent） */
    private boolean tryLock(String key) {
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                key,
                "1",
                RedisConstants.CACHE_REBUILD_LOCK_TTL_SECONDS,
                TimeUnit.SECONDS
        );
        return Boolean.TRUE.equals(success);
    }

    /* 11. 释放 Redis 分布式锁 */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /* 12. 在基础 TTL 上叠加随机秒数，防止大量缓存同时过期（缓存雪崩） */
    private long ttlWithRandomSeconds(long ttl, TimeUnit unit) {
        long baseSeconds = unit.toSeconds(ttl);
        long randomSeconds = ThreadLocalRandom.current().nextLong(RedisConstants.CACHE_RANDOM_TTL_SECONDS + 1);
        return baseSeconds + randomSeconds;
    }

    /* 13. JSON 反序列化 */
    private <T> T readValue(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("缓存数据格式错误", e);
        }
    }

    /* 14. JSON 序列化 */
    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("缓存数据序列化失败", e);
        }
    }
}
