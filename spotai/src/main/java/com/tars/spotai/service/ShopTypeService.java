package com.tars.spotai.service;

import com.tars.spotai.dto.Result;
import com.tars.spotai.entity.ShopType;
import com.tars.spotai.repository.ShopTypeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShopTypeService {
    private final ShopTypeRepository shopTypeRepository;

    public ShopTypeService(ShopTypeRepository shopTypeRepository) {
        this.shopTypeRepository = shopTypeRepository;
    }

    public Result<List<ShopType>> listShopTypes() {
        return Result.ok(shopTypeRepository.findAllOrderBySort());
    }
}
