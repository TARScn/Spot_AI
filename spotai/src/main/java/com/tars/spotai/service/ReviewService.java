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

@Service
public class ReviewService {
    private static final int PAGE_SIZE = 5;

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

    @Transactional
    public Result<Long> saveReview(ReviewCreateDTO createDTO) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("Please login first");
        }
        Result<List<String>> validation = validateCreateDTO(createDTO);
        if (!validation.isSuccess()) {
            return Result.fail(validation.getErrorMsg());
        }
        if (shopRepository.findById(createDTO.getShopId()) == null) {
            return Result.fail("Shop not found");
        }

        LocalDateTime now = LocalDateTime.now();
        List<String> images = validation.getData();
        Long reviewId = redisIdWorker.nextId("review");
        Review review = new Review();
        review.setId(reviewId);
        review.setShopId(createDTO.getShopId());
        review.setUserId(currentUser.getId());
        review.setScore(createDTO.getScore());
        review.setContent(createDTO.getContent().trim());
        review.setStatus(0);
        review.setLiked(0);
        review.setImagesCount(images.size());
        review.setCreateTime(now);
        review.setUpdateTime(now);
        reviewRepository.save(review);
        if (!images.isEmpty()) {
            List<Long> imageIds = images.stream()
                    .map(image -> redisIdWorker.nextId("review_image"))
                    .toList();
            reviewRepository.saveImages(reviewId, imageIds, images);
        }
        markSummaryStaleAndRefreshAfterCommit(createDTO.getShopId());
        return Result.ok(reviewId);
    }

    public Result<PageResultDTO<ReviewViewDTO>> queryByShop(Long shopId, Integer current) {
        if (shopId == null || shopId <= 0) {
            return Result.fail("商户ID不合法");
        }
        if (shopRepository.findById(shopId) == null) {
            return Result.fail("商户不存在");
        }

        int page = current == null || current <= 0 ? 1 : current;
        List<Review> reviews = reviewRepository.findByShopId(shopId, page, PAGE_SIZE + 1);
        boolean hasMore = reviews.size() > PAGE_SIZE;
        if (hasMore) {
            reviews = reviews.subList(0, PAGE_SIZE);
        }

        List<Long> reviewIds = reviews.stream().map(Review::getId).toList();
        Map<Long, List<String>> imageMap = reviewRepository.findImagesByReviewIds(reviewIds);
        List<ReviewViewDTO> dtoList = reviews.stream()
                .map(review -> toViewDTO(review, imageMap.getOrDefault(review.getId(), List.of())))
                .toList();
        return Result.ok(new PageResultDTO<>(dtoList, page, PAGE_SIZE, hasMore));
    }

    public Result<Void> deleteReview(Long id) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("Please login first");
        }
        if (id == null || id <= 0) {
            return Result.fail("Invalid review id");
        }
        Review review = reviewRepository.findById(id);
        if (review == null || review.getStatus() == null || review.getStatus() != 0) {
            return Result.fail("Review not found");
        }
        if (!currentUser.getId().equals(review.getUserId())) {
            return Result.fail("No permission to delete this review");
        }
        int affectedRows = reviewRepository.markDeletedByIdAndUserId(id, currentUser.getId());
        if (affectedRows == 0) {
            return Result.fail("Delete failed");
        }
        markSummaryStaleAndRefreshAfterCommit(review.getShopId());
        return Result.ok(null);
    }

    public Result<PageResultDTO<ReviewViewDTO>> queryMyReviews(Integer current) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("璇峰厛鐧诲綍");
        }
        int page = current == null || current <= 0 ? 1 : current;
        List<Review> reviews = reviewRepository.findByUserId(currentUser.getId(), page, PAGE_SIZE + 1);
        boolean hasMore = reviews.size() > PAGE_SIZE;
        if (hasMore) {
            reviews = reviews.subList(0, PAGE_SIZE);
        }
        List<ReviewViewDTO> dtoList = toViewDTOList(reviews);
        return Result.ok(new PageResultDTO<>(dtoList, page, PAGE_SIZE, hasMore));
    }

    public List<ReviewViewDTO> queryUserReviewList(Long userId, int limit) {
        return toViewDTOList(reviewRepository.findByUserId(userId, 1, limit));
    }

    private Result<List<String>> validateCreateDTO(ReviewCreateDTO createDTO) {
        if (createDTO == null || createDTO.getShopId() == null || createDTO.getShopId() <= 0) {
            return Result.fail("Invalid shop id");
        }
        if (createDTO.getScore() == null || createDTO.getScore() < 1 || createDTO.getScore() > 5) {
            return Result.fail("Score must be between 1 and 5");
        }
        if (!StringUtils.hasText(createDTO.getContent())) {
            return Result.fail("Review content is required");
        }
        List<String> images = createDTO.getImages() == null ? List.of() : createDTO.getImages().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(10)
                .toList();
        if (images.size() > 9) {
            return Result.fail("Images must not exceed 9 items");
        }
        boolean hasInvalidImage = images.stream().anyMatch(image -> image.length() > 1024);
        if (hasInvalidImage) {
            return Result.fail("Image url is too long");
        }
        return Result.ok(images);
    }

    private List<ReviewViewDTO> toViewDTOList(List<Review> reviews) {
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
}
