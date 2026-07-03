package com.tars.spotai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "spotai.ai.chat")
public class AiChatProperties {
    private int contextWindowMaxMessages = 12;
    private int contextWindowMaxChars = 2400;
    private int visibleHistoryMaxMessages = 20;
    private int historyMaxChars = 800;
    private int memoryMaxChars = 800;
    private int memoryTotalMaxChars = 1600;

    public int safeContextWindowMaxMessages() {
        return Math.max(1, contextWindowMaxMessages);
    }

    public int safeContextWindowMaxChars() {
        return Math.max(1, contextWindowMaxChars);
    }

    public int safeVisibleHistoryMaxMessages() {
        return Math.max(1, visibleHistoryMaxMessages);
    }

    public int safeHistoryMaxChars() {
        return Math.max(1, historyMaxChars);
    }

    public int safeMemoryMaxChars() {
        return Math.max(1, memoryMaxChars);
    }

    public int safeMemoryTotalMaxChars() {
        return Math.max(1, memoryTotalMaxChars);
    }
}
