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
 * Repository for persisting and querying user data across sharded database tables.
 * Uses {@link ShardUtils} to route queries to the appropriate table based on the phone number.
 */
@Repository
public class UserRepository {
    /* 1. 依赖注入 */
    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /* 2. 根据手机号查找用户（双表查询：phone-index → user） */
    public User findByPhone(String phone) {
        String phoneTable = ShardUtils.userPhoneTable(phone);
        String userTable = ShardUtils.userTable(phone);
        /* 2.1 在 phone-index 表中查询 user_id */
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
        /* 2.2 在 user 表中查询完整用户信息 */
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

    public User findById(Long userId) {
        if (userId == null) {
            return null;
        }
        for (String userTable : List.of("tb_user_0", "tb_user_1")) {
            try {
                return jdbcTemplate.queryForObject(
                        "select id, phone, password, nick_name, icon, create_time, update_time from " + userTable + " where id = ?",
                        new UserRowMapper(),
                        userId
                );
            } catch (EmptyResultDataAccessException ignored) {
                // Continue scanning the next user shard.
            }
        }
        return null;
    }

    /* 3. 事务性保存用户及其手机号索引 */
    @Transactional
    public void saveUserWithPhone(User user, Long userPhoneId) {
        String userTable = ShardUtils.userTable(user.getPhone());
        String phoneTable = ShardUtils.userPhoneTable(user.getPhone());
        /* 3.1 插入用户表 */
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
        /* 3.2 插入手机号索引表 */
        jdbcTemplate.update(
                "insert into " + phoneTable + " (id, user_id, phone, create_time, update_time) values (?, ?, ?, ?, ?)",
                userPhoneId,
                user.getId(),
                user.getPhone(),
                user.getCreateTime(),
                user.getUpdateTime()
        );
    }

    /* 4. 行映射器：ResultSet → User */
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
