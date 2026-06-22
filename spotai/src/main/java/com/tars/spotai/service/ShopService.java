package com.tars.spotai.service;

import com.tars.spotai.dto.Result;
import com.tars.spotai.entity.Shop;
import com.tars.spotai.repository.ShopRepository;
import com.tars.spotai.utils.CacheClient;
import com.tars.spotai.utils.RedisConstants;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service for shop query operations backed by Redis cache.
 */
@Service
public class ShopService {
    private static final int PAGE_SIZE = 10;
    private static final double DEFAULT_GEO_RADIUS_KM = 5.0;

    /* 1. 依赖注入 */
    private final ShopRepository shopRepository;
    private final CacheClient cacheClient;
    private final StringRedisTemplate stringRedisTemplate;

    public ShopService(ShopRepository shopRepository,
                       CacheClient cacheClient,
                       StringRedisTemplate stringRedisTemplate) {
        this.shopRepository = shopRepository;
        this.cacheClient = cacheClient;
        this.stringRedisTemplate = stringRedisTemplate;
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
        loadShopGeo();
        return Result.ok(null);
    }

    public Result<List<Shop>> queryShopByType(Long typeId, Integer current, Double x, Double y) {
        if (typeId == null || typeId <= 0) {
            return Result.fail("商户类型ID不合法");
        }
        int page = current == null || current <= 0 ? 1 : current;
        if (x == null || y == null) {
            return Result.ok(shopRepository.findByType(typeId, page, PAGE_SIZE));
        }

        int from = (page - 1) * PAGE_SIZE;
        int end = page * PAGE_SIZE;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .radius(
                        RedisConstants.SHOP_GEO_KEY + typeId,
                        new Circle(new Point(x, y), new Distance(DEFAULT_GEO_RADIUS_KM, Metrics.KILOMETERS)),
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                .includeDistance()
                                .sortAscending()
                                .limit(end)
                );
        if (results == null || results.getContent().size() <= from) {
            return Result.ok(List.of());
        }

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> pageResults =
                results.getContent().subList(from, results.getContent().size());
        Map<Long, Double> distanceMap = new HashMap<>();
        List<Long> ids = pageResults.stream()
                .map(result -> {
                    Long shopId = Long.valueOf(result.getContent().getName());
                    distanceMap.put(shopId, result.getDistance().getValue());
                    return shopId;
                })
                .toList();

        Map<Long, Shop> shopMap = new HashMap<>();
        for (Shop shop : shopRepository.findByIds(ids)) {
            shop.setDistance(distanceMap.get(shop.getId()));
            shopMap.put(shop.getId(), shop);
        }
        List<Shop> shops = ids.stream()
                .map(shopMap::get)
                .filter(shop -> shop != null)
                .toList();
        return Result.ok(shops);
    }

    public Result<List<Shop>> search(String keyword) {
        String value = keyword == null ? "" : keyword.trim();
        if (value.isEmpty()) {
            return Result.ok(List.of());
        }
        return Result.ok(shopRepository.search(value, 12));
    }

    public void loadShopGeo() {
        Map<Long, List<Shop>> shopsByType = shopRepository.findAll().stream()
                .filter(shop -> shop.getTypeId() != null && shop.getX() != null && shop.getY() != null)
                .collect(Collectors.groupingBy(Shop::getTypeId));
        shopsByType.forEach((typeId, shops) -> {
            String key = RedisConstants.SHOP_GEO_KEY + typeId;
            stringRedisTemplate.delete(key);
            for (Shop shop : shops) {
                stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
            }
        });
    }
}
