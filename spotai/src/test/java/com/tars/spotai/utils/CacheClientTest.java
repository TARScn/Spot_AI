package com.tars.spotai.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tars.spotai.dto.RedisData;
import com.tars.spotai.entity.Shop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheClientTest {
    /* 1. Mock 依赖 */
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private CacheClient cacheClient;

    /* 2. 测试初始化 */
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        cacheClient = new CacheClient(stringRedisTemplate, objectMapper);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    /* ========== set() 测试 ========== */

    @Test
    void storesObjectAsJsonWithTtl() {
        /* 验证 set() 写入 JSON + TTL（含随机偏移 1800~2100 秒） */
        Shop shop = shop(1L);

        cacheClient.set("cache:shop:1", shop, 30, TimeUnit.MINUTES);

        verify(valueOperations).set(
                eq("cache:shop:1"),
                startsWith("{\"id\":1"),
                longThat(ttl -> ttl >= 1800L && ttl <= 2100L),
                eq(TimeUnit.SECONDS)
        );
    }

    /* ========== setWithLogicalExpire() 测试 ========== */

    @Test
    void storesObjectWithLogicalExpire() {
        /* 验证 setWithLogicalExpire() 写入带有 expireTime 的 JSON */
        Shop shop = shop(1L);

        cacheClient.setWithLogicalExpire("cache:shop:1", shop, 30, TimeUnit.MINUTES);

        verify(valueOperations).set(eq("cache:shop:1"), startsWith("{\"expireTime\""));
    }

    /* ========== queryWithPassThrough() 测试 ========== */

    @Test
    void returnsValueFromPassThroughCacheHit() throws Exception {
        /* 1. 缓存命中：直接返回反序列化后的对象 */
        Shop cachedShop = shop(1L);
        when(valueOperations.get("cache:shop:1")).thenReturn(objectMapper.writeValueAsString(cachedShop));

        Shop result = cacheClient.queryWithPassThrough(
                RedisConstants.CACHE_SHOP_KEY,
                1L,
                Shop.class,
                id -> null,
                30,
                TimeUnit.MINUTES
        );

        assertThat(result.getName()).isEqualTo("103茶餐厅");
    }

    @Test
    void cachesEmptyValueWhenPassThroughMissesDatabase() {
        /* 2. 缓存穿透保护：DB 无数据时缓存空值 */
        Shop result = cacheClient.queryWithPassThrough(
                RedisConstants.CACHE_SHOP_KEY,
                999L,
                Shop.class,
                id -> null,
                30,
                TimeUnit.MINUTES
        );

        assertThat(result).isNull();
        verify(valueOperations).set(
                eq("cache:shop:999"),
                eq(""),
                eq(RedisConstants.CACHE_NULL_TTL_MINUTES),
                eq(TimeUnit.MINUTES)
        );
    }

    @Test
    void returnsNullFromPassThroughCachedEmptyValueWithoutDatabaseQuery() {
        /* 3. 空值缓存命中：不查 DB 直接返回 null */
        AtomicInteger dbCalls = new AtomicInteger();
        when(valueOperations.get("cache:shop:999")).thenReturn("");

        Shop result = cacheClient.queryWithPassThrough(
                RedisConstants.CACHE_SHOP_KEY,
                999L,
                Shop.class,
                id -> {
                    dbCalls.incrementAndGet();
                    return shop(id);
                },
                30,
                TimeUnit.MINUTES
        );

        assertThat(result).isNull();
        assertThat(dbCalls).hasValue(0);
    }

    /* ========== queryWithLogicalExpire() 测试 ========== */

    @Test
    void returnsLogicalExpireValueWhenNotExpired() {
        /* 4. 逻辑过期缓存未过期：直接返回，不尝试获取锁 */
        Shop cachedShop = shop(1L);
        when(valueOperations.get("cache:shop:1")).thenReturn(writeRedisData(cachedShop, LocalDateTime.now().plusMinutes(10)));

        Shop result = cacheClient.queryWithLogicalExpire(
                RedisConstants.CACHE_SHOP_KEY,
                1L,
                Shop.class,
                id -> null,
                30,
                TimeUnit.MINUTES,
                RedisConstants.LOCK_SHOP_KEY
        );

        assertThat(result.getName()).isEqualTo("103茶餐厅");
        verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), eq(RedisConstants.LOCK_SHOP_TTL_SECONDS), eq(TimeUnit.SECONDS));
    }

    @Test
    void returnsExpiredValueAndRebuildsCacheWhenLockAcquired() {
        /* 5. 缓存过期且获取到锁：返回旧值 + 异步重建 */
        Shop expiredShop = shop(5L);
        Shop freshShop = shop(5L);
        freshShop.setName("重建后的商户");
        when(valueOperations.get("cache:shop:5")).thenReturn(writeRedisData(expiredShop, LocalDateTime.now().minusMinutes(1)));
        when(valueOperations.setIfAbsent("lock:shop:5", "1", RedisConstants.LOCK_SHOP_TTL_SECONDS, TimeUnit.SECONDS)).thenReturn(true);

        Shop result = cacheClient.queryWithLogicalExpire(
                RedisConstants.CACHE_SHOP_KEY,
                5L,
                Shop.class,
                id -> freshShop,
                30,
                TimeUnit.MINUTES,
                RedisConstants.LOCK_SHOP_KEY
        );

        assertThat(result.getName()).isEqualTo("103茶餐厅");
        verify(valueOperations, timeout(3000)).set(eq("cache:shop:5"), anyString());
        verify(stringRedisTemplate, timeout(3000)).delete("lock:shop:5");
    }

    @Test
    void returnsExpiredValueWithoutRebuildWhenLockIsBusy() {
        /* 6. 缓存过期但锁被占用：返回旧值，不重建 */
        Shop expiredShop = shop(6L);
        AtomicInteger dbCalls = new AtomicInteger();
        when(valueOperations.get("cache:shop:6")).thenReturn(writeRedisData(expiredShop, LocalDateTime.now().minusMinutes(1)));
        when(valueOperations.setIfAbsent("lock:shop:6", "1", RedisConstants.LOCK_SHOP_TTL_SECONDS, TimeUnit.SECONDS)).thenReturn(false);

        Shop result = cacheClient.queryWithLogicalExpire(
                RedisConstants.CACHE_SHOP_KEY,
                6L,
                Shop.class,
                id -> {
                    dbCalls.incrementAndGet();
                    return shop(id);
                },
                30,
                TimeUnit.MINUTES,
                RedisConstants.LOCK_SHOP_KEY
        );

        assertThat(result.getId()).isEqualTo(6L);
        assertThat(dbCalls).hasValue(0);
        verify(stringRedisTemplate, never()).delete("lock:shop:6");
    }

    /* ========== 测试辅助方法 ========== */

    private Shop shop(Long id) {
        Shop shop = new Shop();
        shop.setId(id);
        shop.setName("103茶餐厅");
        shop.setTypeId(1L);
        shop.setImages("https://example.com/shop.jpg");
        shop.setArea("大关");
        shop.setAddress("金华路锦昌文华苑29号");
        shop.setX(120.149192);
        shop.setY(30.316078);
        shop.setAvgPrice(80L);
        shop.setSold(4215);
        shop.setComments(3035);
        shop.setScore(37);
        shop.setOpenHours("10:00-22:00");
        shop.setCreateTime(LocalDateTime.of(2021, 12, 22, 10, 10, 39));
        shop.setUpdateTime(LocalDateTime.of(2022, 1, 13, 9, 32, 19));
        return shop;
    }

    private String writeRedisData(Shop shop, LocalDateTime expireTime) {
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(expireTime);
        try {
            return objectMapper.writeValueAsString(redisData);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
