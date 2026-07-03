package com.tars.spotai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.config.AiChatProperties;
import com.tars.spotai.dto.AiChatMessageDTO;
import com.tars.spotai.dto.AiChatRequestDTO;
import com.tars.spotai.dto.PreferenceMemoryCandidateDTO;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.entity.AiUserMemory;
import com.tars.spotai.repository.AiConversationRepository;
import com.tars.spotai.utils.RedisIdWorker;
import com.tars.spotai.utils.UserHolder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiChatServiceTest {

    @Test
    void buildsPromptWithHistoryAndReturnsAnswer() {
        CapturingChatModel chatModel = new CapturingChatModel("可以先查看店铺评价和优惠。");
        TestDependencies deps = new TestDependencies(chatModel);
        SpringAiChatService service = deps.service();
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setShopId(12L);
        request.setRoute("agent");
        request.setMessage("这家店适合聚餐吗？");
        AiChatMessageDTO history = new AiChatMessageDTO();
        history.setRole("assistant");
        history.setContent("我可以帮你分析评价。");
        request.setHistory(List.of(history));

        var response = service.chat(request);

        assertThat(response.getAnswer()).isEqualTo("可以先查看店铺评价和优惠。");
        assertThat(response.getRoute()).isEqualTo("CHAT");
        assertThat(response.getAgentRoute()).isEqualTo("SHOP_GUIDE");
        assertThat(chatModel.prompt.getContents()).contains("当前商户ID：12");
        assertThat(chatModel.prompt.getContents()).contains("assistant: 我可以帮你分析评价。");
        assertThat(chatModel.prompt.getContents()).contains("用户问题：这家店适合聚餐吗？");
        verify(deps.conversationRepository, never()).save(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsBlankMessage() {
        SpringAiChatService service = new TestDependencies(new CapturingChatModel("unused")).service();
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage(" ");

        assertThatThrownBy(() -> service.chat(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请输入要咨询的问题");
    }

    @Test
    void couponClaimIntentReturnsConfirmationCardWithoutWaitingForModelToolChoice() {
        CapturingChatModel chatModel = new CapturingChatModel("should not be called");
        TestDependencies deps = new TestDependencies(chatModel);
        when(deps.spotAiChatTools.searchShop("马坡烤肉"))
                .thenReturn("[{\"id\":42,\"name\":\"马坡烤肉\"}]");
        when(deps.spotAiChatTools.queryCoupons(42L))
                .thenReturn("[{\"id\":7,\"title\":\"50元代金券\",\"payValue\":3000,\"actualValue\":5000}]");
        when(deps.spotAiChatTools.claimCoupon(7L))
                .thenReturn("{\"status\":\"CONFIRM_REQUIRED\",\"toolName\":\"claimCoupon\",\"confirmToken\":\"token-7\"}");
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("帮我领取马坡烤肉的券");

        var response = deps.service().chat(request);
        UserHolder.removeUser();

        assertThat(response.getAnswer()).contains("马坡烤肉", "50元代金券", "确定领取");
        assertThat(response.getAgentRoute()).isEqualTo("COUPON");
        assertThat(response.getUsedTools()).containsExactly("searchShop", "queryCoupons", "claimCoupon");
        assertThat(response.getToolConfirmation()).isNotNull();
        assertThat(response.getToolConfirmation().getToolName()).isEqualTo("claimCoupon");
        assertThat(response.getToolConfirmation().getConfirmToken()).isEqualTo("token-7");
        assertThat(chatModel.prompt).isNull();
    }

    @Test
    void loggedInChatAppendsCurrentTurnToShortTermMemory() {
        CapturingChatModel chatModel = new CapturingChatModel("ok");
        TestDependencies deps = new TestDependencies(chatModel);
        when(deps.redisIdWorker.nextId("ai_conversation")).thenReturn(1001L, 1002L);
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("hello");

        deps.service().chat(request);
        UserHolder.removeUser();

        verify(deps.shortTermMemoryService).addUserMessage("user:99:default", "hello");
        verify(deps.shortTermMemoryService).addAssistantMessage("user:99:default", "ok");
    }

    @Test
    void loggedInPromptPrefersShortTermChatMemoryOverPersistedHistory() {
        CapturingChatModel chatModel = new CapturingChatModel("ok");
        TestDependencies deps = new TestDependencies(chatModel);
        when(deps.shortTermMemoryService.get("user:99:default")).thenReturn(List.of(
                message("user", "short-term-user"),
                message("assistant", "short-term-assistant")
        ));
        when(deps.conversationRepository.findRecent(99L, "user:99:default", 24)).thenReturn(List.of(
                message("user", "persisted-user")
        ));
        when(deps.redisIdWorker.nextId("ai_conversation")).thenReturn(1001L, 1002L);
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("hello");

        deps.service().chat(request);
        UserHolder.removeUser();

        assertThat(chatModel.prompt.getContents()).contains("short-term-user");
        assertThat(chatModel.prompt.getContents()).contains("short-term-assistant");
        assertThat(chatModel.prompt.getContents()).doesNotContain("persisted-user");
        verify(deps.conversationRepository, never()).findRecent(eq(99L), eq("user:99:default"), anyInt());
    }

    @Test
    void loggedInPromptFallsBackToPersistedHistoryWhenShortTermMemoryFails() {
        CapturingChatModel chatModel = new CapturingChatModel("ok");
        TestDependencies deps = new TestDependencies(chatModel);
        when(deps.shortTermMemoryService.get("user:99:default")).thenThrow(new IllegalStateException("memory down"));
        when(deps.conversationRepository.findRecent(99L, "user:99:default", 24)).thenReturn(List.of(
                message("user", "persisted-user")
        ));
        when(deps.redisIdWorker.nextId("ai_conversation")).thenReturn(1001L, 1002L);
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("hello");

        deps.service().chat(request);
        UserHolder.removeUser();

        assertThat(chatModel.prompt.getContents()).contains("persisted-user");
        verify(deps.conversationRepository).findRecent(99L, "user:99:default", 24);
    }

    @Test
    void persistsConversationAndExtractedMemoriesForLoggedInUser() {
        CapturingChatModel chatModel = new CapturingChatModel("我会优先考虑安静、人均 80 左右的店。");
        TestDependencies deps = new TestDependencies(chatModel);
        when(deps.redisIdWorker.nextId("ai_conversation")).thenReturn(1001L, 1002L);
        when(deps.redisIdWorker.nextId("ai_user_memory")).thenReturn(2001L);
        PreferenceMemoryCandidateDTO candidate = new PreferenceMemoryCandidateDTO();
        candidate.setMemoryKey("dining.preference.environment");
        candidate.setMemoryType("preference");
        candidate.setValue(java.util.Map.of("likes", List.of("安静")));
        candidate.setConfidence(0.92);
        when(deps.preferenceExtractorAgent.extract(eq(99L), any(), any())).thenReturn(List.of(candidate));
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));

        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("我喜欢安静一点、人均 80 左右的店");
        var response = deps.service().chat(request);
        UserHolder.removeUser();

        assertThat(response.isMemoryUpdated()).isTrue();
        assertThat(response.getMemories()).hasSize(1);
        verify(deps.conversationRepository).findRecent(eq(99L), eq("user:99:default"), eq(24));
        verify(deps.conversationRepository).save(eq(1001L), eq(99L), eq("user:99:default"), eq("user"), eq("我喜欢安静一点、人均 80 左右的店"), eq("text"), eq("deepseek-chat"), eq(List.of()));
        verify(deps.conversationRepository).save(eq(1002L), eq(99L), eq("user:99:default"), eq("assistant"), eq("我会优先考虑安静、人均 80 左右的店。"), eq("text"), eq("deepseek-chat"), eq(List.of()));
        verify(deps.userMemoryStore).put(argThat(command ->
                command.id().equals(2001L)
                        && command.userId().equals(99L)
                        && command.namespace().equals("dining.preference")
                        && command.key().equals("environment")
                        && command.memoryJson().equals("{\"likes\":[\"安静\"]}")
                        && command.sourceMessageId().equals(1001L)
                        && command.sourceAgent().equals("PreferenceExtractorAgent")));
    }

    @Test
    void skipsUnchangedExtractedMemoryWhenConfidenceDoesNotImprove() {
        CapturingChatModel chatModel = new CapturingChatModel("ok");
        TestDependencies deps = new TestDependencies(chatModel);
        AiUserMemory existing = memory("dining.preference.environment", "dining.preference", "{\"likes\":[\"安静\"]}");
        existing.setConfidence(0.95);
        when(deps.userMemoryStore.findActive(99L)).thenReturn(List.of(existing));
        PreferenceMemoryCandidateDTO candidate = new PreferenceMemoryCandidateDTO();
        candidate.setMemoryKey("dining.preference.environment");
        candidate.setMemoryType("preference");
        candidate.setValue(java.util.Map.of("likes", List.of("安静")));
        candidate.setConfidence(0.92);
        when(deps.preferenceExtractorAgent.extract(eq(99L), any(), any())).thenReturn(List.of(candidate));
        when(deps.redisIdWorker.nextId("ai_conversation")).thenReturn(1001L, 1002L);
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("我还是喜欢安静一点");

        var response = deps.service().chat(request);
        UserHolder.removeUser();

        assertThat(response.isMemoryUpdated()).isFalse();
        verify(deps.userMemoryStore, never()).put(any());
    }

    @Test
    void skipsEmptyExtractedMemoryValues() {
        CapturingChatModel chatModel = new CapturingChatModel("ok");
        TestDependencies deps = new TestDependencies(chatModel);
        PreferenceMemoryCandidateDTO emptyLikes = new PreferenceMemoryCandidateDTO();
        emptyLikes.setMemoryKey("dining.preference.environment");
        emptyLikes.setMemoryType("preference");
        emptyLikes.setValue(java.util.Map.of("likes", List.of()));
        emptyLikes.setConfidence(0.9);
        PreferenceMemoryCandidateDTO metadataOnly = new PreferenceMemoryCandidateDTO();
        metadataOnly.setMemoryKey("dining.preference.scene");
        metadataOnly.setMemoryType("preference");
        metadataOnly.setValue(java.util.Map.of("source", "llm"));
        metadataOnly.setConfidence(0.9);
        when(deps.preferenceExtractorAgent.extract(eq(99L), any(), any())).thenReturn(List.of(emptyLikes, metadataOnly));
        when(deps.redisIdWorker.nextId("ai_conversation")).thenReturn(1001L, 1002L);
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("hello");

        var response = deps.service().chat(request);
        UserHolder.removeUser();

        assertThat(response.isMemoryUpdated()).isFalse();
        assertThat(response.getMemories()).isEmpty();
        verify(deps.userMemoryStore, never()).put(any());
    }

    @Test
    void updatesUnchangedExtractedMemoryWhenConfidenceImproves() {
        CapturingChatModel chatModel = new CapturingChatModel("ok");
        TestDependencies deps = new TestDependencies(chatModel);
        AiUserMemory existing = memory("dining.preference.environment", "dining.preference", "{\"likes\":[\"安静\"]}");
        existing.setConfidence(0.72);
        when(deps.userMemoryStore.findActive(99L)).thenReturn(List.of(existing));
        PreferenceMemoryCandidateDTO candidate = new PreferenceMemoryCandidateDTO();
        candidate.setMemoryKey("dining.preference.environment");
        candidate.setMemoryType("preference");
        candidate.setValue(java.util.Map.of("likes", List.of("安静")));
        candidate.setConfidence(0.91);
        when(deps.preferenceExtractorAgent.extract(eq(99L), any(), any())).thenReturn(List.of(candidate));
        when(deps.redisIdWorker.nextId("ai_conversation")).thenReturn(1001L, 1002L);
        when(deps.redisIdWorker.nextId("ai_user_memory")).thenReturn(2001L);
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("我非常确定自己喜欢安静一点");

        var response = deps.service().chat(request);
        UserHolder.removeUser();

        assertThat(response.isMemoryUpdated()).isTrue();
        verify(deps.userMemoryStore).put(argThat(command ->
                command.namespace().equals("dining.preference")
                        && command.key().equals("environment")
                        && command.confidence() == 0.91));
    }

    @Test
    void updatesExtractedMemoryWhenContentChanges() {
        CapturingChatModel chatModel = new CapturingChatModel("ok");
        TestDependencies deps = new TestDependencies(chatModel);
        AiUserMemory existing = memory("dining.preference.area", "dining.preference", "{\"area\":\"高新\"}");
        existing.setConfidence(0.95);
        when(deps.userMemoryStore.findActive(99L)).thenReturn(List.of(existing));
        PreferenceMemoryCandidateDTO candidate = new PreferenceMemoryCandidateDTO();
        candidate.setMemoryKey("dining.preference.area");
        candidate.setMemoryType("preference");
        candidate.setValue(java.util.Map.of("area", "小寨"));
        candidate.setConfidence(0.9);
        when(deps.preferenceExtractorAgent.extract(eq(99L), any(), any())).thenReturn(List.of(candidate));
        when(deps.redisIdWorker.nextId("ai_conversation")).thenReturn(1001L, 1002L);
        when(deps.redisIdWorker.nextId("ai_user_memory")).thenReturn(2001L);
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("最近我更常去小寨");

        var response = deps.service().chat(request);
        UserHolder.removeUser();

        assertThat(response.isMemoryUpdated()).isTrue();
        verify(deps.userMemoryStore).put(argThat(command ->
                command.namespace().equals("dining.preference")
                        && command.key().equals("area")
                        && command.memoryJson().contains("小寨")));
    }

    @Test
    void addsPreFilteredShopRecommendationsForBudgetRequest() {
        CapturingChatModel chatModel = new CapturingChatModel("推荐 [平价高评分F](spotai://shop/6)。");
        TestDependencies deps = new TestDependencies(chatModel);
        when(deps.spotAiChatTools.recommendShops(40L, 60L, "", "", 5))
                .thenReturn("[{\"id\":6,\"name\":\"平价高评分F\",\"shopUrl\":\"spotai://shop/6\",\"avgPrice\":50}]");
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("推荐几家人均50左右的店");

        deps.service().chat(request);

        assertThat(chatModel.prompt.getContents()).contains("后端预筛选推荐候选");
        assertThat(chatModel.prompt.getContents()).contains("spotai://shop/6");
    }

    @Test
    void responseReportsPreFilteredRecommendationToolUsage() {
        CapturingChatModel chatModel = new CapturingChatModel("ok");
        TestDependencies deps = new TestDependencies(chatModel);
        when(deps.spotAiChatTools.recommendShops(40L, 60L, "", "", 5))
                .thenReturn("[{\"id\":6,\"name\":\"Budget Shop\",\"shopUrl\":\"spotai://shop/6\",\"avgPrice\":50}]");
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("\u63a8\u8350\u51e0\u5bb6\u4eba\u574750\u5de6\u53f3\u7684\u5e97");

        var response = deps.service().chat(request);

        assertThat(response.getUsedTools()).containsExactly("recommendShops");
    }

    @Test
    void normalChatDoesNotReportRecommendationToolUsage() {
        CapturingChatModel chatModel = new CapturingChatModel("ok");
        TestDependencies deps = new TestDependencies(chatModel);
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("\u4f60\u597d");

        var response = deps.service().chat(request);

        assertThat(response.getAgentRoute()).isEqualTo("CHAT");
        assertThat(response.getUsedTools()).isEmpty();
        verify(deps.spotAiChatTools, never()).recommendShops(anyLong(), anyLong(), anyString(), anyString(), anyInt());
    }

    @Test
    void loggedInRecommendationPersistsAssistantUsedTools() {
        CapturingChatModel chatModel = new CapturingChatModel("推荐 [Budget Shop](spotai://shop/6)");
        TestDependencies deps = new TestDependencies(chatModel);
        when(deps.redisIdWorker.nextId("ai_conversation")).thenReturn(1001L, 1002L);
        when(deps.spotAiChatTools.recommendShops(40L, 60L, "", "", 5))
                .thenReturn("[{\"id\":6,\"name\":\"Budget Shop\",\"shopUrl\":\"spotai://shop/6\",\"avgPrice\":50}]");
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("推荐几家人均50左右的店");

        deps.service().chat(request);
        UserHolder.removeUser();

        verify(deps.conversationRepository).save(eq(1001L), eq(99L), eq("user:99:default"), eq("user"),
                eq("推荐几家人均50左右的店"), eq("text"), eq("deepseek-chat"), eq(List.of()));
        verify(deps.conversationRepository).save(eq(1002L), eq(99L), eq("user:99:default"), eq("assistant"),
                eq("推荐 [Budget Shop](spotai://shop/6)"), eq("text"), eq("deepseek-chat"), eq(List.of("recommendShops")));
    }

    @Test
    void passesParsedAreaAndKeywordToRecommendationTool() {
        CapturingChatModel chatModel = new CapturingChatModel("推荐 [钟楼火锅店](spotai://shop/8)。");
        TestDependencies deps = new TestDependencies(chatModel);
        when(deps.spotAiChatTools.recommendShops(40L, 60L, "火锅", "钟楼", 5))
                .thenReturn("[{\"id\":8,\"name\":\"钟楼火锅店\",\"shopUrl\":\"spotai://shop/8\",\"avgPrice\":52}]");
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setRoute("CHAT");
        request.setMessage("推荐几家钟楼人均50左右的火锅店");

        deps.service().chat(request);

        assertThat(chatModel.prompt.getContents()).contains("当前子 Agent 路由：SHOP_GUIDE");
        assertThat(chatModel.prompt.getContents()).contains("spotai://shop/8");
        verify(deps.spotAiChatTools).recommendShops(40L, 60L, "火锅", "钟楼", 5);
    }

    @Test
    void usesStoredPreferenceWhenRecommendationRequestMissesExplicitFilters() {
        CapturingChatModel chatModel = new CapturingChatModel("recommend [Memory Hotpot](spotai://shop/88)");
        TestDependencies deps = new TestDependencies(chatModel);
        AiUserMemory budget = memory("budget_range", "preference", "{\"min\":40,\"max\":60}");
        AiUserMemory category = memory("preferred_category", "preference", "{\"category\":\"hotpot\"}");
        AiUserMemory area = memory("preferred_area", "preference", "{\"area\":\"gaoxin\"}");
        when(deps.userMemoryStore.findActive(99L)).thenReturn(List.of(budget, category, area));
        when(deps.redisIdWorker.nextId("ai_conversation")).thenReturn(1001L, 1002L);
        when(deps.spotAiChatTools.recommendShops(40L, 60L, "hotpot", "gaoxin", 5))
                .thenReturn("[{\"id\":88,\"name\":\"Memory Hotpot\",\"shopUrl\":\"spotai://shop/88\"}]");
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("recommend some shops for me");

        deps.service().chat(request);
        UserHolder.removeUser();

        verify(deps.spotAiChatTools).recommendShops(40L, 60L, "hotpot", "gaoxin", 5);
        assertThat(chatModel.prompt.getContents()).contains("spotai://shop/88");
        assertThat(chatModel.prompt.getContents()).contains("budget_range");
    }

    @Test
    void recommendationPromptUsesOnlyRelevantMemoryNamespaces() {
        CapturingChatModel chatModel = new CapturingChatModel("recommend filtered shops");
        TestDependencies deps = new TestDependencies(chatModel);
        AiUserMemory profile = memory("profile.private", "profile", "{\"budget\":999,\"keyword\":\"secret-profile\"}");
        AiUserMemory budget = memory("dining.preference.budget", "dining.preference", "{\"min\":40,\"max\":60}");
        AiUserMemory category = memory("dining.preference.category", "dining.preference", "{\"keyword\":\"火锅\"}");
        when(deps.userMemoryStore.findActive(99L)).thenReturn(List.of(profile, budget, category));
        when(deps.userMemoryStore.findActive(99L, "dining.preference")).thenReturn(List.of(budget, category));
        when(deps.userMemoryStore.findActive(99L, "preference")).thenReturn(List.of());
        when(deps.redisIdWorker.nextId("ai_conversation")).thenReturn(1001L, 1002L);
        when(deps.spotAiChatTools.recommendShops(40L, 60L, "火锅", "", 5))
                .thenReturn("[{\"id\":88,\"name\":\"高新火锅\",\"shopUrl\":\"spotai://shop/88\"}]");
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("推荐几家店");

        deps.service().chat(request);
        UserHolder.removeUser();

        verify(deps.spotAiChatTools).recommendShops(40L, 60L, "火锅", "", 5);
        verify(deps.userMemoryStore).findActive(99L, "dining.preference");
        verify(deps.userMemoryStore).findActive(99L, "preference");
        assertThat(chatModel.prompt.getContents()).contains("dining.preference.budget");
        assertThat(chatModel.prompt.getContents()).doesNotContain("profile.private");
        assertThat(chatModel.prompt.getContents()).doesNotContain("secret-profile");
    }

    @Test
    void recommendationPromptKeepsLatestMemoryForSameKeyWhenScopedByNamespace() {
        CapturingChatModel chatModel = new CapturingChatModel("recommend latest memory");
        TestDependencies deps = new TestDependencies(chatModel);
        AiUserMemory oldArea = memory("dining.preference.area", "dining.preference", "{\"area\":\"gaoxin\"}");
        AiUserMemory newArea = memory("dining.preference.area", "dining.preference", "{\"area\":\"xiaozhai\"}");
        AiUserMemory category = memory("dining.preference.category", "dining.preference", "{\"keyword\":\"hotpot\"}");
        when(deps.userMemoryStore.findActive(99L, "dining.preference")).thenReturn(List.of(oldArea, category, newArea));
        when(deps.userMemoryStore.findActive(99L, "preference")).thenReturn(List.of());
        when(deps.userMemoryStore.findActive(99L, "conversation.summary")).thenReturn(List.of());
        when(deps.redisIdWorker.nextId("ai_conversation")).thenReturn(1001L, 1002L);
        when(deps.spotAiChatTools.recommendShops(0L, 0L, "hotpot", "xiaozhai", 5))
                .thenReturn("[{\"id\":90,\"name\":\"Latest Area Hotpot\",\"shopUrl\":\"spotai://shop/90\"}]");
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("recommend some shops for me");

        deps.service().chat(request);
        UserHolder.removeUser();

        verify(deps.spotAiChatTools).recommendShops(0L, 0L, "hotpot", "xiaozhai", 5);
        assertThat(chatModel.prompt.getContents()).contains("xiaozhai");
        assertThat(chatModel.prompt.getContents()).doesNotContain("gaoxin");
    }

    @Test
    void usesNestedStoredPreferenceForRecommendationDefaults() {
        CapturingChatModel chatModel = new CapturingChatModel("recommend nested shops");
        TestDependencies deps = new TestDependencies(chatModel);
        AiUserMemory memory = memory("dining.preference.nested", "dining.preference",
                "{\"value\":{\"budgetRange\":{\"min\":30,\"max\":50},\"preferred\":{\"category\":\"coffee\",\"area\":\"xiao zhai\"}}}");
        when(deps.userMemoryStore.findActive(99L)).thenReturn(List.of(memory));
        when(deps.redisIdWorker.nextId("ai_conversation")).thenReturn(1001L, 1002L);
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("recommend some shops for me");

        deps.service().chat(request);
        UserHolder.removeUser();

        verify(deps.spotAiChatTools).recommendShops(30L, 50L, "coffee", "xiao zhai", 5);
    }

    @Test
    void extractedPreferenceMemoryIsReusedByLaterRecommendation() {
        CapturingChatModel chatModel = new CapturingChatModel("ok");
        TestDependencies deps = new TestDependencies(chatModel);
        ObjectMapper objectMapper = new ObjectMapper();
        InMemoryUserMemoryStore memoryStore = new InMemoryUserMemoryStore();
        SpringAiPreferenceExtractorAgent extractor =
                new SpringAiPreferenceExtractorAgent(new CapturingChatModel("not json"), objectMapper);
        when(deps.redisIdWorker.nextId("ai_conversation"))
                .thenReturn(1001L, 1002L, 1003L, 1004L);
        when(deps.redisIdWorker.nextId("ai_user_memory"))
                .thenReturn(2001L, 2002L, 2003L);
        when(deps.spotAiChatTools.recommendShops(40L, 60L, "火锅", "高新", 5))
                .thenReturn("[{\"id\":88,\"name\":\"高新火锅\",\"shopUrl\":\"spotai://shop/88\"}]");
        SpringAiChatService service = deps.service(memoryStore, extractor, objectMapper);
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));

        AiChatRequestDTO preferenceRequest = new AiChatRequestDTO();
        preferenceRequest.setMessage("我平时喜欢在高新附近找人均50左右的火锅店");
        service.chat(preferenceRequest);

        reset(deps.spotAiChatTools);
        when(deps.spotAiChatTools.searchShop(any())).thenReturn("[]");
        when(deps.spotAiChatTools.queryShopDetail(anyLong())).thenReturn("{}");
        when(deps.spotAiChatTools.queryReviewSummary(anyLong())).thenReturn("{}");
        when(deps.spotAiChatTools.queryCoupons(anyLong())).thenReturn("[]");
        when(deps.spotAiChatTools.recommendShops(anyLong(), anyLong(), anyString(), anyString(), anyInt())).thenReturn("[]");
        when(deps.spotAiChatTools.recommendShops(40L, 60L, "火锅", "高新", 5))
                .thenReturn("[{\"id\":88,\"name\":\"高新火锅\",\"shopUrl\":\"spotai://shop/88\"}]");

        AiChatRequestDTO recommendRequest = new AiChatRequestDTO();
        recommendRequest.setMessage("推荐几家店");
        service.chat(recommendRequest);
        UserHolder.removeUser();

        assertThat(memoryStore.findActive(99L)).extracting("memoryKey")
                .contains("dining.preference.budget", "dining.preference.area", "dining.preference.taste");
        verify(deps.spotAiChatTools).recommendShops(40L, 60L, "火锅", "高新", 5);
        assertThat(chatModel.prompt.getContents()).contains("spotai://shop/88");
    }

    @Test
    void recentMessagesReturnsPersistedMessagesForLoggedInUser() {
        TestDependencies deps = new TestDependencies(new CapturingChatModel("unused"));
        AiChatMessageDTO message = new AiChatMessageDTO();
        message.setRole("assistant");
        message.setContent("hello again");
        when(deps.conversationRepository.findRecent(99L, "user:99:default", 20)).thenReturn(List.of(message));
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));

        List<AiChatMessageDTO> messages = deps.service().recentMessages(50);
        UserHolder.removeUser();

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getContent()).isEqualTo("hello again");
        verify(deps.conversationRepository).findRecent(99L, "user:99:default", 20);
    }

    @Test
    void recentMessagesUsesConfiguredVisibleHistoryLimit() {
        TestDependencies deps = new TestDependencies(new CapturingChatModel("unused"));
        deps.aiChatProperties.setVisibleHistoryMaxMessages(5);
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));

        deps.service().recentMessages(50);
        UserHolder.removeUser();

        verify(deps.conversationRepository).findRecent(99L, "user:99:default", 5);
    }

    @Test
    void promptHistoryUsesConfiguredContextWindowLimitAndCharLimit() {
        CapturingChatModel chatModel = new CapturingChatModel("ok");
        TestDependencies deps = new TestDependencies(chatModel);
        deps.aiChatProperties.setContextWindowMaxMessages(2);
        deps.aiChatProperties.setHistoryMaxChars(4);
        AiChatMessageDTO oldMessage = message("user", "old-message");
        AiChatMessageDTO userMessage = message("user", "123456");
        AiChatMessageDTO assistantMessage = message("assistant", "abcdef");
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("hello");
        request.setHistory(List.of(oldMessage, userMessage, assistantMessage));

        deps.service().chat(request);

        assertThat(chatModel.prompt.getContents()).doesNotContain("old-message");
        assertThat(chatModel.prompt.getContents()).contains("user: 1234");
        assertThat(chatModel.prompt.getContents()).contains("assistant: abcd");
    }

    @Test
    void promptHistoryKeepsNewestMessagesWithinTotalContextCharBudget() {
        CapturingChatModel chatModel = new CapturingChatModel("ok");
        TestDependencies deps = new TestDependencies(chatModel);
        deps.aiChatProperties.setContextWindowMaxMessages(5);
        deps.aiChatProperties.setHistoryMaxChars(80);
        deps.aiChatProperties.setContextWindowMaxChars(45);
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("hello");
        request.setHistory(List.of(
                message("user", "first-history-message"),
                message("assistant", "second-history-message"),
                message("user", "third-history-message")
        ));

        deps.service().chat(request);

        assertThat(chatModel.prompt.getContents()).doesNotContain("first-history-message");
        assertThat(chatModel.prompt.getContents()).doesNotContain("second-history-message");
        assertThat(chatModel.prompt.getContents()).contains("user: third-history-message");
    }

    @Test
    void promptMemoryFitsConfiguredTotalMemoryBudget() {
        CapturingChatModel chatModel = new CapturingChatModel("ok");
        TestDependencies deps = new TestDependencies(chatModel);
        deps.aiChatProperties.setMemoryMaxChars(40);
        deps.aiChatProperties.setMemoryTotalMaxChars(170);
        AiUserMemory first = memory("preference.first", "preference", "{\"note\":\"first-memory-value-abcdefghijklmnopqrstuvwxyz\"}");
        AiUserMemory second = memory("preference.second", "preference", "{\"note\":\"second-memory-value-abcdefghijklmnopqrstuvwxyz\"}");
        AiUserMemory third = memory("preference.third", "preference", "{\"note\":\"third-memory-value-abcdefghijklmnopqrstuvwxyz\"}");
        when(deps.userMemoryStore.findActive(99L, "profile")).thenReturn(List.of());
        when(deps.userMemoryStore.findActive(99L, "preference")).thenReturn(List.of(first, second, third));
        when(deps.userMemoryStore.findActive(99L, "conversation.summary")).thenReturn(List.of());
        when(deps.redisIdWorker.nextId("ai_conversation")).thenReturn(1001L, 1002L);
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("你好");

        deps.service().chat(request);
        UserHolder.removeUser();

        assertThat(chatModel.prompt.getContents()).contains("preference.first");
        assertThat(chatModel.prompt.getContents()).contains("preference.second");
        assertThat(chatModel.prompt.getContents()).doesNotContain("preference.third");
    }

    @Test
    void overflowConversationHistoryIsSavedAsSummaryMemory() {
        CapturingChatModel chatModel = new CapturingChatModel("ok");
        TestDependencies deps = new TestDependencies(chatModel);
        deps.aiChatProperties.setContextWindowMaxMessages(2);
        when(deps.conversationRepository.findRecent(99L, "user:99:default", 4)).thenReturn(List.of(
                message("user", "old-user-message"),
                message("assistant", "old-assistant-message"),
                message("user", "recent-user-message")
        ));
        when(deps.redisIdWorker.nextId("ai_conversation")).thenReturn(1001L, 1002L);
        when(deps.redisIdWorker.nextId("ai_user_memory")).thenReturn(2001L);
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("hello");

        deps.service().chat(request);
        UserHolder.removeUser();

        verify(deps.userMemoryStore).put(argThat(command ->
                command.namespace().equals("conversation.summary")
                        && command.key().equals("default")
                        && command.memoryJson().contains("old-user-message")
                        && !command.memoryJson().contains("recent-user-message")
                        && "ConversationSummaryAgent".equals(command.sourceAgent())));
    }

    @Test
    void overflowConversationHistoryUsesStructuredSummaryAgentResult() {
        CapturingChatModel chatModel = new CapturingChatModel("ok");
        TestDependencies deps = new TestDependencies(chatModel);
        deps.aiChatProperties.setContextWindowMaxMessages(2);
        when(deps.conversationRepository.findRecent(99L, "user:99:default", 4)).thenReturn(List.of(
                message("user", "old-user-message"),
                message("assistant", "old-assistant-message"),
                message("user", "recent-user-message")
        ));
        when(deps.conversationSummaryAgent.summarize(eq("CHAT"), any())).thenReturn(Map.of(
                "summary", "用户偏好人均50左右的安静火锅店",
                "budget", "人均50左右",
                "taste", List.of("火锅"),
                "area", List.of("高新"),
                "source", "llm"
        ));
        when(deps.redisIdWorker.nextId("ai_conversation")).thenReturn(1001L, 1002L);
        when(deps.redisIdWorker.nextId("ai_user_memory")).thenReturn(2001L);
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("hello");

        deps.service().chat(request);
        UserHolder.removeUser();

        verify(deps.userMemoryStore).put(argThat(command ->
                command.namespace().equals("conversation.summary")
                        && command.memoryJson().contains("用户偏好人均50左右的安静火锅店")
                        && command.memoryJson().contains("人均50左右")
                        && command.memoryJson().contains("火锅")
                        && command.memoryJson().contains("\"source\":\"llm\"")
                        && command.memoryJson().contains("\"memorySource\":\"overflow_history\"")
                        && !command.memoryJson().contains("old-user-message")));
    }

    @Test
    void skipsUnchangedConversationSummaryMemory() {
        CapturingChatModel chatModel = new CapturingChatModel("ok");
        TestDependencies deps = new TestDependencies(chatModel);
        deps.aiChatProperties.setContextWindowMaxMessages(2);
        AiUserMemory existingSummary = memory("conversation.summary.default", "conversation.summary",
                "{\"summary\":\"same summary\",\"source\":\"llm\",\"agentRoute\":\"CHAT\",\"memorySource\":\"overflow_history\"}");
        when(deps.userMemoryStore.findActive(99L)).thenReturn(List.of(existingSummary));
        when(deps.conversationRepository.findRecent(99L, "user:99:default", 4)).thenReturn(List.of(
                message("user", "old-user-message"),
                message("assistant", "old-assistant-message"),
                message("user", "recent-user-message")
        ));
        when(deps.conversationSummaryAgent.summarize(eq("CHAT"), any())).thenReturn(Map.of(
                "summary", "same summary",
                "source", "llm"
        ));
        when(deps.redisIdWorker.nextId("ai_conversation")).thenReturn(1001L, 1002L);
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("hello");

        var response = deps.service().chat(request);
        UserHolder.removeUser();

        assertThat(response.isMemoryUpdated()).isFalse();
        verify(deps.userMemoryStore, never()).put(any());
    }

    @Test
    void promptIncludesConversationSummaryMemoryForShopGuide() {
        CapturingChatModel chatModel = new CapturingChatModel("ok");
        TestDependencies deps = new TestDependencies(chatModel);
        AiUserMemory summary = memory("conversation.summary.default", "conversation.summary",
                "{\"summary\":\"用户之前说喜欢安静、人均50左右的火锅店\"}");
        when(deps.userMemoryStore.findActive(99L, "dining.preference")).thenReturn(List.of());
        when(deps.userMemoryStore.findActive(99L, "preference")).thenReturn(List.of());
        when(deps.userMemoryStore.findActive(99L, "conversation.summary")).thenReturn(List.of(summary));
        when(deps.redisIdWorker.nextId("ai_conversation")).thenReturn(1001L, 1002L);
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("推荐几家店");

        deps.service().chat(request);
        UserHolder.removeUser();

        verify(deps.userMemoryStore).findActive(99L, "conversation.summary");
        assertThat(chatModel.prompt.getContents()).contains("conversation.summary.default");
        assertThat(chatModel.prompt.getContents()).contains("喜欢安静");
    }

    @Test
    void recommendationUsesStructuredConversationSummaryMemory() {
        CapturingChatModel chatModel = new CapturingChatModel("recommend from summary");
        TestDependencies deps = new TestDependencies(chatModel);
        AiUserMemory summary = memory("conversation.summary.default", "conversation.summary",
                "{\"summary\":\"用户偏好安静高分店\",\"budget\":\"人均50左右\",\"taste\":[\"火锅\"],\"area\":[\"高新\"],\"source\":\"llm\"}");
        when(deps.userMemoryStore.findActive(99L, "dining.preference")).thenReturn(List.of());
        when(deps.userMemoryStore.findActive(99L, "preference")).thenReturn(List.of());
        when(deps.userMemoryStore.findActive(99L, "conversation.summary")).thenReturn(List.of(summary));
        when(deps.redisIdWorker.nextId("ai_conversation")).thenReturn(1001L, 1002L);
        when(deps.spotAiChatTools.recommendShops(40L, 60L, "火锅", "高新", 5))
                .thenReturn("[{\"id\":88,\"name\":\"高新火锅\",\"shopUrl\":\"spotai://shop/88\"}]");
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("推荐几家店");

        deps.service().chat(request);
        UserHolder.removeUser();

        verify(deps.spotAiChatTools).recommendShops(40L, 60L, "火锅", "高新", 5);
        assertThat(chatModel.prompt.getContents()).contains("spotai://shop/88");
    }

    @Test
    void recommendationUsesSceneTagsFromConversationSummaryMemory() {
        CapturingChatModel chatModel = new CapturingChatModel("recommend scene shops");
        TestDependencies deps = new TestDependencies(chatModel);
        AiUserMemory summary = memory("conversation.summary.default", "conversation.summary",
                "{\"summary\":\"用户想找适合约会的安静店\",\"budget\":\"人均50左右\",\"taste\":[\"火锅\"],\"area\":[\"高新\"],\"scene\":[\"约会\",\"安静\"],\"source\":\"llm\"}");
        when(deps.userMemoryStore.findActive(99L, "dining.preference")).thenReturn(List.of());
        when(deps.userMemoryStore.findActive(99L, "preference")).thenReturn(List.of());
        when(deps.userMemoryStore.findActive(99L, "conversation.summary")).thenReturn(List.of(summary));
        when(deps.redisIdWorker.nextId("ai_conversation")).thenReturn(1001L, 1002L);
        when(deps.spotAiChatTools.recommendShops(40L, 60L, "火锅 约会 安静", "高新", 5))
                .thenReturn("[{\"id\":89,\"name\":\"高新约会火锅\",\"shopUrl\":\"spotai://shop/89\"}]");
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("推荐几家店");

        deps.service().chat(request);
        UserHolder.removeUser();

        verify(deps.spotAiChatTools).recommendShops(40L, 60L, "火锅 约会 安静", "高新", 5);
        assertThat(chatModel.prompt.getContents()).contains("spotai://shop/89");
    }

    @Test
    void memoriesExposeSourceAgentAndUpdateTimeForUserControl() {
        TestDependencies deps = new TestDependencies(new CapturingChatModel("unused"));
        AiUserMemory memory = memory("conversation.summary.default", "conversation.summary",
                "{\"summary\":\"用户偏好高新人均50左右的火锅\"}");
        memory.setSourceAgent(ConversationSummaryAgent.AGENT_NAME);
        memory.setUpdateTime(LocalDateTime.of(2026, 7, 2, 9, 30));
        when(deps.userMemoryStore.findActive(99L)).thenReturn(List.of(memory));
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));

        var memories = deps.service().memories();
        UserHolder.removeUser();

        assertThat(memories).hasSize(1);
        assertThat(memories.get(0).getSourceAgent()).isEqualTo(ConversationSummaryAgent.AGENT_NAME);
        assertThat(memories.get(0).getUpdateTime()).isEqualTo(LocalDateTime.of(2026, 7, 2, 9, 30));
    }

    @Test
    void deleteMemoryMarksCurrentUsersMemoryDeleted() {
        TestDependencies deps = new TestDependencies(new CapturingChatModel("unused"));
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));

        deps.service().deleteMemory("preferred_area");
        UserHolder.removeUser();

        verify(deps.userMemoryStore).delete(99L, "preferred_area");
    }

    @Test
    void clearMemoriesMarksCurrentUsersMemoriesDeleted() {
        TestDependencies deps = new TestDependencies(new CapturingChatModel("unused"));
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));

        deps.service().clearMemories();
        UserHolder.removeUser();

        verify(deps.userMemoryStore).clear(99L);
    }

    @Test
    void clearConversationDeletesCurrentUsersDefaultSession() {
        TestDependencies deps = new TestDependencies(new CapturingChatModel("unused"));
        UserHolder.saveUser(new UserDTO(99L, "tester", ""));

        deps.service().clearConversation();
        UserHolder.removeUser();

        verify(deps.conversationRepository).deleteBySession(99L, "user:99:default");
        verify(deps.shortTermMemoryService).clear("user:99:default");
    }

    private static AiUserMemory memory(String key, String type, String json) {
        AiUserMemory memory = new AiUserMemory();
        memory.setMemoryKey(key);
        memory.setMemoryType(type);
        memory.setMemoryJson(json);
        memory.setConfidence(0.9);
        return memory;
    }

    private static AiChatMessageDTO message(String role, String content) {
        AiChatMessageDTO message = new AiChatMessageDTO();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private static class TestDependencies {
        private final CapturingChatModel chatModel;
        private final AiConversationRepository conversationRepository = mock(AiConversationRepository.class);
        private final UserMemoryStore userMemoryStore = mock(UserMemoryStore.class);
        private final PreferenceExtractorAgent preferenceExtractorAgent = mock(PreferenceExtractorAgent.class);
        private final ShopGuideAgent shopGuideAgent = mock(ShopGuideAgent.class);
        private final CouponAgent couponAgent = mock(CouponAgent.class);
        private final SpotAiChatTools spotAiChatTools = mock(SpotAiChatTools.class);
        private final ConversationSummaryAgent conversationSummaryAgent = mock(ConversationSummaryAgent.class);
        private final AiShortTermMemoryService shortTermMemoryService = mock(AiShortTermMemoryService.class);
        private final RedisIdWorker redisIdWorker = mock(RedisIdWorker.class);
        private final AiChatProperties aiChatProperties = new AiChatProperties();
        @SuppressWarnings("unchecked")
        private final ObjectProvider<ReviewSummaryService> reviewSummaryServiceProvider = mock(ObjectProvider.class);
        private final ToolConfirmService toolConfirmService = mock(ToolConfirmService.class);

        private TestDependencies(CapturingChatModel chatModel) {
            this.chatModel = chatModel;
            when(conversationRepository.findRecent(anyLong(), any(), anyInt())).thenReturn(List.of());
            when(userMemoryStore.findActive(anyLong())).thenReturn(List.of());
            when(userMemoryStore.findActive(anyLong(), anyString())).thenReturn(List.of());
            when(shortTermMemoryService.get(anyString())).thenReturn(List.of());
            when(preferenceExtractorAgent.extract(anyLong(), any(), any())).thenReturn(List.of());
            when(conversationSummaryAgent.summarize(anyString(), any())).thenReturn(Map.of());
            when(shopGuideAgent.buildContext(anyLong())).thenReturn("");
            when(couponAgent.buildContext(anyLong())).thenReturn("");
            when(spotAiChatTools.searchShop(any())).thenReturn("[]");
            when(spotAiChatTools.queryShopDetail(anyLong())).thenReturn("{}");
            when(spotAiChatTools.queryReviewSummary(anyLong())).thenReturn("{}");
            when(spotAiChatTools.queryCoupons(anyLong())).thenReturn("[]");
            when(spotAiChatTools.recommendShops(anyLong(), any())).thenReturn("[]");
            when(spotAiChatTools.recommendShops(anyLong(), anyLong(), anyString(), anyString(), anyInt())).thenReturn("[]");
        }

        private SpringAiChatService service() {
            return service(userMemoryStore, preferenceExtractorAgent, new ObjectMapper());
        }

        private SpringAiChatService service(UserMemoryStore userMemoryStore,
                                            PreferenceExtractorAgent preferenceExtractorAgent,
                                            ObjectMapper objectMapper) {
            return new SpringAiChatService(
                    chatModel,
                    conversationRepository,
                    userMemoryStore,
                    preferenceExtractorAgent,
                    shopGuideAgent,
                    couponAgent,
                    spotAiChatTools,
                    conversationSummaryAgent,
                    shortTermMemoryService,
                    new AiContextWindowService(aiChatProperties),
                    aiChatProperties,
                    new RecommendationPreferenceResolver(objectMapper),
                    new AgentMemorySelectionPolicy(),
                    redisIdWorker,
                    reviewSummaryServiceProvider,
                    objectMapper,
                    toolConfirmService,
                    "deepseek-chat"
            );
        }
    }

    private static class CapturingChatModel implements ChatModel {
        private final String content;
        private Prompt prompt;

        private CapturingChatModel(String content) {
            this.content = content;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.prompt = prompt;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
        }
    }

    private static class InMemoryUserMemoryStore implements UserMemoryStore {
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final Map<String, AiUserMemory> memories = new LinkedHashMap<>();

        @Override
        public List<AiUserMemory> findActive(Long userId) {
            return memories.values().stream()
                    .filter(memory -> userId.equals(memory.getUserId()))
                    .toList();
        }

        @Override
        public List<AiUserMemory> findActive(Long userId, String namespace) {
            return memories.values().stream()
                    .filter(memory -> userId.equals(memory.getUserId()))
                    .filter(memory -> namespace.equals(memory.getMemoryType())
                            || memory.getMemoryKey().startsWith(namespace + "."))
                    .toList();
        }

        @Override
        public Optional<AiUserMemory> findOne(Long userId, String namespace, String key) {
            String physicalKey = UserMemoryKey.of(namespace, key).physicalKey();
            return Optional.ofNullable(memories.get(userId + ":" + physicalKey));
        }

        @Override
        public void put(MemoryWriteCommand command) {
            AiUserMemory memory = new AiUserMemory();
            memory.setId(command.id());
            memory.setUserId(command.userId());
            memory.setMemoryKey(command.physicalKey());
            memory.setMemoryType(command.namespace());
            memory.setMemoryJson(normalizeJson(command.memoryJson()));
            memory.setConfidence(command.confidence());
            memory.setSourceMessageId(command.sourceMessageId());
            memory.setSourceAgent(command.sourceAgent());
            memory.setStatus(1);
            memories.put(command.userId() + ":" + command.physicalKey(), memory);
        }

        @Override
        public void delete(Long userId, String namespace, String key) {
            memories.remove(userId + ":" + UserMemoryKey.of(namespace, key).physicalKey());
        }

        @Override
        public void clear(Long userId) {
            memories.entrySet().removeIf(entry -> entry.getKey().startsWith(userId + ":"));
        }

        private String normalizeJson(String json) {
            try {
                return objectMapper.writeValueAsString(objectMapper.readValue(json, Object.class));
            } catch (Exception ignored) {
                return json;
            }
        }
    }
}
