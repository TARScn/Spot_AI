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
    private final ShopRepository shopRepository;
    private final CacheClient cacheClient;

    public ShopService(ShopRepository shopRepository,
                       CacheClient cacheClient) {
        this.shopRepository = shopRepository;
        this.cacheClient = cacheClient;
    }

    /**
     * Queries shop detail by ID with logical expiration.
     * Expired hot cache values are returned immediately while one background task rebuilds them.
     */
    public Result<Shop> queryById(Long id) {
        if (id == null || id <= 0) {
            return Result.fail("商户ID不合法");
        }

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

    /**
     * Writes a shop cache value with logical expiration. Intended for cache warm-up and rebuilds.
     */
    public void setShopWithLogicalExpire(Long id, Shop shop, long ttl, TimeUnit unit) {
        cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY + id, shop, ttl, unit);
    }

    /**
     * Updates shop data in MySQL first, then removes the related Redis cache.
     */
    public Result<Void> update(Shop shop) {
        if (shop == null || shop.getId() == null || shop.getId() <= 0) {
            return Result.fail("商户ID不合法");
        }

        int affectedRows = shopRepository.updateById(shop);
        if (affectedRows == 0) {
            return Result.fail("商户不存在");
        }

        cacheClient.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return Result.ok(null);
    }
}
