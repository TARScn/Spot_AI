package com.tars.spotai.service;

import com.tars.spotai.dto.AiChatMessageDTO;

import java.util.List;
import java.util.Map;

public interface ConversationSummaryAgent {
    String AGENT_NAME = "ConversationSummaryAgent";

    Map<String, Object> summarize(String agentRoute, List<AiChatMessageDTO> overflowHistory);
}
