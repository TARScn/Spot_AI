package com.tars.spotai.repository;

import com.tars.spotai.entity.AiUserMemory;
import com.tars.spotai.utils.ShardUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class AiUserMemoryRepository {
    private final JdbcTemplate jdbcTemplate;

    public AiUserMemoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AiUserMemory> findActiveByUserId(Long userId) {
        return jdbcTemplate.query(
                """
                        select id, user_id, memory_key, memory_type, memory_json, confidence,
                               source_message_id, source_agent, status, create_time, update_time
                        from %s
                        where user_id = ? and status = 1
                        order by update_time desc
                        limit 30
                        """.formatted(ShardUtils.aiUserMemoryTable(userId)),
                new AiUserMemoryRowMapper(),
                userId
        );
    }

    public void upsert(Long id, Long userId, String memoryKey, String memoryType, String memoryJson,
                       double confidence, Long sourceMessageId, String sourceAgent) {
        jdbcTemplate.update(
                """
                        insert into %s
                            (id, user_id, memory_key, memory_type, memory_json, confidence, source_message_id, source_agent, status)
                        values (?, ?, ?, ?, ?, ?, ?, ?, 1)
                        on duplicate key update
                            memory_type = values(memory_type),
                            memory_json = values(memory_json),
                            confidence = values(confidence),
                            source_message_id = values(source_message_id),
                            source_agent = values(source_agent),
                            status = 1
                        """.formatted(ShardUtils.aiUserMemoryTable(userId)),
                id,
                userId,
                memoryKey,
                memoryType,
                memoryJson,
                confidence,
                sourceMessageId,
                sourceAgent
        );
    }

    public void markDeleted(Long userId, String memoryKey) {
        jdbcTemplate.update(
                "update " + ShardUtils.aiUserMemoryTable(userId) + " set status = 2 where user_id = ? and memory_key = ?",
                userId,
                memoryKey
        );
    }

    private static class AiUserMemoryRowMapper implements RowMapper<AiUserMemory> {
        @Override
        public AiUserMemory mapRow(ResultSet rs, int rowNum) throws SQLException {
            AiUserMemory memory = new AiUserMemory();
            memory.setId(rs.getLong("id"));
            memory.setUserId(rs.getLong("user_id"));
            memory.setMemoryKey(rs.getString("memory_key"));
            memory.setMemoryType(rs.getString("memory_type"));
            memory.setMemoryJson(rs.getString("memory_json"));
            memory.setConfidence(rs.getObject("confidence", Double.class));
            memory.setSourceMessageId(rs.getObject("source_message_id", Long.class));
            memory.setSourceAgent(rs.getString("source_agent"));
            memory.setStatus(rs.getObject("status", Integer.class));
            memory.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
            memory.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
            return memory;
        }

        private java.time.LocalDateTime toLocalDateTime(Timestamp timestamp) {
            return timestamp == null ? null : timestamp.toLocalDateTime();
        }
    }
}
