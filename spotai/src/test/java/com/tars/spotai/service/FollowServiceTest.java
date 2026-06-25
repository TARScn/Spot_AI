package com.tars.spotai.service;

import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.entity.User;
import com.tars.spotai.repository.FollowRepository;
import com.tars.spotai.repository.UserRepository;
import com.tars.spotai.utils.RedisConstants;
import com.tars.spotai.utils.RedisIdWorker;
import com.tars.spotai.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {
    @Mock
    private FollowRepository followRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RedisIdWorker redisIdWorker;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private FeedService feedService;
    @Mock
    private SetOperations<String, String> setOperations;

    private FollowService followService;

    @BeforeEach
    void setUp() {
        followService = new FollowService(followRepository, userRepository, redisIdWorker, stringRedisTemplate, feedService);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void followCreatesRelationAndCachesSets() {
        UserHolder.saveUser(new UserDTO(1001L, "alice", ""));
        when(userRepository.findById(1002L)).thenReturn(user(1002L, "bob"));
        when(followRepository.exists(1001L, 1002L)).thenReturn(false);
        when(redisIdWorker.nextId("follow")).thenReturn(9001L);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);

        Result<Void> result = followService.follow(1002L, true);

        assertThat(result.isSuccess()).isTrue();
        verify(followRepository).save(9001L, 1001L, 1002L);
        verify(setOperations).add(RedisConstants.FOLLOW_KEY + 1001L, "1002");
        verify(setOperations).add(RedisConstants.FOLLOWERS_KEY + 1002L, "1001");
    }

    @Test
    void followRejectsSelfFollow() {
        UserHolder.saveUser(new UserDTO(1001L, "alice", ""));

        Result<Void> result = followService.follow(1001L, true);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMsg()).isEqualTo("不能关注自己");
        verify(followRepository, never()).save(null, null, null);
    }

    @Test
    void unfollowDeletesRelationAndCaches() {
        UserHolder.saveUser(new UserDTO(1001L, "alice", ""));
        when(userRepository.findById(1002L)).thenReturn(user(1002L, "bob"));
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);

        Result<Void> result = followService.follow(1002L, false);

        assertThat(result.isSuccess()).isTrue();
        verify(followRepository).delete(1001L, 1002L);
        verify(setOperations).remove(RedisConstants.FOLLOW_KEY + 1001L, "1002");
        verify(setOperations).remove(RedisConstants.FOLLOWERS_KEY + 1002L, "1001");
    }

    @Test
    void isFollowFallsBackToDatabaseAndWarmsCache() {
        UserHolder.saveUser(new UserDTO(1001L, "alice", ""));
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(RedisConstants.FOLLOW_KEY + 1001L, "1002")).thenReturn(false);
        when(followRepository.exists(1001L, 1002L)).thenReturn(true);

        Result<Boolean> result = followService.isFollow(1002L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isTrue();
        verify(setOperations).add(RedisConstants.FOLLOW_KEY + 1001L, "1002");
    }

    @Test
    void commonFollowUsesRedisIntersection() {
        UserHolder.saveUser(new UserDTO(1001L, "alice", ""));
        when(stringRedisTemplate.hasKey(RedisConstants.FOLLOW_KEY + 1001L)).thenReturn(true);
        when(stringRedisTemplate.hasKey(RedisConstants.FOLLOW_KEY + 1002L)).thenReturn(true);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.intersect(RedisConstants.FOLLOW_KEY + 1001L, RedisConstants.FOLLOW_KEY + 1002L))
                .thenReturn(new LinkedHashSet<>(List.of("1003")));
        when(userRepository.findById(1003L)).thenReturn(user(1003L, "carol"));

        Result<List<UserDTO>> result = followService.commonFollow(1002L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).extracting(UserDTO::getId).containsExactly(1003L);
    }

    private User user(Long id, String nickName) {
        User user = new User();
        user.setId(id);
        user.setNickName(nickName);
        user.setIcon("");
        return user;
    }
}
