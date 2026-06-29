package com.tars.spotai.controller;

import com.tars.spotai.dto.PageResultDTO;
import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.ReviewCreateDTO;
import com.tars.spotai.dto.ReviewViewDTO;
import com.tars.spotai.dto.ReviewSummaryDTO;
import com.tars.spotai.service.ReviewService;
import com.tars.spotai.service.ReviewSummaryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReviewController {
    private final ReviewService reviewService;
    private final ReviewSummaryService reviewSummaryService;

    public ReviewController(ReviewService reviewService, ReviewSummaryService reviewSummaryService) {
        this.reviewService = reviewService;
        this.reviewSummaryService = reviewSummaryService;
    }

    @GetMapping("/review/of/shop")
    public Result<PageResultDTO<ReviewViewDTO>> queryReviewByShop(@RequestParam("id") Long shopId,
                                                                  @RequestParam(defaultValue = "1") Integer current) {
        return reviewService.queryByShop(shopId, current);
    }

    @GetMapping("/review/of/me")
    public Result<PageResultDTO<ReviewViewDTO>> queryMyReviews(@RequestParam(defaultValue = "1") Integer current) {
        return reviewService.queryMyReviews(current);
    }

    @PostMapping("/review")
    public Result<Long> saveReview(@Valid @RequestBody ReviewCreateDTO createDTO) {
        return reviewService.saveReview(createDTO);
    }

    @DeleteMapping("/review/{id}")
    public Result<Void> deleteReview(@PathVariable Long id) {
        return reviewService.deleteReview(id);
    }

    @GetMapping("/review/summary")
    public Result<ReviewSummaryDTO> querySummary(@RequestParam Long shopId) {
        return reviewSummaryService.querySummary(shopId);
    }
}
