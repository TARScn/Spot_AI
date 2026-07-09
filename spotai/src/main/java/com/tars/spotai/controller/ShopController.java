package com.tars.spotai.controller;

import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.ShopItemDTO;
import com.tars.spotai.entity.Shop;
import com.tars.spotai.service.ShopItemService;
import com.tars.spotai.service.ShopService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for shop operations.
 */
@RestController
public class ShopController {
    /* 1. 依赖注入 */
    private final ShopService shopService;
    private final ShopItemService shopItemService;

    public ShopController(ShopService shopService, ShopItemService shopItemService) {
        this.shopService = shopService;
        this.shopItemService = shopItemService;
    }

    /* 2. 根据 ID 查询商户详情（缓存加速） */
    @GetMapping("/shop/{id}")
    public Result<Shop> queryShopById(@PathVariable Long id) {
        return shopService.queryById(id);
    }

    @GetMapping("/shop/{id}/items")
    public Result<List<ShopItemDTO>> queryShopItems(@PathVariable Long id) {
        return shopItemService.queryByShopId(id);
    }

    @GetMapping("/shop/of/type")
    public Result<List<Shop>> queryShopByType(@RequestParam Long typeId,
                                              @RequestParam(defaultValue = "1") Integer current,
                                              @RequestParam(required = false) Double x,
                                              @RequestParam(required = false) Double y) {
        return shopService.queryShopByType(typeId, current, x, y);
    }

    /* 3. 更新商户信息并失效缓存 */
    @GetMapping("/shop/search")
    public Result<List<Shop>> searchShop(@RequestParam("keyword") String keyword) {
        return shopService.search(keyword);
    }

    @PutMapping("/shop")
    public Result<Void> updateShop(@RequestBody Shop shop) {
        return shopService.update(shop);
    }

    @PutMapping("/shop/geo/load")
    public Result<Void> loadShopGeo() {
        shopService.loadShopGeo();
        return Result.ok(null);
    }
}
