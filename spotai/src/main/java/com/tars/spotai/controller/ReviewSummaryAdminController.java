package com.tars.spotai.controller;

import com.tars.spotai.config.ReviewAiProperties;
import com.tars.spotai.dto.Result;
import com.tars.spotai.service.ReviewRagIndexService;
import com.tars.spotai.service.ReviewSummaryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

@RestController
public class ReviewSummaryAdminController {
    private final Optional<ReviewRagIndexService> ragIndexService;
    private final ReviewSummaryService summaryService;
    private final ReviewAiProperties properties;

    public ReviewSummaryAdminController(Optional<ReviewRagIndexService> ragIndexService,
                                        ReviewSummaryService summaryService,
                                        ReviewAiProperties properties) {
        this.ragIndexService = ragIndexService;
        this.summaryService = summaryService;
        this.properties = properties;
    }

    @PostMapping("/admin/review/summary/reindex")
    public Result<Integer> reindex(@RequestParam Long shopId,
                                   @RequestHeader(value = "X-Review-AI-Key", required = false) String adminKey) {
        if (shopId == null || shopId <= 0) {
            return Result.fail("商户 ID 不合法");
        }
        if (!authorized(adminKey)) {
            return Result.fail("无权执行评论向量重建");
        }
        if (ragIndexService.isEmpty()) {
            return Result.fail("评论 AI 功能未启用");
        }
        int indexed = ragIndexService.get().reindexShop(shopId);
        summaryService.refreshSummaryNow(shopId);
        return Result.ok(indexed);
    }

    private boolean authorized(String providedKey) {
        String configuredKey = properties.getAdminKey();
        if (configuredKey == null || configuredKey.isBlank() || providedKey == null) {
            return false;
        }
        return MessageDigest.isEqual(
                configuredKey.getBytes(StandardCharsets.UTF_8),
                providedKey.getBytes(StandardCharsets.UTF_8)
        );
    }
}
