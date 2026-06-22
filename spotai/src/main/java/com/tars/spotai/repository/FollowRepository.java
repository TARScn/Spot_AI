package com.tars.spotai.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class FollowRepository {
    private final JdbcTemplate jdbcTemplate;

    public FollowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int save(Long id, Long userId, Long followUserId) {
        return jdbcTemplate.update(
                """
                        insert into tb_follow (id, user_id, follow_user_id, create_time)
                        values (?, ?, ?, ?)
                        """,
                id,
                userId,
                followUserId,
                LocalDateTime.now()
        );
    }

    public int delete(Long userId, Long followUserId) {
        return jdbcTemplate.update(
                "delete from tb_follow where user_id = ? and follow_user_id = ?",
                userId,
                followUserId
        );
    }

    public boolean exists(Long userId, Long followUserId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(1) from tb_follow where user_id = ? and follow_user_id = ?",
                Integer.class,
                userId,
                followUserId
        );
        return count != null && count > 0;
    }

    public List<Long> findFollowUserIds(Long userId) {
        return jdbcTemplate.queryForList(
                "select follow_user_id from tb_follow where user_id = ?",
                Long.class,
                userId
        );
    }

    public List<Long> findFollowerIds(Long followUserId) {
        return jdbcTemplate.queryForList(
                "select user_id from tb_follow where follow_user_id = ?",
                Long.class,
                followUserId
        );
    }

    public int countFollows(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(1) from tb_follow where user_id = ?",
                Integer.class,
                userId
        );
        return count == null ? 0 : count;
    }

    public int countFans(Long followUserId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(1) from tb_follow where follow_user_id = ?",
                Integer.class,
                followUserId
        );
        return count == null ? 0 : count;
    }
}
