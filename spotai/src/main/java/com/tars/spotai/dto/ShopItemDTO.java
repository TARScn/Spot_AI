package com.tars.spotai.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Read model for a dish or service offered by a shop.
 */
@Data
@NoArgsConstructor
public class ShopItemDTO {
    private Long id;
    private Long shopId;
    private String name;
    private String description;
    private Long price;
    private Integer sort;
}
