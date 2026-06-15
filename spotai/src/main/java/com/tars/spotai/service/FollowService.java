package com.tars.spotai.service;

import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.entity.User;
import com.tars.spotai.repository.FollowRepository;
import com.tars.spotai.repository.UserRepository;
import com.tars.spotai.utils.RedisConstants;
import com.tars.spotai.utils.RedisIdWorker;
import com.tars.spotai.utils.UserHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class FollowService {
    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final RedisIdWorker redisIdWorker;
    private final StringRedisTemplate stringRedisTemplate;

    public FollowService(FollowRepository followRepository,
                         UserRepository userRepository,
                         RedisIdWorker redisIdWorker,
                         StringRedisTemplate stringRedisTemplate) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.redisIdWorker = redisIdWorker;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Result<Void> follow(Long followUserId, Boolean isFollow) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }
        Result<Void> validation = validateFollowTarget(currentUser.getId(), followUserId);
        if (!validation.isSuccess()) {
            return Result.fail(validation.getErrorMsg());
        }
        return Boolean.TRUE.equals(isFollow)
                ? followUser(currentUser.getId(), followUserId)
                : unfollowUser(currentUser.getId(), followUserId);
    }

    public Result<Boolean> isFollow(Long followUserId) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }
        if (followUserId == null || followUserId <= 0) {
            return Result.fail("用户ID不合法");
        }
        String key = RedisConstants.FOLLOW_KEY + currentUser.getId();
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, String.valueOf(followUserId));
        if (Boolean.TRUE.equals(isMember)) {
            return Result.ok(true);
        }
        boolean exists = followRepository.exists(currentUser.getId(), followUserId);
        if (exists) {
            stringRedisTemplate.opsForSet().add(key, String.valueOf(followUserId));
        }
        return Result.ok(exists);
    }

    public Result<List<UserDTO>> commonFollow(Long targetUserId) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }
        if (targetUserId == null || targetUserId <= 0) {
            return Result.fail("用户ID不合法");
        }
        loadFollowSet(currentUser.getId());
        loadFollowSet(targetUserId);
        Set<String> commonIds = stringRedisTemplate.opsForSet()
                .intersect(RedisConstants.FOLLOW_KEY + currentUser.getId(), RedisConstants.FOLLOW_KEY + targetUserId);
        if (commonIds == null || commonIds.isEmpty()) {
            return Result.ok(List.of());
        }
        List<UserDTO> users = new ArrayList<>();
        for (String userId : commonIds) {
            User user = userRepository.findById(Long.valueOf(userId));
            if (user != null) {
                users.add(new UserDTO(user.getId(), user.getNickName(), user.getIcon()));
            }
        }
        return Result.ok(users);
    }

    private Result<Void> followUser(Long currentUserId, Long followUserId) {
        if (followRepository.exists(currentUserId, followUserId)) {
            syncFollowToRedis(currentUserId, followUserId);
            return Result.ok(null);
        }
        try {
            followRepository.save(redisIdWorker.nextId("follow"), currentUserId, followUserId);
        } catch (DuplicateKeyException ignored) {
            // Unique index makes repeated follow requests idempotent.
        }
        syncFollowToRedis(currentUserId, followUserId);
        return Result.ok(null);
    }

    private Result<Void> unfollowUser(Long currentUserId, Long followUserId) {
        followRepository.delete(currentUserId, followUserId);
        stringRedisTemplate.opsForSet().remove(RedisConstants.FOLLOW_KEY + currentUserId, String.valueOf(followUserId));
        stringRedisTemplate.opsForSet().remove(RedisConstants.FOLLOWERS_KEY + followUserId, String.valueOf(currentUserId));
        return Result.ok(null);
    }

    private Result<Void> validateFollowTarget(Long currentUserId, Long followUserId) {
        if (followUserId == null || followUserId <= 0) {
            return Result.fail("用户ID不合法");
        }
        if (currentUserId.equals(followUserId)) {
            return Result.fail("不能关注自己");
        }
        if (userRepository.findById(followUserId) == null) {
            return Result.fail("用户不存在");
        }
        return Result.ok(null);
    }

    private void syncFollowToRedis(Long currentUserId, Long followUserId) {
        stringRedisTemplate.opsForSet().add(RedisConstants.FOLLOW_KEY + currentUserId, String.valueOf(followUserId));
        stringRedisTemplate.opsForSet().add(RedisConstants.FOLLOWERS_KEY + followUserId, String.valueOf(currentUserId));
    }

    private void loadFollowSet(Long userId) {
        String key = RedisConstants.FOLLOW_KEY + userId;
        Boolean exists = stringRedisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(exists)) {
            return;
        }
        List<Long> followUserIds = followRepository.findFollowUserIds(userId);
        if (!followUserIds.isEmpty()) {
            String[] values = followUserIds.stream().map(String::valueOf).toArray(String[]::new);
            stringRedisTemplate.opsForSet().add(key, values);
        }
    }
}
