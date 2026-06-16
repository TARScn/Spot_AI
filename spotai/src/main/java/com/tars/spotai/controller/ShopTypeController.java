package com.tars.spotai.controller;

import com.tars.spotai.dto.Result;
import com.tars.spotai.entity.ShopType;
import com.tars.spotai.service.ShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ShopTypeController {
    private final ShopTypeService shopTypeService;

    public ShopTypeController(ShopTypeService shopTypeService) {
        this.shopTypeService = shopTypeService;
    }

    @GetMapping("/shop-type/list")
    public Result<List<ShopType>> listShopTypes() {
        return shopTypeService.listShopTypes();
    }
}
