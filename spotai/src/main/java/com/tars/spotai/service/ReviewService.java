package com.tars.spotai.service;

import com.tars.spotai.dto.PageResultDTO;
import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.ReviewViewDTO;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.entity.Review;
import com.tars.spotai.entity.User;
import com.tars.spotai.repository.ReviewRepository;
import com.tars.spotai.repository.ShopRepository;
import com.tars.spotai.repository.UserRepository;
import com.tars.spotai.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ReviewService {
    private static final int PAGE_SIZE = 5;

    private final ReviewRepository reviewRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;

    public ReviewService(ReviewRepository reviewRepository,
                         ShopRepository shopRepository,
                         UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
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
}
