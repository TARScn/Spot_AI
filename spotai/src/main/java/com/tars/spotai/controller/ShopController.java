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
    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    /**
     * Returns shop detail by ID. Uses Redis cache in the service layer.
     */
    @GetMapping("/shop/{id}")
    public Result<Shop> queryShopById(@PathVariable Long id) {
        return shopService.queryById(id);
    }

    /**
     * Updates shop data and removes the old Redis cache.
     */
    @PutMapping("/shop")
    public Result<Void> updateShop(@RequestBody Shop shop) {
        return shopService.update(shop);
    }
}
