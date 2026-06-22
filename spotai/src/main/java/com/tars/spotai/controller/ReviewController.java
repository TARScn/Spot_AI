package com.tars.spotai.controller;

import com.tars.spotai.dto.PageResultDTO;
import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.ReviewViewDTO;
import com.tars.spotai.service.ReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReviewController {
    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
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
}
