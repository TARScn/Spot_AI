package com.tars.spotai.repository;

import com.tars.spotai.utils.ShardUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class AiToolCallLogRepository {
    private final JdbcTemplate jdbcTemplate;

    public AiToolCallLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(ToolCallLogRecord record) {
        jdbcTemplate.update(
                """
                        insert into %s
                            (id, user_id, session_id, tool_name, risk_level, target_type, target_id,
                             tool_input, tool_output, status, confirm_required, error_message, confirm_token)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.formatted(ShardUtils.aiToolCallLogTable(record.userId())),
                record.id(),
                record.userId(),
                record.sessionId(),
                record.toolName(),
                record.riskLevel(),
                record.targetType(),
                record.targetId(),
                record.toolInputJson(),
                record.toolOutputJson(),
                record.status(),
                record.confirmRequired() ? 1 : 0,
                record.errorMessage(),
                record.confirmToken()
        );
    }

    public ToolCallLogRecord findByConfirmToken(String confirmToken) {
        return findByUserIdAndConfirmToken(0L, confirmToken);
    }

    public ToolCallLogRecord findByUserIdAndConfirmToken(Long userId, String confirmToken) {
        if (userId == null) {
            return null;
        }
        if (!StringUtils.hasText(confirmToken)) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "select id, user_id, session_id, tool_name, risk_level, target_type, target_id, " +
                    "tool_input, tool_output, status, confirm_required, error_message, confirm_token " +
                    "from %s where confirm_token = ? and status = 'pending' limit 1"
                            .formatted(ShardUtils.aiToolCallLogTable(userId)),
                    toolCallLogRowMapper(),
                    confirmToken);
        } catch (Exception ignored) {
            return null;
        }
    }

    public int updateStatusAndOutput(Long id, Long userId, String status, String toolOutput, String errorMessage) {
        return jdbcTemplate.update(
                "update %s set status = ?, tool_output = ?, error_message = ? where id = ? and status = 'pending'"
                        .formatted(ShardUtils.aiToolCallLogTable(userId)),
                status,
                toolOutput == null ? "" : toolOutput,
                errorMessage,
                id);
    }

    private RowMapper<ToolCallLogRecord> toolCallLogRowMapper() {
        return (rs, rowNum) -> {
            boolean confirmRequired = rs.getInt("confirm_required") == 1;
            String confirmToken = rs.getString("confirm_token");
            return new ToolCallLogRecord(
                    rs.getLong("id"),
                    rs.getLong("user_id"),
                    rs.getString("session_id"),
                    rs.getString("tool_name"),
                    rs.getString("risk_level"),
                    rs.getString("target_type"),
                    rs.getObject("target_id", Long.class),
                    rs.getString("tool_input"),
                    rs.getString("tool_output"),
                    rs.getString("status"),
                    confirmRequired,
                    rs.getString("error_message"),
                    confirmToken
            );
        };
    }

    public record ToolCallLogRecord(Long id,
                                    Long userId,
                                    String sessionId,
                                    String toolName,
                                    String riskLevel,
                                    String targetType,
                                    Long targetId,
                                    String toolInputJson,
                                    String toolOutputJson,
                                    String status,
                                    boolean confirmRequired,
                                    String errorMessage,
                                    String confirmToken) {
    }
}
