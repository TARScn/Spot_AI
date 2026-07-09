package com.tars.spotai.service;

import com.tars.spotai.dto.PageResultDTO;
import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.ReviewCreateDTO;
import com.tars.spotai.dto.ReviewViewDTO;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.entity.Review;
import com.tars.spotai.entity.User;
import com.tars.spotai.repository.ReviewRepository;
import com.tars.spotai.repository.ShopRepository;
import com.tars.spotai.repository.UserRepository;
import com.tars.spotai.utils.RedisIdWorker;
import com.tars.spotai.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 店铺评价业务。
 *
 * <p>评价变更会影响店铺 AI 口碑摘要，因此新增/删除评价后必须把摘要标记为过期，
 * 并在事务提交后触发后台刷新。这个顺序很重要：如果在事务提交前生成摘要，
 * 后台线程可能读不到刚写入或刚删除的数据。</p>
 */
@Service
public class ReviewService {
    private static final int PAGE_SIZE = 5;
    private static final int MAX_IMAGE_COUNT = 9;
    private static final int MAX_IMAGE_URL_LENGTH = 1024;
    private static final String LOGIN_REQUIRED = "请先登录";

    private final ReviewRepository reviewRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final RedisIdWorker redisIdWorker;
    private final ReviewSummaryService reviewSummaryService;
    private final ReviewSummaryRefreshScheduler reviewSummaryRefreshScheduler;

    public ReviewService(ReviewRepository reviewRepository,
                         ShopRepository shopRepository,
                         UserRepository userRepository,
                         RedisIdWorker redisIdWorker,
                         ReviewSummaryService reviewSummaryService,
                         ReviewSummaryRefreshScheduler reviewSummaryRefreshScheduler) {
        this.reviewRepository = reviewRepository;
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
        this.redisIdWorker = redisIdWorker;
        this.reviewSummaryService = reviewSummaryService;
        this.reviewSummaryRefreshScheduler = reviewSummaryRefreshScheduler;
    }

    /** 发布评价，并保存评价图片。 */
    @Transactional
    public Result<Long> saveReview(ReviewCreateDTO createDTO) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail(LOGIN_REQUIRED);
        }

        Result<List<String>> validation = validateCreateDTO(createDTO);
        if (!validation.isSuccess()) {
            return Result.fail(validation.getErrorMsg());
        }
        if (shopRepository.findById(createDTO.getShopId()) == null) {
            return Result.fail("店铺不存在");
        }

        List<String> images = validation.getData();
        Review review = createReview(createDTO, currentUser.getId(), images.size());
        reviewRepository.save(review);
        saveReviewImages(review.getId(), images);
        markSummaryStaleAndRefreshAfterCommit(createDTO.getShopId());
        return Result.ok(review.getId());
    }

    public Result<PageResultDTO<ReviewViewDTO>> queryByShop(Long shopId, Integer current) {
        if (!isPositiveId(shopId)) {
            return Result.fail("店铺ID不合法");
        }
        if (shopRepository.findById(shopId) == null) {
            return Result.fail("店铺不存在");
        }

        int page = normalizeCurrent(current);
        PageSlice<Review> slice = queryReviewPage(shopId, page);
        return Result.ok(new PageResultDTO<>(
                toViewDTOList(slice.items()),
                page,
                PAGE_SIZE,
                slice.hasMore()));
    }

    public Result<Void> deleteReview(Long id) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail(LOGIN_REQUIRED);
        }
        if (!isPositiveId(id)) {
            return Result.fail("评价ID不合法");
        }

        Review review = reviewRepository.findById(id);
        if (review == null || review.getStatus() == null || review.getStatus() != 0) {
            return Result.fail("评价不存在");
        }
        if (!currentUser.getId().equals(review.getUserId())) {
            return Result.fail("只能删除自己发布的评价");
        }

        int affectedRows = reviewRepository.markDeletedByIdAndUserId(id, currentUser.getId());
        if (affectedRows == 0) {
            return Result.fail("删除失败");
        }
        markSummaryStaleAndRefreshAfterCommit(review.getShopId());
        return Result.ok(null);
    }

    public Result<PageResultDTO<ReviewViewDTO>> queryMyReviews(Integer current) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail(LOGIN_REQUIRED);
        }

        int page = normalizeCurrent(current);
        List<Review> reviews = reviewRepository.findByUserId(currentUser.getId(), page, PAGE_SIZE + 1);
        PageSlice<Review> slice = trimToPageSize(reviews);
        return Result.ok(new PageResultDTO<>(
                toViewDTOList(slice.items()),
                page,
                PAGE_SIZE,
                slice.hasMore()));
    }

    /** 用户详情页展示最近评价时使用，不做登录态校验。 */
    public List<ReviewViewDTO> queryUserReviewList(Long userId, int limit) {
        return toViewDTOList(reviewRepository.findByUserId(userId, 1, limit));
    }

    private Review createReview(ReviewCreateDTO createDTO, Long userId, int imageCount) {
        LocalDateTime now = LocalDateTime.now();
        Long reviewId = redisIdWorker.nextId("review");
        Review review = new Review();
        review.setId(reviewId);
        review.setShopId(createDTO.getShopId());
        review.setUserId(userId);
        review.setScore(createDTO.getScore());
        review.setContent(createDTO.getContent().trim());
        review.setStatus(0);
        review.setLiked(0);
        review.setImagesCount(imageCount);
        review.setCreateTime(now);
        review.setUpdateTime(now);
        return review;
    }

    private void saveReviewImages(Long reviewId, List<String> images) {
        if (images.isEmpty()) {
            return;
        }
        List<Long> imageIds = images.stream()
                .map(image -> redisIdWorker.nextId("review_image"))
                .toList();
        reviewRepository.saveImages(reviewId, imageIds, images);
    }

    private PageSlice<Review> queryReviewPage(Long shopId, int page) {
        List<Review> reviews = reviewRepository.findByShopId(shopId, page, PAGE_SIZE + 1);
        return trimToPageSize(reviews);
    }

    private PageSlice<Review> trimToPageSize(List<Review> reviews) {
        boolean hasMore = reviews.size() > PAGE_SIZE;
        List<Review> items = hasMore ? reviews.subList(0, PAGE_SIZE) : reviews;
        return new PageSlice<>(items, hasMore);
    }

    private Result<List<String>> validateCreateDTO(ReviewCreateDTO createDTO) {
        if (createDTO == null || !isPositiveId(createDTO.getShopId())) {
            return Result.fail("店铺ID不合法");
        }
        if (createDTO.getScore() == null || createDTO.getScore() < 1 || createDTO.getScore() > 5) {
            return Result.fail("评分必须在1到5之间");
        }
        if (!StringUtils.hasText(createDTO.getContent())) {
            return Result.fail("评价内容不能为空");
        }

        List<String> images = normalizeImages(createDTO.getImages());
        if (images.size() > MAX_IMAGE_COUNT) {
            return Result.fail("图片数量不能超过9张");
        }
        boolean hasInvalidImage = images.stream().anyMatch(image -> image.length() > MAX_IMAGE_URL_LENGTH);
        if (hasInvalidImage) {
            return Result.fail("图片地址过长");
        }
        return Result.ok(images);
    }

    private List<String> normalizeImages(List<String> images) {
        if (images == null) {
            return List.of();
        }
        return images.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(MAX_IMAGE_COUNT + 1)
                .toList();
    }

    private List<ReviewViewDTO> toViewDTOList(List<Review> reviews) {
        if (reviews.isEmpty()) {
            return List.of();
        }
        List<Long> reviewIds = reviews.stream().map(Review::getId).toList();
        Map<Long, List<String>> imageMap = reviewRepository.findImagesByReviewIds(reviewIds);
        return reviews.stream()
                .map(review -> toViewDTO(review, imageMap.getOrDefault(review.getId(), List.of())))
                .toList();
    }

    private ReviewViewDTO toViewDTO(Review review, List<String> images) {
        ReviewViewDTO dto = new ReviewViewDTO();
        dto.setId(review.getId());
        dto.setShopId(review.getShopId());
        dto.setUserId(review.getUserId());
        dto.setScore(review.getScore());
        dto.setContent(review.getContent());
        dto.setLiked(review.getLiked() == null ? 0 : review.getLiked());
        dto.setImages(images);
        dto.setCreateTime(review.getCreateTime());

        User user = userRepository.findById(review.getUserId());
        if (user != null) {
            dto.setUserName(user.getNickName());
            dto.setUserIcon(user.getIcon());
        }
        return dto;
    }

    private void markSummaryStaleAndRefreshAfterCommit(Long shopId) {
        reviewSummaryService.markStale(shopId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    reviewSummaryRefreshScheduler.refreshAsync(shopId);
                }
            });
            return;
        }
        reviewSummaryRefreshScheduler.refreshAsync(shopId);
    }

    private boolean isPositiveId(Long id) {
        return id != null && id > 0;
    }

    private int normalizeCurrent(Integer current) {
        return current == null || current <= 0 ? 1 : current;
    }

    private record PageSlice<T>(List<T> items, boolean hasMore) {
    }
}
