package com.tars.spotai.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.dto.AiChatMessageDTO;
import com.tars.spotai.utils.ShardUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Repository
public class AiConversationRepository {
    private static final TypeReference<Map<String, List<String>>> METADATA_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AiConversationRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(Long id, Long userId, String sessionId, String role, String content, String messageType, String modelName) {
        save(id, userId, sessionId, role, content, messageType, modelName, List.of());
    }

    public void save(Long id, Long userId, String sessionId, String role, String content,
                     String messageType, String modelName, List<String> usedTools) {
        String metadata = toMetadataJson(usedTools);
        try {
            jdbcTemplate.update(
                    """
                            insert into %s
                                (id, user_id, session_id, role, content, message_type, model_name, metadata)
                            values (?, ?, ?, ?, ?, ?, ?, ?)
                            """.formatted(ShardUtils.aiConversationTable(userId)),
                    id,
                    userId,
                    sessionId,
                    role,
                    content,
                    messageType,
                    modelName,
                    metadata
            );
        } catch (DataAccessException ignored) {
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
    }

    public List<AiChatMessageDTO> findRecent(Long userId, String sessionId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 30));
        try {
            return findRecentWithMetadata(userId, sessionId, safeLimit);
        } catch (DataAccessException ignored) {
            return findRecentWithoutMetadata(userId, sessionId, safeLimit);
        }
    }

    private List<AiChatMessageDTO> findRecentWithMetadata(Long userId, String sessionId, int safeLimit) {
        List<AiChatMessageDTO> messages = jdbcTemplate.query(
                """
                        select role, content, metadata
                        from %s
                        where user_id = ? and session_id = ?
                        order by create_time desc, id desc
                        limit ?
                        """.formatted(ShardUtils.aiConversationTable(userId)),
                (rs, rowNum) -> {
                    AiChatMessageDTO dto = new AiChatMessageDTO();
                    dto.setRole(rs.getString("role"));
                    dto.setContent(rs.getString("content"));
                    dto.setUsedTools(parseUsedTools(rs.getString("metadata")));
                    return dto;
                },
                userId,
                sessionId,
                safeLimit
        );
        return reverse(messages);
    }

    private List<AiChatMessageDTO> findRecentWithoutMetadata(Long userId, String sessionId, int safeLimit) {
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
                    dto.setUsedTools(List.of());
                    return dto;
                },
                userId,
                sessionId,
                safeLimit
        );
        return reverse(messages);
    }

    private List<AiChatMessageDTO> reverse(List<AiChatMessageDTO> messages) {
        List<AiChatMessageDTO> ordered = new ArrayList<>(messages);
        Collections.reverse(ordered);
        return ordered;
    }

    private String toMetadataJson(List<String> usedTools) {
        try {
            return objectMapper.writeValueAsString(Map.of("usedTools", usedTools == null ? List.of() : usedTools));
        } catch (Exception ignored) {
            return "{\"usedTools\":[]}";
        }
    }

    private List<String> parseUsedTools(String metadata) {
        if (!StringUtils.hasText(metadata)) {
            return List.of();
        }
        try {
            Map<String, List<String>> value = objectMapper.readValue(metadata, METADATA_TYPE);
            List<String> usedTools = value.get("usedTools");
            return usedTools == null ? List.of() : usedTools;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public int deleteBySession(Long userId, String sessionId) {
        return jdbcTemplate.update(
                """
                        delete from %s
                        where user_id = ? and session_id = ?
                        """.formatted(ShardUtils.aiConversationTable(userId)),
                userId,
                sessionId
        );
    }
}
