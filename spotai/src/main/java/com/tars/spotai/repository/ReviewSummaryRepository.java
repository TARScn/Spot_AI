package com.tars.spotai.repository;

import com.tars.spotai.entity.ReviewSummary;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ReviewSummaryRepository {
    private final JdbcTemplate jdbcTemplate;

    public ReviewSummaryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ReviewSummary findByShopId(Long shopId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select shop_id, status, summary, highlights_json, weaknesses_json, scenes_json,
                                   review_count, version, generated_at, expire_at, create_time, update_time
                            from tb_review_summary
                            where shop_id = ?
                            """,
                    rowMapper(),
                    shopId
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public void upsert(ReviewSummary summary) {
        jdbcTemplate.update(
                """
                        insert into tb_review_summary
                            (shop_id, status, summary, highlights_json, weaknesses_json, scenes_json,
                             review_count, version, generated_at, expire_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        on duplicate key update
                            status = values(status),
                            summary = values(summary),
                            highlights_json = values(highlights_json),
                            weaknesses_json = values(weaknesses_json),
                            scenes_json = values(scenes_json),
                            review_count = values(review_count),
                            version = values(version),
                            generated_at = values(generated_at),
                            expire_at = values(expire_at)
                        """,
                summary.getShopId(),
                summary.getStatus(),
                summary.getSummary(),
                summary.getHighlightsJson(),
                summary.getWeaknessesJson(),
                summary.getScenesJson(),
                summary.getReviewCount(),
                summary.getVersion(),
                summary.getGeneratedAt(),
                summary.getExpireAt()
        );
    }

    public void markStale(Long shopId) {
        jdbcTemplate.update(
                """
                        insert into tb_review_summary (shop_id, status, version)
                        values (?, 'STALE', 1)
                        on duplicate key update
                            status = 'STALE',
                            version = version + 1
                        """,
                shopId
        );
    }

    public void markBuilding(Long shopId) {
        jdbcTemplate.update(
                """
                        insert into tb_review_summary (shop_id, status, version)
                        values (?, 'BUILDING', 1)
                        on duplicate key update status = 'BUILDING'
                        """,
                shopId
        );
    }

    public List<Long> findShopIdsNeedingRefresh(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbcTemplate.queryForList(
                """
                        select shop_id
                        from tb_review_summary
                        where status = 'STALE'
                           or (status = 'BUILDING' and update_time < date_sub(now(), interval 10 minute))
                        order by update_time asc
                        limit ?
                        """,
                Long.class,
                safeLimit
        );
    }

    public List<Long> findShopIdsWithoutSummary(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbcTemplate.queryForList(
                """
                        select s.id
                        from tb_shop s
                        left join tb_review_summary rs on rs.shop_id = s.id
                        where rs.shop_id is null
                        order by s.update_time desc, s.id desc
                        limit ?
                        """,
                Long.class,
                safeLimit
        );
    }

    private RowMapper<ReviewSummary> rowMapper() {
        return (rs, rowNum) -> {
            ReviewSummary summary = new ReviewSummary();
            summary.setShopId(rs.getLong("shop_id"));
            summary.setStatus(rs.getString("status"));
            summary.setSummary(rs.getString("summary"));
            summary.setHighlightsJson(rs.getString("highlights_json"));
            summary.setWeaknessesJson(rs.getString("weaknesses_json"));
            summary.setScenesJson(rs.getString("scenes_json"));
            summary.setReviewCount(rs.getObject("review_count", Integer.class));
            summary.setVersion(rs.getObject("version", Integer.class));
            summary.setGeneratedAt(toLocalDateTime(rs.getTimestamp("generated_at")));
            summary.setExpireAt(toLocalDateTime(rs.getTimestamp("expire_at")));
            summary.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
            summary.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
            return summary;
        };
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
