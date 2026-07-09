package com.tars.spotai.service;

import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.ShopItemDTO;
import com.tars.spotai.repository.ShopItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read-only application service for shop dishes and services.
 */
@Service
public class ShopItemService {
    private static final int DETAIL_ITEM_LIMIT = 20;

    private final ShopItemRepository shopItemRepository;

    public ShopItemService(ShopItemRepository shopItemRepository) {
        this.shopItemRepository = shopItemRepository;
    }

    public Result<List<ShopItemDTO>> queryByShopId(Long shopId) {
        if (shopId == null || shopId <= 0) {
            return Result.fail("店铺ID不合法");
        }
        return Result.ok(shopItemRepository.findByShopId(shopId, DETAIL_ITEM_LIMIT));
    }
}
