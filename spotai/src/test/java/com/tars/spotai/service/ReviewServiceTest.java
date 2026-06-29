package com.tars.spotai.service;

import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.ReviewCreateDTO;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.entity.Review;
import com.tars.spotai.entity.Shop;
import com.tars.spotai.repository.ReviewRepository;
import com.tars.spotai.repository.ShopRepository;
import com.tars.spotai.repository.UserRepository;
import com.tars.spotai.utils.RedisIdWorker;
import com.tars.spotai.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ShopRepository shopRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RedisIdWorker redisIdWorker;

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(reviewRepository, shopRepository, userRepository, redisIdWorker);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void saveReviewCreatesReviewAndImagesForCurrentUser() {
        UserHolder.saveUser(new UserDTO(1001L, "alice", ""));
        ReviewCreateDTO createDTO = createDTO();
        when(shopRepository.findById(1L)).thenReturn(new Shop());
        when(redisIdWorker.nextId("review")).thenReturn(9001L);
        when(redisIdWorker.nextId("review_image")).thenReturn(9101L, 9102L);

        Result<Long> result = reviewService.saveReview(createDTO);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(9001L);
        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1001L);
        assertThat(captor.getValue().getShopId()).isEqualTo(1L);
        assertThat(captor.getValue().getScore()).isEqualTo(5);
        assertThat(captor.getValue().getImagesCount()).isEqualTo(2);
        verify(reviewRepository).saveImages(9001L, List.of(9101L, 9102L), createDTO.getImages());
    }

    @Test
    void saveReviewFailsWhenUserIsNotLoggedIn() {
        Result<Long> result = reviewService.saveReview(createDTO());

        assertThat(result.isSuccess()).isFalse();
        verify(reviewRepository, never()).save(any());
        verify(reviewRepository, never()).saveImages(any(), anyList(), anyList());
    }

    @Test
    void deleteReviewMarksCurrentUsersReviewDeleted() {
        UserHolder.saveUser(new UserDTO(1001L, "alice", ""));
        Review review = review(9001L, 1001L);
        when(reviewRepository.findById(9001L)).thenReturn(review);
        when(reviewRepository.markDeletedByIdAndUserId(9001L, 1001L)).thenReturn(1);

        Result<Void> result = reviewService.deleteReview(9001L);

        assertThat(result.isSuccess()).isTrue();
        verify(reviewRepository).markDeletedByIdAndUserId(9001L, 1001L);
    }

    @Test
    void deleteReviewRejectsOtherUsersReview() {
        UserHolder.saveUser(new UserDTO(1001L, "alice", ""));
        when(reviewRepository.findById(9001L)).thenReturn(review(9001L, 1002L));

        Result<Void> result = reviewService.deleteReview(9001L);

        assertThat(result.isSuccess()).isFalse();
        verify(reviewRepository, never()).markDeletedByIdAndUserId(any(), any());
    }

    private ReviewCreateDTO createDTO() {
        ReviewCreateDTO createDTO = new ReviewCreateDTO();
        createDTO.setShopId(1L);
        createDTO.setScore(5);
        createDTO.setContent("Good food and service.");
        createDTO.setImages(List.of("http://localhost:9000/spotai/review/a.jpg", "http://localhost:9000/spotai/review/b.jpg"));
        return createDTO;
    }

    private Review review(Long id, Long userId) {
        Review review = new Review();
        review.setId(id);
        review.setUserId(userId);
        review.setStatus(0);
        return review;
    }
}
