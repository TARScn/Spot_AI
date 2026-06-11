package com.tars.spotai.controller;

import com.tars.spotai.dto.Result;
import com.tars.spotai.entity.Shop;
import com.tars.spotai.service.ShopService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for shop operations.
 */
@RestController
public class ShopController {
    /* 1. 依赖注入 */
    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    /* 2. 根据 ID 查询商户详情（缓存加速） */
    @GetMapping("/shop/{id}")
    public Result<Shop> queryShopById(@PathVariable Long id) {
        return shopService.queryById(id);
    }

    /* 3. 更新商户信息并失效缓存 */
    @PutMapping("/shop")
    public Result<Void> updateShop(@RequestBody Shop shop) {
        return shopService.update(shop);
    }
}
