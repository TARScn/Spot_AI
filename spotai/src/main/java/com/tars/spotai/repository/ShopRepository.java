package com.tars.spotai.repository;

import com.tars.spotai.entity.Shop;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Repository for querying shop data from tb_shop.
 */
@Repository
public class ShopRepository {
    /* 1. 依赖注入 */
    private final JdbcTemplate jdbcTemplate;

    public ShopRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /* 2. 根据 ID 查询商户（不存在返回 null） */
    public Shop findById(Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, name, type_id, images, area, address, x, y,
                                   avg_price, sold, comments, score, open_hours,
                                   create_time, update_time
                            from tb_shop
                            where id = ?
                            """,
                    new ShopRowMapper(),
                    id
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public List<Shop> findAll() {
        return jdbcTemplate.query(
                """
                        select id, name, type_id, images, area, address, x, y,
                               avg_price, sold, comments, score, open_hours,
                               create_time, update_time
                        from tb_shop
                        """,
                new ShopRowMapper()
        );
    }

    public List<Shop> findByType(Long typeId, int current, int pageSize) {
        int page = Math.max(current, 1);
        int offset = (page - 1) * pageSize;
        return jdbcTemplate.query(
                """
                        select id, name, type_id, images, area, address, x, y,
                               avg_price, sold, comments, score, open_hours,
                               create_time, update_time
                        from tb_shop
                        where type_id = ?
                        order by update_time desc, id desc
                        limit ?, ?
                        """,
                new ShopRowMapper(),
                typeId,
                offset,
                pageSize
        );
    }

    public List<Shop> search(String keyword, int limit) {
        List<String> terms = buildSearchTerms(keyword);
        if (terms.isEmpty()) {
            return Collections.emptyList();
        }
        String conditions = terms.stream()
                .map(word -> "(name like ? or area like ? or address like ?)")
                .collect(Collectors.joining(" or "));
        List<Object> args = new ArrayList<>();
        for (String term : terms) {
            String pattern = "%" + term + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        String rawPattern = "%" + keyword.trim() + "%";
        String firstTermPattern = "%" + terms.get(0) + "%";
        args.add(rawPattern);
        args.add(firstTermPattern);
        args.add(firstTermPattern);
        args.add(firstTermPattern);
        args.add(Math.max(1, Math.min(limit, 20)));
        return jdbcTemplate.query(
                """
                select id, name, type_id, images, area, address, x, y,
                       avg_price, sold, comments, score, open_hours,
                       create_time, update_time
                from tb_shop
                where 
                """ + conditions + """

                order by
                    case
                        when name like ? then 0
                        when name like ? then 1
                        when area like ? or address like ? then 2
                        else 3
                    end,
                    score desc, comments desc, id desc
                limit ?
                """,
                new ShopRowMapper(),
                args.toArray()
        );
    }

    private List<String> buildSearchTerms(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }
        List<String> terms = new ArrayList<>();
        for (String segment : keyword.trim().split("[\\s,，.。;；:：!！?？()（）\\[\\]【】<>《》/\\\\|、\"'“”‘’-]+")) {
            String value = segment.trim();
            if (value.isEmpty()) {
                continue;
            }
            terms.add(value);
            if (value.length() >= 5) {
                int window = Math.min(5, value.length());
                for (int index = 0; index <= value.length() - window && terms.size() < 12; index++) {
                    terms.add(value.substring(index, index + window));
                }
            }
        }
        return terms.stream()
                .distinct()
                .limit(12)
                .toList();
    }

    public List<Shop> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        return jdbcTemplate.query(
                """
                        select id, name, type_id, images, area, address, x, y,
                               avg_price, sold, comments, score, open_hours,
                               create_time, update_time
                        from tb_shop
                        where id in (
                        """ + placeholders + ")",
                new ShopRowMapper(),
                ids.toArray()
        );
    }

    /* 3. 更新商户信息，返回影响行数 */
    public int updateById(Shop shop) {
        return jdbcTemplate.update(
                """
                        update tb_shop
                        set name = ?,
                            type_id = ?,
                            images = ?,
                            area = ?,
                            address = ?,
                            x = ?,
                            y = ?,
                            avg_price = ?,
                            sold = ?,
                            comments = ?,
                            score = ?,
                            open_hours = ?,
                            update_time = ?
                        where id = ?
                        """,
                shop.getName(),
                shop.getTypeId(),
                shop.getImages(),
                shop.getArea(),
                shop.getAddress(),
                shop.getX(),
                shop.getY(),
                shop.getAvgPrice(),
                shop.getSold(),
                shop.getComments(),
                shop.getScore(),
                shop.getOpenHours(),
                LocalDateTime.now(),
                shop.getId()
        );
    }

    /* 4. 行映射器：ResultSet → Shop */
    private static class ShopRowMapper implements RowMapper<Shop> {
        @Override
        public Shop mapRow(ResultSet rs, int rowNum) throws SQLException {
            Shop shop = new Shop();
            shop.setId(rs.getLong("id"));
            shop.setName(rs.getString("name"));
            shop.setTypeId(rs.getLong("type_id"));
            shop.setImages(rs.getString("images"));
            shop.setArea(rs.getString("area"));
            shop.setAddress(rs.getString("address"));
            shop.setX(rs.getDouble("x"));
            shop.setY(rs.getDouble("y"));
            shop.setAvgPrice(rs.getObject("avg_price", Long.class));
            shop.setSold(rs.getInt("sold"));
            shop.setComments(rs.getInt("comments"));
            shop.setScore(rs.getInt("score"));
            shop.setOpenHours(rs.getString("open_hours"));
            shop.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
            shop.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
            return shop;
        }

        private LocalDateTime toLocalDateTime(Timestamp timestamp) {
            return timestamp == null ? null : timestamp.toLocalDateTime();
        }
    }
}
