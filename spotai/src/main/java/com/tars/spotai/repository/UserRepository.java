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

/**
 * Repository for persisting and querying user data across sharded database tables.
 * Uses {@link ShardUtils} to route queries to the appropriate table based on the phone number.
 */
@Repository
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Looks up a user by phone number. First queries the phone-index table to find the user ID,
     * then queries the user table. Returns null if the phone is not found.
     */
    public User findByPhone(String phone) {
        String phoneTable = ShardUtils.userPhoneTable(phone);
        String userTable = ShardUtils.userTable(phone);
        Long userId;
        try {
            userId = jdbcTemplate.queryForObject(
                    "select user_id from " + phoneTable + " where phone = ? limit 1",
                    Long.class,
                    phone
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
        if (userId == null) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "select id, phone, password, nick_name, icon, create_time, update_time from " + userTable + " where id = ?",
                    new UserRowMapper(),
                    userId
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * Atomically saves a new user and its phone-index record in a single transaction.
     */
    @Transactional
    public void saveUserWithPhone(User user, Long userPhoneId) {
        String userTable = ShardUtils.userTable(user.getPhone());
        String phoneTable = ShardUtils.userPhoneTable(user.getPhone());
        jdbcTemplate.update(
                "insert into " + userTable + " (id, phone, password, nick_name, icon, create_time, update_time) values (?, ?, ?, ?, ?, ?, ?)",
                user.getId(),
                user.getPhone(),
                user.getPassword(),
                user.getNickName(),
                user.getIcon(),
                user.getCreateTime(),
                user.getUpdateTime()
        );
        jdbcTemplate.update(
                "insert into " + phoneTable + " (id, user_id, phone, create_time, update_time) values (?, ?, ?, ?, ?)",
                userPhoneId,
                user.getId(),
                user.getPhone(),
                user.getCreateTime(),
                user.getUpdateTime()
        );
    }

    /**
     * Maps a JDBC result set row to a {@link User} entity.
     */
    private static class UserRowMapper implements RowMapper<User> {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User user = new User();
            user.setId(rs.getLong("id"));
            user.setPhone(rs.getString("phone"));
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
