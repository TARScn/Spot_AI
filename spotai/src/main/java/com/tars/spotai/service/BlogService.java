package com.tars.spotai.service;

import com.tars.spotai.dto.BlogCreateDTO;
import com.tars.spotai.dto.BlogViewDTO;
import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.ScrollResultDTO;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.entity.Blog;
import com.tars.spotai.entity.User;
import com.tars.spotai.repository.BlogRepository;
import com.tars.spotai.repository.FollowRepository;
import com.tars.spotai.repository.ShopRepository;
import com.tars.spotai.repository.UserRepository;
import com.tars.spotai.utils.RedisConstants;
import com.tars.spotai.utils.RedisIdWorker;
import com.tars.spotai.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class BlogService {
    private static final int MAX_IMAGE_COUNT = 9;
    private static final DefaultRedisScript<Long> LIKE_SCRIPT = new DefaultRedisScript<>(
            """
                    if redis.call('zscore', KEYS[1], ARGV[1]) then
                      redis.call('zrem', KEYS[1], ARGV[1])
                      redis.call('zrem', KEYS[2], ARGV[3])
                      return -1
                    end
                    redis.call('zadd', KEYS[1], ARGV[2], ARGV[1])
                    redis.call('zadd', KEYS[2], ARGV[2], ARGV[3])
                    return 1
                    """,
            Long.class
    );
    private static final DefaultRedisScript<Long> ROLLBACK_LIKE_SCRIPT = new DefaultRedisScript<>(
            """
                    if ARGV[2] == '1' then
                      redis.call('zrem', KEYS[1], ARGV[1])
                      redis.call('zrem', KEYS[2], ARGV[4])
                      return 1
                    end
                    redis.call('zadd', KEYS[1], ARGV[3], ARGV[1])
                    redis.call('zadd', KEYS[2], ARGV[3], ARGV[4])
                    return 1
                    """,
            Long.class
    );

    private final BlogRepository blogRepository;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final FollowRepository followRepository;
    private final RedisIdWorker redisIdWorker;
    private final StringRedisTemplate stringRedisTemplate;
    private final FeedService feedService;

    public BlogService(BlogRepository blogRepository,
                       UserRepository userRepository,
                       ShopRepository shopRepository,
                       FollowRepository followRepository,
                       RedisIdWorker redisIdWorker,
                       StringRedisTemplate stringRedisTemplate,
                       FeedService feedService) {
        this.blogRepository = blogRepository;
        this.userRepository = userRepository;
        this.shopRepository = shopRepository;
        this.followRepository = followRepository;
        this.redisIdWorker = redisIdWorker;
        this.stringRedisTemplate = stringRedisTemplate;
        this.feedService = feedService;
    }

    public Result<Long> saveBlog(BlogCreateDTO createDTO) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }
        Result<Void> validation = validateCreateDTO(createDTO);
        if (!validation.isSuccess()) {
            return Result.fail(validation.getErrorMsg());
        }
        if (shopRepository.findById(createDTO.getShopId()) == null) {
            return Result.fail("商户不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        Blog blog = new Blog();
        blog.setId(redisIdWorker.nextId("blog"));
        blog.setShopId(createDTO.getShopId());
        blog.setUserId(currentUser.getId());
        blog.setTitle(createDTO.getTitle().trim());
        blog.setImages(normalizeImages(createDTO.getImages()));
        blog.setContent(createDTO.getContent().trim());
        blog.setLiked(0);
        blog.setComments(0);
        blog.setCreateTime(now);
        blog.setUpdateTime(now);
        blogRepository.save(blog);
        feedService.pushBlogToFollowers(currentUser.getId(), blog.getId());
        return Result.ok(blog.getId());
    }

    public Result<BlogViewDTO> queryBlogById(Long id) {
        if (id == null || id <= 0) {
            return Result.fail("探店笔记ID不合法");
        }
        Blog blog = blogRepository.findById(id);
        if (blog == null) {
            return Result.fail("探店笔记不存在");
        }
        return Result.ok(toViewDTO(blog));
    }

    public Result<List<BlogViewDTO>> queryHotBlog(Integer current) {
        return Result.ok(toViewDTOList(blogRepository.findHot(normalizeCurrent(current))));
    }

    public Result<List<BlogViewDTO>> queryRecentBlog(Integer current) {
        return Result.ok(toViewDTOList(blogRepository.findRecentPaged(normalizeCurrent(current))));
    }

    public Result<List<BlogViewDTO>> queryMyBlog(Integer current) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }
        return Result.ok(toViewDTOList(blogRepository.findByUserId(currentUser.getId(), normalizeCurrent(current))));
    }

    public Result<List<BlogViewDTO>> queryMyLikedBlogs() {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("璇峰厛鐧诲綍");
        }
        return Result.ok(queryUserLikedBlogs(currentUser.getId(), 10));
    }

    public Result<List<BlogViewDTO>> queryBlogByUser(Long userId, Integer current) {
        if (userId == null || userId <= 0) {
            return Result.fail("用户ID不合法");
        }
        return Result.ok(toViewDTOList(blogRepository.findByUserId(userId, normalizeCurrent(current))));
    }

    public Result<List<BlogViewDTO>> queryBlogByShop(Long shopId, Integer current) {
        if (shopId == null || shopId <= 0) {
            return Result.fail("商户ID不合法");
        }
        if (shopRepository.findById(shopId) == null) {
            return Result.fail("商户不存在");
        }
        return Result.ok(toViewDTOList(blogRepository.findByShopId(shopId, normalizeCurrent(current))));
    }

    public Result<ScrollResultDTO<BlogViewDTO>> queryBlogOfFollow(Long lastId, Integer offset) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }
        ScrollResultDTO<Long> feedBlogIds = feedService.queryFeedBlogIds(currentUser.getId(), lastId, offset);
        List<BlogViewDTO> blogs = new ArrayList<>();
        for (Long blogId : feedBlogIds.getList()) {
            Blog blog = blogRepository.findById(blogId);
            if (blog != null) {
                blogs.add(toViewDTO(blog));
            }
        }
        return Result.ok(new ScrollResultDTO<>(blogs, feedBlogIds.getMinTime(), feedBlogIds.getOffset()));
    }

    public Result<Void> likeBlog(Long id) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }
        Blog blog = blogRepository.findById(id);
        if (blog == null) {
            return Result.fail("探店笔记不存在");
        }

        String userId = String.valueOf(currentUser.getId());
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        String userLikedKey = RedisConstants.BLOG_LIKED_USER_KEY + userId;
        String now = String.valueOf(System.currentTimeMillis());
        Long action = stringRedisTemplate.execute(LIKE_SCRIPT, List.of(key, userLikedKey), userId, now, String.valueOf(id));
        if (action == null) {
            return Result.fail("点赞失败，请稍后重试");
        }

        int affectedRows = action > 0 ? blogRepository.increaseLiked(id) : blogRepository.decreaseLiked(id);
        if (affectedRows == 0) {
            stringRedisTemplate.execute(ROLLBACK_LIKE_SCRIPT, List.of(key, userLikedKey), userId, String.valueOf(action), now, String.valueOf(id));
            return Result.fail("点赞失败，请稍后重试");
        }
        return Result.ok(null);
    }

    public Result<Void> deleteBlog(Long id) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("璇峰厛鐧诲綍");
        }
        if (id == null || id <= 0) {
            return Result.fail("鎺㈠簵绗旇ID涓嶅悎娉?");
        }
        Blog blog = blogRepository.findById(id);
        if (blog == null) {
            return Result.fail("鎺㈠簵绗旇涓嶅瓨鍦?");
        }
        if (!currentUser.getId().equals(blog.getUserId())) {
            return Result.fail("鏃犳潈鍒犻櫎璇ョ瑪璁?");
        }
        int affectedRows = blogRepository.deleteByIdAndUserId(id, currentUser.getId());
        if (affectedRows == 0) {
            return Result.fail("鍒犻櫎澶辫触");
        }
        stringRedisTemplate.delete(RedisConstants.BLOG_LIKED_KEY + id);
        return Result.ok(null);
    }

    public Result<List<UserDTO>> queryBlogLikes(Long id) {
        if (id == null || id <= 0) {
            return Result.fail("探店笔记ID不合法");
        }
        Set<String> userIds = stringRedisTemplate.opsForZSet()
                .reverseRange(RedisConstants.BLOG_LIKED_KEY + id, 0, 4);
        if (userIds == null || userIds.isEmpty()) {
            return Result.ok(List.of());
        }
        List<UserDTO> users = new ArrayList<>();
        for (String userId : userIds) {
            User user = userRepository.findById(Long.valueOf(userId));
            if (user != null) {
                users.add(new UserDTO(user.getId(), user.getNickName(), user.getIcon()));
            }
        }
        return Result.ok(users);
    }

    public List<BlogViewDTO> queryUserLikedBlogs(Long userId, int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        Set<String> blogIds = stringRedisTemplate.opsForZSet()
                .reverseRange(RedisConstants.BLOG_LIKED_USER_KEY + userId, 0, size - 1);
        List<Blog> likedBlogs = new ArrayList<>();
        if (blogIds != null && !blogIds.isEmpty()) {
            for (String blogId : blogIds) {
                Blog blog = blogRepository.findById(Long.valueOf(blogId));
                if (blog != null) {
                    likedBlogs.add(blog);
                }
            }
        }
        if (likedBlogs.isEmpty()) {
            likedBlogs = blogRepository.findRecent(200).stream()
                    .filter(blog -> stringRedisTemplate.opsForZSet()
                            .score(RedisConstants.BLOG_LIKED_KEY + blog.getId(), String.valueOf(userId)) != null)
                    .limit(size)
                    .toList();
        }
        return toViewDTOList(likedBlogs.stream().limit(size).toList());
    }

    private Result<Void> validateCreateDTO(BlogCreateDTO createDTO) {
        if (createDTO == null || createDTO.getShopId() == null || createDTO.getShopId() <= 0) {
            return Result.fail("商户ID不合法");
        }
        if (!StringUtils.hasText(createDTO.getTitle())) {
            return Result.fail("标题不能为空");
        }
        if (!StringUtils.hasText(createDTO.getContent())) {
            return Result.fail("正文不能为空");
        }
        String images = normalizeImages(createDTO.getImages());
        if (StringUtils.hasText(images) && images.split(",").length > MAX_IMAGE_COUNT) {
            return Result.fail("图片数量不能超过9张");
        }
        return Result.ok(null);
    }

    private List<BlogViewDTO> toViewDTOList(List<Blog> blogs) {
        return blogs.stream().map(this::toViewDTO).toList();
    }

    private BlogViewDTO toViewDTO(Blog blog) {
        BlogViewDTO dto = new BlogViewDTO();
        dto.setId(blog.getId());
        dto.setShopId(blog.getShopId());
        dto.setUserId(blog.getUserId());
        dto.setTitle(blog.getTitle());
        dto.setImages(blog.getImages());
        dto.setContent(blog.getContent());
        dto.setLiked(blog.getLiked() == null ? 0 : blog.getLiked());
        dto.setComments(blog.getComments() == null ? 0 : blog.getComments());
        dto.setCreateTime(blog.getCreateTime());
        dto.setUpdateTime(blog.getUpdateTime());
        fillAuthor(dto, blog.getUserId());
        dto.setIsLike(isCurrentUserLiked(blog.getId()));
        dto.setIsFollow(isCurrentUserFollowed(blog.getUserId()));
        return dto;
    }

    private void fillAuthor(BlogViewDTO dto, Long userId) {
        User user = userRepository.findById(userId);
        if (user == null) {
            return;
        }
        dto.setName(user.getNickName());
        dto.setIcon(user.getIcon());
    }

    private boolean isCurrentUserLiked(Long blogId) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return false;
        }
        Double score = stringRedisTemplate.opsForZSet()
                .score(RedisConstants.BLOG_LIKED_KEY + blogId, String.valueOf(currentUser.getId()));
        return score != null;
    }

    private boolean isCurrentUserFollowed(Long authorId) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null || authorId == null || currentUser.getId().equals(authorId)) {
            return false;
        }
        return followRepository.exists(currentUser.getId(), authorId);
    }

    private int normalizeCurrent(Integer current) {
        return current == null || current <= 0 ? 1 : current;
    }

    private String normalizeImages(String images) {
        if (!StringUtils.hasText(images)) {
            return "";
        }
        return images.trim();
    }
}
