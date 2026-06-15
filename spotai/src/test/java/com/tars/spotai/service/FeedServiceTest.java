package com.tars.spotai.service;

import com.tars.spotai.dto.ScrollResultDTO;
import com.tars.spotai.repository.FollowRepository;
import com.tars.spotai.utils.RedisConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {
    @Mock
    private FollowRepository followRepository;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private FeedService feedService;

    @BeforeEach
    void setUp() {
        feedService = new FeedService(followRepository, stringRedisTemplate);
    }

    @Test
    void pushBlogToFollowersWritesBlogIdToEachFollowerFeed() {
        when(followRepository.findFollowerIds(1001L)).thenReturn(List.of(2001L, 2002L));
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        feedService.pushBlogToFollowers(1001L, 9001L);

        verify(zSetOperations).add(eq(RedisConstants.FEED_KEY + 2001L), eq("9001"), anyDouble());
        verify(zSetOperations).add(eq(RedisConstants.FEED_KEY + 2002L), eq("9001"), anyDouble());
    }

    @Test
    void pushBlogToFollowersSkipsWhenNoFollowersExist() {
        when(followRepository.findFollowerIds(1001L)).thenReturn(List.of());

        feedService.pushBlogToFollowers(1001L, 9001L);

        verify(stringRedisTemplate, never()).opsForZSet();
    }

    @Test
    void queryFeedBlogIdsReturnsScrollMetadata() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRangeByScoreWithScores(RedisConstants.FEED_KEY + 1001L, 0, 500L, 0, 10))
                .thenReturn(new LinkedHashSet<>(List.of(
                        new DefaultTypedTuple<>("9003", 300.0),
                        new DefaultTypedTuple<>("9002", 200.0),
                        new DefaultTypedTuple<>("9001", 200.0)
                )));

        ScrollResultDTO<Long> result = feedService.queryFeedBlogIds(1001L, 500L, 0);

        assertThat(result.getList()).containsExactly(9003L, 9002L, 9001L);
        assertThat(result.getMinTime()).isEqualTo(200L);
        assertThat(result.getOffset()).isEqualTo(2);
    }
}
