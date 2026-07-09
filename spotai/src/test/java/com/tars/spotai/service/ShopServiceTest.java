package com.tars.spotai.service;

import com.tars.spotai.config.MqEventProperties;
import com.tars.spotai.dto.ShopChangedMessage;
import com.tars.spotai.dto.Result;
import com.tars.spotai.entity.Shop;
import com.tars.spotai.repository.ShopRepository;
import com.tars.spotai.utils.CacheClient;
import com.tars.spotai.utils.RedisConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {
    /* 1. Mock 依赖 */
    @Mock
    private ShopRepository shopRepository;

    @Mock
    private CacheClient cacheClient;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private MqEventPublisher mqEventPublisher;

    private ShopService shopService;
    private MqEventProperties mqEventProperties;

    /* 2. 测试初始化 */
    @BeforeEach
    void setUp() {
        mqEventProperties = new MqEventProperties();
        shopService = new ShopService(
                shopRepository,
                cacheClient,
                stringRedisTemplate,
                mqEventPublisher,
                mqEventProperties
        );
    }

    /* ========== queryById() 测试 ========== */

    @Test
    void queriesShopThroughCacheClient() {
        /* 1. 正常查询：CacheClient 返回商户 → service 返回成功 */
        Shop shop = shop(1L);
        when(cacheClient.queryWithLogicalExpire(
                eq(RedisConstants.CACHE_SHOP_KEY),
                eq(shop.getId()),
                eq(Shop.class),
                any(),
                eq(RedisConstants.CACHE_SHOP_TTL_MINUTES),
                eq(TimeUnit.MINUTES),
                eq(RedisConstants.LOCK_SHOP_KEY)
        )).thenReturn(shop);

        Result<Shop> result = shopService.queryById(shop.getId());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isSameAs(shop);
    }

    @Test
    void returnsFailureWhenCacheClientReturnsNull() {
        /* 2. 商户不存在：CacheClient 返回 null → service 返回 "商户不存在" */
        long missingId = 404L;
        when(cacheClient.queryWithLogicalExpire(
                eq(RedisConstants.CACHE_SHOP_KEY),
                eq(missingId),
                eq(Shop.class),
                any(),
                eq(RedisConstants.CACHE_SHOP_TTL_MINUTES),
                eq(TimeUnit.MINUTES),
                eq(RedisConstants.LOCK_SHOP_KEY)
        )).thenReturn(null);

        Result<Shop> result = shopService.queryById(missingId);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMsg()).isEqualTo("商户不存在");
    }

    @Test
    void rejectsInvalidShopIdBeforeQueryingCache() {
        /* 3. 不合法的 ID：不查缓存直接返回 "商户ID不合法" */
        Result<Shop> result = shopService.queryById(0L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMsg()).isEqualTo("商户ID不合法");
        verify(cacheClient, never()).queryWithLogicalExpire(any(), any(), any(), any(), any(Long.class), any(), any());
    }

    /* ========== setShopWithLogicalExpire() 测试 ========== */

    @Test
    void writesLogicalExpireCacheForWarmUp() {
        /* 4. 缓存预热/重建委托给 CacheClient */
        Shop shop = shop(2L);

        shopService.setShopWithLogicalExpire(shop.getId(), shop, 30, TimeUnit.MINUTES);

        verify(cacheClient).setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY + shop.getId(), shop, 30, TimeUnit.MINUTES);
    }

    /* ========== update() 测试 ========== */

    @Test
    void updatesDatabaseAndDeletesCacheWhenShopExists() {
        /* 5. 更新成功：写 DB + 删缓存 */
        Shop shop = shop(3L);
        when(shopRepository.updateById(shop)).thenReturn(1);

        Result<Void> result = shopService.update(shop);

        assertThat(result.isSuccess()).isTrue();
        verify(shopRepository).updateById(shop);
        verify(mqEventPublisher).publishOrRun(
                eq("spotai.shop.changed"),
                any(ShopChangedMessage.class),
                any(Runnable.class)
        );
    }

    @Test
    void returnsFailureAndKeepsCacheWhenUpdatedShopDoesNotExist() {
        /* 6. 更新失败（DB 无记录）：不删缓存，返回 "商户不存在" */
        Shop shop = shop(404L);
        when(shopRepository.updateById(shop)).thenReturn(0);

        Result<Void> result = shopService.update(shop);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMsg()).isEqualTo("商户不存在");
        verify(cacheClient, never()).delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
    }

    @Test
    void shopChangedConsumerDelegatesToShopService() {
        ShopService service = org.mockito.Mockito.mock(ShopService.class);
        ShopChangedConsumer consumer = new ShopChangedConsumer(service);

        consumer.onMessage(new ShopChangedMessage(3L, "UPDATED", null));

        verify(service).handleShopChanged(3L);
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
}
