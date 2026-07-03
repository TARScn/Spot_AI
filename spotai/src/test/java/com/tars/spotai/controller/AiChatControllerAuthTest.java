package com.tars.spotai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.config.AuthProperties;
import com.tars.spotai.dto.AiChatMessageDTO;
import com.tars.spotai.dto.AiChatResponseDTO;
import com.tars.spotai.interceptor.LoginInterceptor;
import com.tars.spotai.interceptor.RefreshTokenInterceptor;
import com.tars.spotai.service.AiChatService;
import com.tars.spotai.utils.RedisConstants;
import com.tars.spotai.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiChatControllerAuthTest {
    private final AiChatService service = mock(AiChatService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AiChatController(service))
            .addMappedInterceptors(
                    new String[]{"/ai/conversations/**", "/ai/memories/**"},
                    new LoginInterceptor(new ObjectMapper()))
            .build();

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void chatEndpointAllowsAnonymousAccess() throws Exception {
        when(service.chat(any())).thenReturn(AiChatResponseDTO.of(
                "ok",
                "CHAT",
                "SHOP_GUIDE",
                false,
                List.of(),
                List.of("recommendShops")));

        mockMvc.perform(post("/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.answer").value("ok"))
                .andExpect(jsonPath("$.data.agentRoute").value("SHOP_GUIDE"))
                .andExpect(jsonPath("$.data.usedTools[0]").value("recommendShops"));

        verify(service).chat(any());
    }

    @Test
    void confirmToolEndpointIsCallable() throws Exception {
        when(service.confirmTool("test-token", true)).thenReturn("{\"status\":\"SUCCESS\",\"orderId\":1}");

        mockMvc.perform(post("/ai/tool/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmToken\":\"test-token\",\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("{\"status\":\"SUCCESS\",\"orderId\":1}"));

        verify(service).confirmTool("test-token", true);
    }

    @Test
    void recentConversationsRequireLogin() throws Exception {
        mockMvc.perform(get("/ai/conversations/recent"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(service);
    }

    @Test
    void clearConversationRequiresLogin() throws Exception {
        mockMvc.perform(delete("/ai/conversations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(service);
    }

    @Test
    void memoriesRequireLogin() throws Exception {
        mockMvc.perform(get("/ai/memories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(service);
    }

    @Test
    void clearMemoriesRequiresLogin() throws Exception {
        mockMvc.perform(delete("/ai/memories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(service);
    }

    @Test
    void deleteMemoryRequiresLogin() throws Exception {
        mockMvc.perform(delete("/ai/memories/dining.preference.area"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(service);
    }

    @Test
    void protectedAiEndpointAllowsValidTokenAndRefreshesTtl() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        AuthProperties authProperties = new AuthProperties();
        authProperties.setTokenTtlMinutes(45);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(RedisConstants.LOGIN_TOKEN_KEY + "abc"))
                .thenReturn(Map.of("id", "99", "nickName", "tester", "icon", ""));
        AiChatMessageDTO message = new AiChatMessageDTO();
        message.setRole("assistant");
        message.setContent("hello again");
        message.setUsedTools(List.of("recommendShops"));
        when(service.recentMessages(20)).thenReturn(List.of(message));
        MockMvc authenticatedMockMvc = MockMvcBuilders
                .standaloneSetup(new AiChatController(service))
                .addInterceptors(new RefreshTokenInterceptor(redisTemplate, authProperties))
                .addMappedInterceptors(
                        new String[]{"/ai/conversations/**", "/ai/memories/**"},
                        new LoginInterceptor(new ObjectMapper()))
                .build();

        authenticatedMockMvc.perform(get("/ai/conversations/recent")
                        .header("Authorization", "Bearer abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].content").value("hello again"))
                .andExpect(jsonPath("$.data[0].usedTools[0]").value("recommendShops"));

        verify(service).recentMessages(20);
        verify(redisTemplate).expire(eq(RedisConstants.LOGIN_TOKEN_KEY + "abc"), eq(45L), eq(TimeUnit.MINUTES));
    }
}
