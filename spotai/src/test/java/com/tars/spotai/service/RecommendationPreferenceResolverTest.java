package com.tars.spotai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.entity.AiUserMemory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationPreferenceResolverTest {
    private final RecommendationPreferenceResolver resolver = new RecommendationPreferenceResolver(new ObjectMapper());

    @Test
    void resolvesExplicitBudgetKeywordAndAreaFromUserMessage() {
        var preference = resolver.resolve("推荐几家钟楼人均50左右的火锅店", List.of());

        assertThat(preference.budgetRange()).containsExactly(40L, 60L);
        assertThat(preference.keyword()).isEqualTo("火锅");
        assertThat(preference.area()).isEqualTo("钟楼");
        assertThat(preference.hasAny()).isTrue();
    }

    @Test
    void resolvesStoredPreferencesWhenMessageMissesFilters() {
        AiUserMemory budget = memory("{\"min\":40,\"max\":60}");
        AiUserMemory category = memory("{\"keyword\":\"火锅\"}");
        AiUserMemory area = memory("{\"area\":\"高新\"}");

        var preference = resolver.resolve("推荐几家店", List.of(budget, category, area));

        assertThat(preference.budgetRange()).containsExactly(40L, 60L);
        assertThat(preference.keyword()).isEqualTo("火锅");
        assertThat(preference.area()).isEqualTo("高新");
    }

    @Test
    void resolvesNestedStoredPreferences() {
        AiUserMemory memory = memory(
                "{\"value\":{\"budgetRange\":{\"min\":30,\"max\":50},\"preferred\":{\"category\":\"coffee\",\"area\":\"xiao zhai\"}}}");

        var preference = resolver.resolve("recommend some shops", List.of(memory));

        assertThat(preference.budgetRange()).containsExactly(30L, 50L);
        assertThat(preference.keyword()).isEqualTo("coffee");
        assertThat(preference.area()).isEqualTo("xiao zhai");
    }

    @Test
    void resolvesConversationSummaryStructuredFields() {
        AiUserMemory memory = memory(
                "{\"summary\":\"用户偏好安静高分店\",\"budget\":\"人均50左右\",\"taste\":[\"火锅\"],\"area\":[\"高新\"],\"scene\":[\"朋友聚餐\"],\"source\":\"llm\"}",
                "conversation.summary");

        var preference = resolver.resolve("推荐几家店", List.of(memory));

        assertThat(preference.budgetRange()).containsExactly(40L, 60L);
        assertThat(preference.keyword()).isEqualTo("火锅");
        assertThat(preference.area()).isEqualTo("高新");
    }

    @Test
    void explicitMessageTakesPrecedenceOverMemory() {
        AiUserMemory memory = memory("{\"min\":40,\"max\":60,\"keyword\":\"火锅\",\"area\":\"高新\"}");

        var preference = resolver.resolve("推荐几家钟楼人均80左右的咖啡店", List.of(memory));

        assertThat(preference.budgetRange()).containsExactly(64L, 96L);
        assertThat(preference.keyword()).isEqualTo("咖啡");
        assertThat(preference.area()).isEqualTo("钟楼");
    }

    @Test
    void shopCountIsNotTreatedAsBudget() {
        var preference = resolver.resolve("推荐3家高新的火锅店", List.of());

        assertThat(preference.budgetRange()).isNull();
        assertThat(preference.keyword()).isEqualTo("火锅");
        assertThat(preference.area()).isEqualTo("高新");
    }

    @Test
    void detectsRecommendationIntent() {
        assertThat(resolver.isRecommendationIntent("推荐几家店")).isTrue();
        assertThat(resolver.isRecommendationIntent("recommend some shops")).isTrue();
        assertThat(resolver.isRecommendationIntent("我喜欢安静一点、人均80左右的店")).isFalse();
        assertThat(resolver.isRecommendationIntent("今天天气不错")).isFalse();
    }

    @Test
    void keepsCuisineAndSceneKeywordsTogether() {
        var preference = resolver.resolve("推荐几家高新适合约会的人均50左右火锅店", List.of());

        assertThat(preference.budgetRange()).containsExactly(40L, 60L);
        assertThat(preference.keyword()).contains("火锅", "约会");
        assertThat(preference.area()).isEqualTo("高新");
    }

    @Test
    void combinesMemoryCuisineAndSceneKeywords() {
        AiUserMemory memory = memory(
                "{\"budget\":\"人均50左右\",\"taste\":[\"火锅\"],\"area\":[\"高新\"],\"scene\":[\"约会\",\"安静\"],\"source\":\"llm\"}",
                "conversation.summary");

        var preference = resolver.resolve("推荐几家店", List.of(memory));

        assertThat(preference.budgetRange()).containsExactly(40L, 60L);
        assertThat(preference.keyword()).contains("火锅", "约会", "安静");
        assertThat(preference.area()).isEqualTo("高新");
    }

    private static AiUserMemory memory(String json) {
        return memory(json, "preference");
    }

    private static AiUserMemory memory(String json, String type) {
        AiUserMemory memory = new AiUserMemory();
        memory.setMemoryJson(json);
        memory.setMemoryType(type);
        memory.setConfidence(0.9);
        return memory;
    }
}
