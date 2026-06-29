package com.tars.spotai.repository;

import com.tars.spotai.entity.Review;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ReviewRepository {
    private final JdbcTemplate jdbcTemplate;

    public ReviewRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Review> findByShopId(Long shopId, int current, int pageSize) {
        int page = Math.max(current, 1);
        int offset = (page - 1) * pageSize;
        return jdbcTemplate.query(
                """
                        select id, shop_id, user_id, order_id, score, content, status, liked, images_count, create_time, update_time
                        from tb_review
                        where shop_id = ? and status = 0
                        order by create_time desc, id desc
                        limit ?, ?
                        """,
                new ReviewRowMapper(),
                shopId,
                offset,
                pageSize
        );
    }

    public List<Review> findByUserId(Long userId, int current, int pageSize) {
        int page = Math.max(current, 1);
        int offset = (page - 1) * pageSize;
        return jdbcTemplate.query(
                """
                        select id, shop_id, user_id, order_id, score, content, status, liked, images_count, create_time, update_time
                        from tb_review
                        where user_id = ? and status = 0
                        order by create_time desc, id desc
                        limit ?, ?
                        """,
                new ReviewRowMapper(),
                userId,
                offset,
                pageSize
        );
    }

    public int countByUserId(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(1) from tb_review where user_id = ? and status = 0",
                Integer.class,
                userId
        );
        return count == null ? 0 : count;
    }

    public int countActiveWithContentByShopId(Long shopId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(1) from tb_review where shop_id = ? and status = 0 and content is not null and trim(content) <> ''",
                Integer.class,
                shopId
        );
        return count == null ? 0 : count;
    }

    public List<Review> findActiveWithContentByShopId(Long shopId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 2000));
        return jdbcTemplate.query(
                """
                        select id, shop_id, user_id, order_id, score, content, status, liked,
                               images_count, create_time, update_time
                        from tb_review
                        where shop_id = ? and status = 0 and content is not null and trim(content) <> ''
                        order by create_time desc, id desc
                        limit ?
                        """,
                new ReviewRowMapper(),
                shopId,
                safeLimit
        );
    }

    public Map<Long, List<String>> findImagesByReviewIds(List<Long> reviewIds) {
        if (reviewIds == null || reviewIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", reviewIds.stream().map(id -> "?").toList());
        Object[] args = reviewIds.toArray();
        Map<Long, List<String>> imageMap = new LinkedHashMap<>();
        jdbcTemplate.query(
                """
                        select review_id, image_url
                        from tb_review_image
                        where review_id in (%s)
                        order by review_id, sort asc, id asc
                        """.formatted(placeholders),
                rs -> {
                    Long reviewId = rs.getLong("review_id");
                    imageMap.computeIfAbsent(reviewId, key -> new java.util.ArrayList<>())
                            .add(rs.getString("image_url"));
                },
                args
        );
        return new HashMap<>(imageMap);
    }

    public void save(Review review) {
        jdbcTemplate.update(
                """
                        insert into tb_review
                            (id, shop_id, user_id, order_id, score, content, status, liked,
                             images_count, create_time, update_time)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                review.getId(),
                review.getShopId(),
                review.getUserId(),
                review.getOrderId(),
                review.getScore(),
                review.getContent(),
                review.getStatus(),
                review.getLiked(),
                review.getImagesCount(),
                review.getCreateTime(),
                review.getUpdateTime()
        );
    }

    public Review findById(Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, shop_id, user_id, order_id, score, content, status, liked, images_count, create_time, update_time
                            from tb_review
                            where id = ?
                            """,
                    new ReviewRowMapper(),
                    id
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public void saveImages(Long reviewId, List<Long> imageIds, List<String> imageUrls) {
        if (reviewId == null || imageUrls == null || imageUrls.isEmpty()) {
            return;
        }
        for (int i = 0; i < imageUrls.size(); i++) {
            jdbcTemplate.update(
                    """
                            insert into tb_review_image (id, review_id, image_url, sort, create_time)
                            values (?, ?, ?, ?, ?)
                            """,
                    imageIds.get(i),
                    reviewId,
                    imageUrls.get(i),
                    i,
                    LocalDateTime.now()
            );
        }
    }

    public int markDeletedByIdAndUserId(Long id, Long userId) {
        return jdbcTemplate.update(
                "update tb_review set status = 2 where id = ? and user_id = ? and status = 0",
                id,
                userId
        );
    }

    private static class ReviewRowMapper implements RowMapper<Review> {
        @Override
        public Review mapRow(ResultSet rs, int rowNum) throws SQLException {
            Review review = new Review();
            review.setId(rs.getLong("id"));
            review.setShopId(rs.getLong("shop_id"));
            review.setUserId(rs.getLong("user_id"));
            review.setOrderId(rs.getObject("order_id", Long.class));
            review.setScore(rs.getObject("score", Integer.class));
            review.setContent(rs.getString("content"));
            review.setStatus(rs.getObject("status", Integer.class));
            review.setLiked(rs.getObject("liked", Integer.class));
            review.setImagesCount(rs.getObject("images_count", Integer.class));
            review.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
            review.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
            return review;
        }

        private LocalDateTime toLocalDateTime(Timestamp timestamp) {
            return timestamp == null ? null : timestamp.toLocalDateTime();
        }
    }
}
