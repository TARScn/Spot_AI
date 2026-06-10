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
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public CacheClient(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Stores any Java object as JSON with a Redis TTL.
     */
    public void set(String key, Object value, long ttl, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, writeValue(value), ttlWithRandomSeconds(ttl, unit), TimeUnit.SECONDS);
    }

    /**
     * Stores any Java object as JSON with a logical expiration time.
     */
    public void setWithLogicalExpire(String key, Object value, long ttl, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(ttl)));
        stringRedisTemplate.opsForValue().set(key, writeValue(redisData));
    }

    /**
     * Queries cache with pass-through strategy and caches empty values to reduce cache penetration.
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix,
                                          ID id,
                                          Class<R> type,
                                          Function<ID, R> dbFallback,
                                          long ttl,
                                          TimeUnit unit) {
        String key = keyPrefix + id;
        return queryWithPassThrough(key, type, () -> dbFallback.apply(id), ttl, unit);
    }

    /**
     * Queries a specified cache key with pass-through strategy and caches empty values.
     */
    public <R> R queryWithPassThrough(String key,
                                      Class<R> type,
                                      Supplier<R> dbFallback,
                                      long ttl,
                                      TimeUnit unit) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.hasText(json)) {
            return readValue(json, type);
        }
        if (json != null) {
            return null;
        }

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

        set(key, value, ttl, unit);
        return value;
    }

    /**
     * Queries cache with logical expiration. Expired values are returned immediately,
     * while the cache is rebuilt asynchronously by the request that obtains the Redis lock.
     */
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

    /**
     * Queries a specified cache key with logical expiration.
     */
    public <R> R queryWithLogicalExpire(String key,
                                        Class<R> type,
                                        Supplier<R> dbFallback,
                                        long ttl,
                                        TimeUnit unit,
                                        String lockKey) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(json)) {
            if (json != null) {
                return null;
            }
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

        RedisData redisData = readValue(json, RedisData.class);
        R value = objectMapper.convertValue(redisData.getData(), type);
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            return value;
        }

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
        return value;
    }

    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    private boolean tryLock(String key) {
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                key,
                "1",
                RedisConstants.CACHE_REBUILD_LOCK_TTL_SECONDS,
                TimeUnit.SECONDS
        );
        return Boolean.TRUE.equals(success);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    private long ttlWithRandomSeconds(long ttl, TimeUnit unit) {
        long baseSeconds = unit.toSeconds(ttl);
        long randomSeconds = ThreadLocalRandom.current().nextLong(RedisConstants.CACHE_RANDOM_TTL_SECONDS + 1);
        return baseSeconds + randomSeconds;
    }

    private <T> T readValue(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("缓存数据格式错误", e);
        }
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("缓存数据序列化失败", e);
        }
    }
}
