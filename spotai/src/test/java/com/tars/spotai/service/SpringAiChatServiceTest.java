package com.tars.spotai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.dto.AiChatMessageDTO;
import com.tars.spotai.dto.AiChatRequestDTO;
import com.tars.spotai.dto.PreferenceMemoryCandidateDTO;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.repository.AiConversationRepository;
import com.tars.spotai.repository.AiUserMemoryRepository;
import com.tars.spotai.utils.RedisIdWorker;
import com.tars.spotai.utils.UserHolder;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        verify(deps.conversationRepository).findRecent(eq(99L), eq("user:99:default"), eq(12));
        verify(deps.conversationRepository).save(eq(1001L), eq(99L), eq("user:99:default"), eq("user"), eq("我喜欢安静一点、人均 80 左右的店"), eq("text"), eq("deepseek-chat"));
        verify(deps.conversationRepository).save(eq(1002L), eq(99L), eq("user:99:default"), eq("assistant"), eq("我会优先考虑安静、人均 80 左右的店。"), eq("text"), eq("deepseek-chat"));
        verify(deps.memoryRepository).upsert(eq(2001L), eq(99L), eq("dining.preference.environment"), eq("preference"), eq("{\"likes\":[\"安静\"]}"), anyDouble(), eq(1001L), eq("PreferenceExtractorAgent"));
    }

    private static class TestDependencies {
        private final CapturingChatModel chatModel;
        private final AiConversationRepository conversationRepository = mock(AiConversationRepository.class);
        private final AiUserMemoryRepository memoryRepository = mock(AiUserMemoryRepository.class);
        private final PreferenceExtractorAgent preferenceExtractorAgent = mock(PreferenceExtractorAgent.class);
        private final ShopGuideAgent shopGuideAgent = mock(ShopGuideAgent.class);
        private final CouponAgent couponAgent = mock(CouponAgent.class);
        private final RedisIdWorker redisIdWorker = mock(RedisIdWorker.class);
        @SuppressWarnings("unchecked")
        private final ObjectProvider<ReviewSummaryService> reviewSummaryServiceProvider = mock(ObjectProvider.class);

        private TestDependencies(CapturingChatModel chatModel) {
            this.chatModel = chatModel;
            when(conversationRepository.findRecent(anyLong(), any(), anyInt())).thenReturn(List.of());
            when(memoryRepository.findActiveByUserId(anyLong())).thenReturn(List.of());
            when(preferenceExtractorAgent.extract(anyLong(), any(), any())).thenReturn(List.of());
            when(shopGuideAgent.buildContext(anyLong())).thenReturn("");
            when(couponAgent.buildContext(anyLong())).thenReturn("");
        }

        private SpringAiChatService service() {
            return new SpringAiChatService(
                    chatModel,
                    conversationRepository,
                    memoryRepository,
                    preferenceExtractorAgent,
                    shopGuideAgent,
                    couponAgent,
                    redisIdWorker,
                    reviewSummaryServiceProvider,
                    new ObjectMapper(),
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
}
