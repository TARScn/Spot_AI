package com.tars.spotai.repository;

import com.tars.spotai.entity.User;
import com.tars.spotai.utils.ShardUtils;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for sharded user tables and email index tables.
 */
@Repository
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public User findByEmail(String email) {
        String emailTable = ShardUtils.userEmailTable(email);
        String userTable = ShardUtils.userTable(email);
        Long userId;
        try {
            userId = jdbcTemplate.queryForObject(
                    "select user_id from " + emailTable + " where email = ? limit 1",
                    Long.class,
                    email
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
        if (userId == null) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "select id, email, password, nick_name, icon, create_time, update_time from " + userTable + " where id = ?",
                    new UserRowMapper(),
                    userId
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public User findById(Long userId) {
        if (userId == null) {
            return null;
        }
        for (String userTable : List.of("tb_user_0", "tb_user_1")) {
            try {
                return jdbcTemplate.queryForObject(
                        "select id, email, password, nick_name, icon, create_time, update_time from " + userTable + " where id = ?",
                        new UserRowMapper(),
                        userId
                );
            } catch (EmptyResultDataAccessException ignored) {
                // Continue scanning the next user shard.
            }
        }
        return null;
    }

    @Transactional
    public void saveUserWithEmail(User user, Long userEmailId) {
        String userTable = ShardUtils.userTable(user.getEmail());
        String emailTable = ShardUtils.userEmailTable(user.getEmail());
        jdbcTemplate.update(
                "insert into " + userTable + " (id, email, password, nick_name, icon, create_time, update_time) values (?, ?, ?, ?, ?, ?, ?)",
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                user.getNickName(),
                user.getIcon(),
                user.getCreateTime(),
                user.getUpdateTime()
        );
        jdbcTemplate.update(
                "insert into " + emailTable + " (id, user_id, email, create_time, update_time) values (?, ?, ?, ?, ?)",
                userEmailId,
                user.getId(),
                user.getEmail(),
                user.getCreateTime(),
                user.getUpdateTime()
        );
    }

    private static class UserRowMapper implements RowMapper<User> {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User user = new User();
            user.setId(rs.getLong("id"));
            user.setEmail(rs.getString("email"));
            user.setPassword(rs.getString("password"));
            user.setNickName(rs.getString("nick_name"));
            user.setIcon(rs.getString("icon"));
            LocalDateTime createTime = rs.getTimestamp("create_time") == null ? null : rs.getTimestamp("create_time").toLocalDateTime();
            LocalDateTime updateTime = rs.getTimestamp("update_time") == null ? null : rs.getTimestamp("update_time").toLocalDateTime();
            user.setCreateTime(createTime);
            user.setUpdateTime(updateTime);
            return user;
        }
    }
}
