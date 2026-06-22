package com.tars.spotai.service;

import com.tars.spotai.dto.ScrollResultDTO;
import com.tars.spotai.entity.Blog;
import com.tars.spotai.repository.BlogRepository;
import com.tars.spotai.repository.FollowRepository;
import com.tars.spotai.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class FeedService {
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final FollowRepository followRepository;
    private final BlogRepository blogRepository;
    private final StringRedisTemplate stringRedisTemplate;

    public FeedService(FollowRepository followRepository,
                       BlogRepository blogRepository,
                       StringRedisTemplate stringRedisTemplate) {
        this.followRepository = followRepository;
        this.blogRepository = blogRepository;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void pushBlogToFollowers(Long authorId, Long blogId) {
        List<Long> followerIds = followRepository.findFollowerIds(authorId);
        if (followerIds.isEmpty()) {
            return;
        }
        double now = System.currentTimeMillis();
        for (Long followerId : followerIds) {
            stringRedisTemplate.opsForZSet()
                    .add(RedisConstants.FEED_KEY + followerId, String.valueOf(blogId), now);
        }
    }

    public void backfillAuthorBlogsToFollower(Long followerId, Long authorId) {
        List<Blog> blogs = blogRepository.findRecentByUserId(authorId, DEFAULT_PAGE_SIZE);
        if (blogs.isEmpty()) {
            return;
        }
        for (Blog blog : blogs) {
            long score = blog.getCreateTime() == null
                    ? System.currentTimeMillis()
                    : java.sql.Timestamp.valueOf(blog.getCreateTime()).getTime();
            stringRedisTemplate.opsForZSet()
                    .add(RedisConstants.FEED_KEY + followerId, String.valueOf(blog.getId()), score);
        }
    }

    public List<Long> queryAuthorRecentBlogIds(Long authorId, int limit) {
        return blogRepository.findRecentByUserId(authorId, limit).stream()
                .map(Blog::getId)
                .toList();
    }

    public ScrollResultDTO<Long> queryFeedBlogIds(Long userId, Long lastId, Integer offset) {
        long max = lastId == null || lastId <= 0 ? System.currentTimeMillis() : lastId;
        int skip = offset == null || offset < 0 ? 0 : offset;
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(RedisConstants.FEED_KEY + userId, 0, max, skip, DEFAULT_PAGE_SIZE);
        if (tuples == null || tuples.isEmpty()) {
            return queryFeedFromDatabase(userId);
        }

        List<Long> blogIds = new ArrayList<>();
        long minTime = 0L;
        int nextOffset = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String value = tuple.getValue();
            Double score = tuple.getScore();
            if (value == null || score == null) {
                continue;
            }
            blogIds.add(Long.valueOf(value));
            long time = score.longValue();
            if (minTime == 0L || time < minTime) {
                minTime = time;
                nextOffset = 1;
            } else if (time == minTime) {
                nextOffset++;
            }
        }
        return new ScrollResultDTO<>(blogIds, minTime, nextOffset);
    }

    private ScrollResultDTO<Long> queryFeedFromDatabase(Long userId) {
        List<Long> followUserIds = followRepository.findFollowUserIds(userId);
        if (followUserIds.isEmpty()) {
            return new ScrollResultDTO<>(List.of(), 0L, 0);
        }
        List<Blog> blogs = blogRepository.findRecentByUserIds(followUserIds, DEFAULT_PAGE_SIZE);
        if (blogs.isEmpty()) {
            return new ScrollResultDTO<>(List.of(), 0L, 0);
        }
        List<Long> blogIds = new ArrayList<>();
        long minTime = 0L;
        for (Blog blog : blogs) {
            blogIds.add(blog.getId());
            long score = blog.getCreateTime() == null
                    ? System.currentTimeMillis()
                    : java.sql.Timestamp.valueOf(blog.getCreateTime()).getTime();
            stringRedisTemplate.opsForZSet()
                    .add(RedisConstants.FEED_KEY + userId, String.valueOf(blog.getId()), score);
            if (minTime == 0L || score < minTime) {
                minTime = score;
            }
        }
        return new ScrollResultDTO<>(blogIds, minTime, 1);
    }
}
