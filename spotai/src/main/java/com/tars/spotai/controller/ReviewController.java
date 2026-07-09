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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店铺评价接口。
 *
 * <p>评价列表和 AI 评价摘要是店铺详情页的关键数据。发布/删除评价后，
 * 摘要失效与后台刷新由 {@link ReviewService} 处理，控制器不承载业务规则。</p>
 */
@RestController
@RequestMapping("/review")
public class ReviewController {
    private final ReviewService reviewService;
    private final ReviewSummaryService reviewSummaryService;

    public ReviewController(ReviewService reviewService, ReviewSummaryService reviewSummaryService) {
        this.reviewService = reviewService;
        this.reviewSummaryService = reviewSummaryService;
    }

    /** 店铺详情页评价列表，支持前端下滑分页加载。 */
    @GetMapping("/of/shop")
    public Result<PageResultDTO<ReviewViewDTO>> queryReviewByShop(@RequestParam("id") Long shopId,
                                                                  @RequestParam(defaultValue = "1") Integer current) {
        return reviewService.queryByShop(shopId, current);
    }

    /** 当前登录用户发布过的评价。 */
    @GetMapping("/of/me")
    public Result<PageResultDTO<ReviewViewDTO>> queryMyReviews(@RequestParam(defaultValue = "1") Integer current) {
        return reviewService.queryMyReviews(current);
    }

    /** 发布评价，支持图片 URL 列表。 */
    @PostMapping
    public Result<Long> saveReview(@Valid @RequestBody ReviewCreateDTO createDTO) {
        return reviewService.saveReview(createDTO);
    }

    /** 删除自己的评价，删除后会触发店铺评价摘要刷新。 */
    @DeleteMapping("/{id}")
    public Result<Void> deleteReview(@PathVariable Long id) {
        return reviewService.deleteReview(id);
    }

    /** 查询店铺评价 AI 摘要；没有摘要时服务层会尝试立即生成。 */
    @GetMapping("/summary")
    public Result<ReviewSummaryDTO> querySummary(@RequestParam Long shopId) {
        return reviewSummaryService.querySummary(shopId);
    }
}
