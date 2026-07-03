package com.tars.spotai.service;

public interface AiToolCallLogger {
    AiToolCallLogger NOOP = command -> {
    };

    void log(ToolCallLogCommand command);

    record ToolCallLogCommand(String toolName,
                              String riskLevel,
                              String targetType,
                              Long targetId,
                              Object input,
                              String output,
                              String status,
                              String errorMessage,
                              String confirmToken) {
    }
}
