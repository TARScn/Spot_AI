package com.tars.spotai.controller;

import com.tars.spotai.dto.AiChatMessageDTO;
import com.tars.spotai.dto.AiChatRequestDTO;
import com.tars.spotai.dto.AiChatResponseDTO;
import com.tars.spotai.dto.AiMemoryDTO;
import com.tars.spotai.service.AiChatService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatControllerTest {
    @Test
    void chatReturnsWrappedAiResponse() {
        AiChatService service = mock(AiChatService.class);
        AiChatController controller = new AiChatController(service);
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("推荐几家店");
        AiChatResponseDTO response = AiChatResponseDTO.of("推荐 [高新火锅](spotai://shop/88)", "CHAT", "SHOP_GUIDE", false, List.of());
        when(service.chat(request)).thenReturn(response);

        var result = controller.chat(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isSameAs(response);
        verify(service).chat(request);
    }

    @Test
    void recentMessagesReturnsWrappedHistory() {
        AiChatService service = mock(AiChatService.class);
        AiChatController controller = new AiChatController(service);
        AiChatMessageDTO message = new AiChatMessageDTO();
        message.setRole("assistant");
        message.setContent("hello");
        when(service.recentMessages(5)).thenReturn(List.of(message));

        var result = controller.recentMessages(5);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsExactly(message);
        verify(service).recentMessages(5);
    }

    @Test
    void clearConversationReturnsSuccess() {
        AiChatService service = mock(AiChatService.class);
        AiChatController controller = new AiChatController(service);

        var result = controller.clearConversation();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isNull();
        verify(service).clearConversation();
    }

    @Test
    void memoriesReturnsWrappedMemoryList() {
        AiChatService service = mock(AiChatService.class);
        AiChatController controller = new AiChatController(service);
        AiMemoryDTO memory = AiMemoryDTO.of("dining.preference.area", "preference", "{\"area\":\"高新\"}", 0.8);
        when(service.memories()).thenReturn(List.of(memory));

        var result = controller.memories();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsExactly(memory);
        verify(service).memories();
    }

    @Test
    void deleteMemoryReturnsSuccess() {
        AiChatService service = mock(AiChatService.class);
        AiChatController controller = new AiChatController(service);

        var result = controller.deleteMemory("dining.preference.area");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isNull();
        verify(service).deleteMemory("dining.preference.area");
    }

    @Test
    void clearMemoriesReturnsSuccess() {
        AiChatService service = mock(AiChatService.class);
        AiChatController controller = new AiChatController(service);

        var result = controller.clearMemories();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isNull();
        verify(service).clearMemories();
    }
}
