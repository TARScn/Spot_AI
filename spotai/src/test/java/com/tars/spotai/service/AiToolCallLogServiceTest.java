package com.tars.spotai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.repository.AiToolCallLogRepository;
import com.tars.spotai.utils.RedisIdWorker;
import com.tars.spotai.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiToolCallLogServiceTest {
    private final AiToolCallLogRepository repository = mock(AiToolCallLogRepository.class);
    private final RedisIdWorker redisIdWorker = mock(RedisIdWorker.class);
    private final AiToolCallLogService service = new AiToolCallLogService(repository, redisIdWorker, new ObjectMapper());

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void writesToolCallLogForLoggedInUser() {
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));
        when(redisIdWorker.nextId("ai_tool_call_log")).thenReturn(3001L);

        service.log(new AiToolCallLogger.ToolCallLogCommand(
                "recommendShops",
                "low",
                "shop",
               6L,
                Map.of("minPrice", 40, "maxPrice", 60),
                "[{\"id\":6,\"name\":\"Budget Shop\"}]",
                "success",
                null,
                null));

        verify(repository).save(argThat(record ->
                record.id().equals(3001L)
                        && record.userId().equals(99L)
                        && record.sessionId().equals("user:99:default")
                        && record.toolName().equals("recommendShops")
                        && record.riskLevel().equals("low")
                        && record.targetType().equals("shop")
                        && record.targetId().equals(6L)
                        && record.toolInputJson().contains("\"minPrice\":40")
                        && record.toolOutputJson().contains("\"id\":6")
                        && record.status().equals("success")
                        && !record.confirmRequired()));
    }

    @Test
    void skipsToolCallLogForAnonymousUser() {
        service.log(new AiToolCallLogger.ToolCallLogCommand(
                "recommendShops",
                "low",
                "shop",
               6L,
                Map.of("minPrice", 40),
                "[]",
                "success",
                null,
                null));

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
