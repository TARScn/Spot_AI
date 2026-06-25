package com.tars.spotai.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
public class ReviewSummaryDTO {
    public static final String STATUS_READY = "READY";
    public static final String STATUS_INSUFFICIENT_REVIEWS = "INSUFFICIENT_REVIEWS";
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

    private Long shopId;
    private String status;
    private String message;
    private String summary;
    private List<String> highlights = List.of();
    private List<String> weaknesses = List.of();
    private List<String> scenes = List.of();
    private int reviewCount;
    private LocalDateTime generatedAt;

    public static ReviewSummaryDTO ready(Long shopId,
                                         String summary,
                                         List<String> highlights,
                                         List<String> weaknesses,
                                         List<String> scenes,
                                         int reviewCount) {
        ReviewSummaryDTO dto = base(shopId, STATUS_READY, reviewCount);
        dto.setSummary(summary);
        dto.setHighlights(safeList(highlights));
        dto.setWeaknesses(safeList(weaknesses));
        dto.setScenes(safeList(scenes));
        dto.setGeneratedAt(LocalDateTime.now());
        return dto;
    }

    public static ReviewSummaryDTO insufficient(Long shopId, int reviewCount) {
        ReviewSummaryDTO dto = base(shopId, STATUS_INSUFFICIENT_REVIEWS, reviewCount);
        dto.setMessage("当前评价较少，暂不生成 AI 总结");
        return dto;
    }

    public static ReviewSummaryDTO unavailable(Long shopId, int reviewCount) {
        ReviewSummaryDTO dto = base(shopId, STATUS_UNAVAILABLE, reviewCount);
        dto.setMessage("AI 总结暂不可用");
        return dto;
    }

    private static ReviewSummaryDTO base(Long shopId, String status, int reviewCount) {
        ReviewSummaryDTO dto = new ReviewSummaryDTO();
        dto.setShopId(shopId);
        dto.setStatus(status);
        dto.setReviewCount(reviewCount);
        return dto;
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
