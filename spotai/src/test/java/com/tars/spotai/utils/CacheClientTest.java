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
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private CacheClient cacheClient;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        cacheClient = new CacheClient(stringRedisTemplate, objectMapper);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void storesObjectAsJsonWithTtl() {
        Shop shop = shop(1L);

        cacheClient.set("cache:shop:1", shop, 30, TimeUnit.MINUTES);

        verify(valueOperations).set(
                eq("cache:shop:1"),
                startsWith("{\"id\":1"),
                longThat(ttl -> ttl >= 1800L && ttl <= 2100L),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    void storesObjectWithLogicalExpire() {
        Shop shop = shop(1L);

        cacheClient.setWithLogicalExpire("cache:shop:1", shop, 30, TimeUnit.MINUTES);

        verify(valueOperations).set(eq("cache:shop:1"), startsWith("{\"expireTime\""));
    }

    @Test
    void returnsValueFromPassThroughCacheHit() throws Exception {
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

    @Test
    void returnsLogicalExpireValueWhenNotExpired() {
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
