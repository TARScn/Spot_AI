package com.tars.spotai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.config.ReviewAiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpringAiReviewSummaryGeneratorTest {

    @Mock
    private VectorStore vectorStore;
    @Mock
    private ReviewLlmClient llmClient;

    private SpringAiReviewSummaryGenerator generator;

    @BeforeEach
    void setUp() {
        ReviewAiProperties properties = new ReviewAiProperties();
        properties.setTopK(12);
        properties.setSimilarityThreshold(0.3);
        generator = new SpringAiReviewSummaryGenerator(
                vectorStore,
                llmClient,
                new ObjectMapper(),
                properties
        );
    }

    @Test
    void retrievesOnlyCurrentShopAndParsesFencedJson() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                Document.builder().text("评分5：牛肉很嫩").build(),
                Document.builder().text("评分3：高峰期排队较久").build()
        ));
        when(llmClient.summarize(any())).thenReturn("""
                ```json
                {
                  "summary": "口味获得认可，但高峰期需要排队。",
                  "highlights": ["牛肉口感好"],
                  "weaknesses": ["高峰期排队"],
                  "scenes": ["朋友聚餐"]
                }
                ```
                """);

        ReviewSummaryContent result = generator.generate(7L);

        assertThat(result.summary()).contains("口味获得认可");
        assertThat(result.highlights()).containsExactly("牛肉口感好");

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(requestCaptor.capture());
        SearchRequest request = requestCaptor.getValue();
        assertThat(request.getTopK()).isEqualTo(12);
        assertThat(request.getSimilarityThreshold()).isEqualTo(0.3);
        assertThat(request.getFilterExpression()).isNotNull();
        assertThat(request.getFilterExpression().toString()).contains("shopId").contains("7");
    }

    @Test
    void refusesToGenerateWhenRetrievalReturnsNoReviews() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        assertThatThrownBy(() -> generator.generate(7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未检索到");
        verifyNoInteractions(llmClient);
    }
}
