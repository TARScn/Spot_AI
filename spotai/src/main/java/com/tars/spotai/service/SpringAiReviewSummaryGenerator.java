package com.tars.spotai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.config.ReviewAiProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "spotai.ai.review-summary", name = "enabled", havingValue = "true")
public class SpringAiReviewSummaryGenerator implements ReviewSummaryGenerator {
    private static final int MAX_CONTEXT_CHARS_PER_REVIEW = 1200;
    private static final int MAX_SUMMARY_CHARS = 500;
    private static final int MAX_ITEM_CHARS = 80;

    private final VectorStore vectorStore;
    private final ReviewLlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final ReviewAiProperties properties;

    public SpringAiReviewSummaryGenerator(VectorStore vectorStore,
                                          ReviewLlmClient llmClient,
                                          ObjectMapper objectMapper,
                                          ReviewAiProperties properties) {
        this.vectorStore = vectorStore;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public ReviewSummaryContent generate(Long shopId) {
        SearchRequest request = SearchRequest.builder()
                .query("总结店铺评论中的总体评价、优点、缺点和适合场景")
                .topK(properties.getTopK())
                .similarityThreshold(properties.getSimilarityThreshold())
                .filterExpression("shopId == '" + shopId + "'")
                .build();
        List<Document> documents = vectorStore.similaritySearch(request);
        if (documents == null || documents.isEmpty()) {
            throw new IllegalStateException("未检索到可用于总结的评论");
        }

        String context = buildContext(documents);
        return parseAndValidate(llmClient.summarize(context));
    }

    private String buildContext(List<Document> documents) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            String text = documents.get(i).getText();
            if (text == null) {
                continue;
            }
            String trimmed = text.strip();
            if (trimmed.length() > MAX_CONTEXT_CHARS_PER_REVIEW) {
                trimmed = trimmed.substring(0, MAX_CONTEXT_CHARS_PER_REVIEW);
            }
            context.append(i + 1).append(". ").append(trimmed).append('\n');
        }
        return context.toString();
    }

    private ReviewSummaryContent parseAndValidate(String response) {
        if (response == null) {
            throw new IllegalStateException("DeepSeek 未返回总结内容");
        }
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("DeepSeek 返回格式不正确");
        }
        try {
            ReviewSummaryContent parsed = objectMapper.readValue(response.substring(start, end + 1), ReviewSummaryContent.class);
            if (parsed.summary() == null || parsed.summary().isBlank()) {
                throw new IllegalStateException("DeepSeek 返回的总结为空");
            }
            return new ReviewSummaryContent(
                    truncate(parsed.summary().strip(), MAX_SUMMARY_CHARS),
                    sanitizeList(parsed.highlights(), 3),
                    sanitizeList(parsed.weaknesses(), 3),
                    sanitizeList(parsed.scenes(), 2)
            );
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("DeepSeek 返回格式不正确", e);
        }
    }

    private List<String> sanitizeList(List<String> values, int limit) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .map(value -> truncate(value, MAX_ITEM_CHARS))
                .limit(limit)
                .toList();
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
