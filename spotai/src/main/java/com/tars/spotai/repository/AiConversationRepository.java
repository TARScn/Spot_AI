package com.tars.spotai.repository;

import com.tars.spotai.dto.AiChatMessageDTO;
import com.tars.spotai.utils.ShardUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
public class AiConversationRepository {
    private final JdbcTemplate jdbcTemplate;

    public AiConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(Long id, Long userId, String sessionId, String role, String content, String messageType, String modelName) {
        jdbcTemplate.update(
                """
                        insert into %s
                            (id, user_id, session_id, role, content, message_type, model_name)
                        values (?, ?, ?, ?, ?, ?, ?)
                        """.formatted(ShardUtils.aiConversationTable(userId)),
                id,
                userId,
                sessionId,
                role,
                content,
                messageType,
                modelName
        );
    }

    public List<AiChatMessageDTO> findRecent(Long userId, String sessionId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 30));
        List<AiChatMessageDTO> messages = jdbcTemplate.query(
                """
                        select role, content
                        from %s
                        where user_id = ? and session_id = ?
                        order by create_time desc, id desc
                        limit ?
                        """.formatted(ShardUtils.aiConversationTable(userId)),
                (rs, rowNum) -> {
                    AiChatMessageDTO dto = new AiChatMessageDTO();
                    dto.setRole(rs.getString("role"));
                    dto.setContent(rs.getString("content"));
                    return dto;
                },
                userId,
                sessionId,
                safeLimit
        );
        List<AiChatMessageDTO> ordered = new ArrayList<>(messages);
        Collections.reverse(ordered);
        return ordered;
    }
}
