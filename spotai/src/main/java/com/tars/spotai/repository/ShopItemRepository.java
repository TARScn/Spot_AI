package com.tars.spotai.repository;

import com.tars.spotai.dto.ShopItemDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Queries dishes and services stored in tb_shop_item.
 */
@Repository
public class ShopItemRepository {
    private final JdbcTemplate jdbcTemplate;

    public ShopItemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ShopItemDTO> findByShopId(Long shopId, int limit) {
        return jdbcTemplate.query(
                """
                        select id, shop_id, name, description, price, sort
                        from tb_shop_item
                        where shop_id = ?
                        order by sort asc, id asc
                        limit ?
                        """,
                (rs, rowNum) -> {
                    ShopItemDTO item = new ShopItemDTO();
                    item.setId(rs.getLong("id"));
                    item.setShopId(rs.getLong("shop_id"));
                    item.setName(rs.getString("name"));
                    item.setDescription(rs.getString("description"));
                    item.setPrice(rs.getObject("price", Long.class));
                    item.setSort(rs.getObject("sort", Integer.class));
                    return item;
                },
                shopId,
                Math.max(1, Math.min(limit, 50))
        );
    }
}
