package com.tars.spotai.repository;

import com.tars.spotai.entity.ShopType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ShopTypeRepository {
    private final JdbcTemplate jdbcTemplate;

    public ShopTypeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ShopType> findAllOrderBySort() {
        return jdbcTemplate.query(
                """
                        select id, name, icon, sort, create_time, update_time
                        from tb_shop_type
                        order by sort asc, id asc
                        """,
                new ShopTypeRowMapper()
        );
    }

    private static class ShopTypeRowMapper implements RowMapper<ShopType> {
        @Override
        public ShopType mapRow(ResultSet rs, int rowNum) throws SQLException {
            ShopType shopType = new ShopType();
            shopType.setId(rs.getLong("id"));
            shopType.setName(rs.getString("name"));
            shopType.setIcon(rs.getString("icon"));
            shopType.setSort(rs.getObject("sort", Integer.class));
            shopType.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
            shopType.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
            return shopType;
        }

        private LocalDateTime toLocalDateTime(Timestamp timestamp) {
            return timestamp == null ? null : timestamp.toLocalDateTime();
        }
    }
}
