package com.tars.spotai.entity;

import lombok.Data;

@Data
public class ReviewEmbedding {
    private Long reviewId;
    private Long shopId;
    private String chunkText;
    private String embeddingId;
    private String embeddingModel;
    private String embeddingJson;
    private boolean redisIndexed;
}
