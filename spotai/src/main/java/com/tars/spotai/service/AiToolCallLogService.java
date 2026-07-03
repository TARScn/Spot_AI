package com.tars.spotai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.repository.AiToolCallLogRepository;
import com.tars.spotai.utils.RedisIdWorker;
import com.tars.spotai.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
public class AiToolCallLogService implements AiToolCallLogger {
    private final AiToolCallLogRepository repository;
    private final RedisIdWorker redisIdWorker;
    private final ObjectMapper objectMapper;

    public AiToolCallLogService(AiToolCallLogRepository repository, RedisIdWorker redisIdWorker, ObjectMapper objectMapper) {
        this.repository = repository;
        this.redisIdWorker = redisIdWorker;
        this.objectMapper = objectMapper;
    }

    @Override
    public void log(ToolCallLogCommand command) {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null || command == null || !StringUtils.hasText(command.toolName())) {
            return;
        }
        try {
            repository.save(new AiToolCallLogRepository.ToolCallLogRecord(
                    redisIdWorker.nextId("ai_tool_call_log"),
                    user.getId(),
                    conversationKey(user.getId()),
                    command.toolName(),
                    safeText(command.riskLevel(), "low", 20),
                    safeText(command.targetType(), null, 50),
                    command.targetId(),
                    toJson(command.input()),
                    outputJson(command.output()),
                    safeText(command.status(), "success", 20),
                   isConfirmRequired(command.riskLevel()),
                    truncate(command.errorMessage(), 1024),
                    command.confirmToken()
            ));
        } catch (Exception ignored) {
        }
    }

    private boolean isConfirmRequired(String riskLevel) {
        return "medium".equalsIgnoreCase(riskLevel) || "high".equalsIgnoreCase(riskLevel);
    }

    private String outputJson(String output) {
        if (!StringUtils.hasText(output)) {
            return "{}";
        }
        try {
            objectMapper.readTree(output);
            return output;
        } catch (Exception ignored) {
            return toJson(Map.of("raw", output));
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ignored) {
            return "{\"raw\":\"unserializable\"}";
        }
    }

    private String safeText(String value, String fallback, int maxLength) {
        String text = StringUtils.hasText(value) ? value : fallback;
        return truncate(text, maxLength);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String conversationKey(Long userId) {
        return "user:" + userId + ":default";
    }
}
