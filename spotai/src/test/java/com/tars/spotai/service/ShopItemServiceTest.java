package com.tars.spotai.service;

import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.ShopItemDTO;
import com.tars.spotai.repository.ShopItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopItemServiceTest {
    @Mock
    private ShopItemRepository shopItemRepository;

    private ShopItemService shopItemService;

    @BeforeEach
    void setUp() {
        shopItemService = new ShopItemService(shopItemRepository);
    }

    @Test
    void returnsOrderedItemsForShop() {
        ShopItemDTO first = item(11L, 7L, "招牌酸菜鱼", 7800L, 1);
        ShopItemDTO second = item(12L, 7L, "家常豆腐", 2100L, 2);
        when(shopItemRepository.findByShopId(7L, 20)).thenReturn(List.of(first, second));

        Result<List<ShopItemDTO>> result = shopItemService.queryByShopId(7L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsExactly(first, second);
        verify(shopItemRepository).findByShopId(7L, 20);
    }

    @Test
    void rejectsInvalidShopIdWithoutQueryingDatabase() {
        Result<List<ShopItemDTO>> result = shopItemService.queryByShopId(0L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(shopItemRepository).isNotNull();
        verify(shopItemRepository, never()).findByShopId(0L, 20);
    }

    private ShopItemDTO item(Long id, Long shopId, String name, Long price, Integer sort) {
        ShopItemDTO item = new ShopItemDTO();
        item.setId(id);
        item.setShopId(shopId);
        item.setName(name);
        item.setDescription("真实项目描述");
        item.setPrice(price);
        item.setSort(sort);
        return item;
    }
}
