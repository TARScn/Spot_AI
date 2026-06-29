package com.tars.spotai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ReviewCreateDTO {
    @NotNull(message = "shopId is required")
    private Long shopId;

    @NotNull(message = "score is required")
    @Min(value = 1, message = "score must be at least 1")
    @Max(value = 5, message = "score must be at most 5")
    private Integer score;

    @NotBlank(message = "content is required")
    @Size(max = 2048, message = "content must not exceed 2048 characters")
    private String content;

    @Size(max = 9, message = "images must not exceed 9 items")
    private List<@Size(max = 1024, message = "image url must not exceed 1024 characters") String> images;
}
