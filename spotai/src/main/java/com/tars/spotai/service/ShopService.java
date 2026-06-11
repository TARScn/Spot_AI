package com.tars.spotai.service;

import com.tars.spotai.dto.Result;
import com.tars.spotai.entity.Shop;
import com.tars.spotai.repository.ShopRepository;
import com.tars.spotai.utils.CacheClient;
import com.tars.spotai.utils.RedisConstants;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Service for shop query operations backed by Redis cache.
 */
@Service
public class ShopService {
    /* 1. 依赖注入 */
    private final ShopRepository shopRepository;
    private final CacheClient cacheClient;

    public ShopService(ShopRepository shopRepository,
                       CacheClient cacheClient) {
        this.shopRepository = shopRepository;
        this.cacheClient = cacheClient;
    }

    /* ========== 业务方法 ========== */

    /* 2. 根据 ID 查询商户（逻辑过期缓存策略） */
    public Result<Shop> queryById(Long id) {
        /* 2.1 参数校验 */
        if (id == null || id <= 0) {
            return Result.fail("商户ID不合法");
        }

        /* 2.2 查缓存（过期时异步回源，返回旧值） */
        Shop shop = cacheClient.queryWithLogicalExpire(
                RedisConstants.CACHE_SHOP_KEY,
                id,
                Shop.class,
                shopRepository::findById,
                RedisConstants.CACHE_SHOP_TTL_MINUTES,
                TimeUnit.MINUTES,
                RedisConstants.LOCK_SHOP_KEY
        );
        if (shop == null) {
            return Result.fail("商户不存在");
        }
        return Result.ok(shop);
    }

    /* 3. 预写缓存（预热/重建时调用，设置逻辑过期） */
    public void setShopWithLogicalExpire(Long id, Shop shop, long ttl, TimeUnit unit) {
        cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY + id, shop, ttl, unit);
    }

    /* 4. 更新商户：先写 MySQL，再删缓存 */
    public Result<Void> update(Shop shop) {
        /* 4.1 参数校验 */
        if (shop == null || shop.getId() == null || shop.getId() <= 0) {
            return Result.fail("商户ID不合法");
        }

        /* 4.2 更新数据库 */
        int affectedRows = shopRepository.updateById(shop);
        if (affectedRows == 0) {
            return Result.fail("商户不存在");
        }

        /* 4.3 删除缓存，下次读取时重建 */
        cacheClient.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return Result.ok(null);
    }
}
