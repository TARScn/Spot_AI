package com.tars.spotai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.repository.AiToolCallLogRepository;
import com.tars.spotai.utils.UserHolder;
import org.springframework.stereotype.Service;

@Service
public class ToolConfirmService {
    private final AiToolCallLogRepository logRepository;
    private final VoucherService voucherService;
    private final ObjectMapper objectMapper;

    public ToolConfirmService(AiToolCallLogRepository logRepository, VoucherService voucherService, ObjectMapper objectMapper) {
        this.logRepository = logRepository;
        this.voucherService = voucherService;
        this.objectMapper = objectMapper;
    }

    public String confirm(String confirmToken, boolean confirmed) {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            return "{\"status\":\"ERROR\",\"message\":\"请先登录\"}";
        }

        AiToolCallLogRepository.ToolCallLogRecord record = logRepository.findByUserIdAndConfirmToken(user.getId(), confirmToken);
        if (record == null) {
            return "{\"status\":\"ERROR\",\"message\":\"确认令牌无效或已过期\"}";
        }
        if (!record.userId().equals(user.getId())) {
            return "{\"status\":\"ERROR\",\"message\":\"无权操作\"}";
        }

        if (!confirmed) {
            logRepository.updateStatusAndOutput(record.id(), record.userId(), "rejected", "{\"status\":\"REJECTED\"}", null);
            return "{\"status\":\"REJECTED\"}";
        }

        String result = executeConfirmedTool(record);
        logRepository.updateStatusAndOutput(record.id(), record.userId(), "confirmed", result, null);
        return result;
    }

    private String executeConfirmedTool(AiToolCallLogRepository.ToolCallLogRecord record) {
        try {
            if ("claimCoupon".equals(record.toolName())) {
                JsonNode input = objectMapper.readTree(record.toolInputJson());
                long voucherId = input.has("voucherId") ? input.get("voucherId").asLong() : 0;
                if (voucherId <= 0) {
                    return "{\"status\":\"ERROR\",\"message\":\"参数错误\"}";
                }
                Result<Long> result = voucherService.claimVoucher(voucherId);
                if (result.getData() != null) {
                    return "{\"status\":\"SUCCESS\",\"orderId\":" + result.getData() + "}";
                }
                String msg = result.getErrorMsg() == null ? "操作失败" : result.getErrorMsg();
                return "{\"status\":\"FAILED\",\"message\":\"" + escapeJson(msg) + "\"}";
            }
            return "{\"status\":\"ERROR\",\"message\":\"未知工具类型\"}";
        } catch (Exception e) {
            return "{\"status\":\"ERROR\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
