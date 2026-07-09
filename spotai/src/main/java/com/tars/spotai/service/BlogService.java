package com.tars.spotai.service;

import com.tars.spotai.config.MqEventProperties;
import com.tars.spotai.dto.BlogCreateDTO;
import com.tars.spotai.dto.BlogPublishedMessage;
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

/**
 * 探店笔记核心业务。
 *
 * <p>这里同时维护三类状态：MySQL 中的笔记主体、Redis 中的点赞关系、
 * Redis 关注流中的笔记投递。控制器只负责协议转发，业务规则统一收敛在本类。</p>
 */
@Service
public class BlogService {
    private static final int MAX_IMAGE_COUNT = 9;
    private static final int DEFAULT_LIKED_BLOG_LIMIT = 10;
    private static final int MAX_LIKED_BLOG_LIMIT = 50;
    private static final int LIKE_PREVIEW_LIMIT = 5;
    private static final String LOGIN_REQUIRED = "请先登录";

    /**
     * 点赞状态需要同时维护两个 ZSet：
     * 1. 单篇笔记 -> 点赞用户；
     * 2. 单个用户 -> 点赞笔记。
     *
     * <p>使用 Lua 是为了保证两份索引的写入/删除原子化，避免页面出现
     * “笔记已点赞，但我的点赞列表没有记录”的中间态。</p>
     */
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

    /** 数据库点赞数更新失败时，用这段脚本回滚 Redis 中已经切换的点赞状态。 */
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
    private final MqEventPublisher mqEventPublisher;
    private final MqEventProperties mqEventProperties;

    public BlogService(BlogRepository blogRepository,
                       UserRepository userRepository,
                       ShopRepository shopRepository,
                       FollowRepository followRepository,
                       RedisIdWorker redisIdWorker,
                       StringRedisTemplate stringRedisTemplate,
                       FeedService feedService,
                       MqEventPublisher mqEventPublisher,
                       MqEventProperties mqEventProperties) {
        this.blogRepository = blogRepository;
        this.userRepository = userRepository;
        this.shopRepository = shopRepository;
        this.followRepository = followRepository;
        this.redisIdWorker = redisIdWorker;
        this.stringRedisTemplate = stringRedisTemplate;
        this.feedService = feedService;
        this.mqEventPublisher = mqEventPublisher;
        this.mqEventProperties = mqEventProperties;
    }

    /** 发布探店笔记，并把新笔记推送到作者粉丝的关注流。 */
    public Result<Long> saveBlog(BlogCreateDTO createDTO) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail(LOGIN_REQUIRED);
        }

        Result<Void> validation = validateCreateDTO(createDTO);
        if (!validation.isSuccess()) {
            return Result.fail(validation.getErrorMsg());
        }
        if (shopRepository.findById(createDTO.getShopId()) == null) {
            return Result.fail("店铺不存在");
        }

        Blog blog = createBlog(createDTO, currentUser.getId());
        blogRepository.save(blog);
        BlogPublishedMessage message = new BlogPublishedMessage(currentUser.getId(), blog.getId(), LocalDateTime.now());
        mqEventPublisher.publishOrRun(
                mqEventProperties.getBlogPublishedTopic(),
                message,
                () -> feedService.pushBlogToFollowers(currentUser.getId(), blog.getId())
        );
        return Result.ok(blog.getId());
    }

    public Result<BlogViewDTO> queryBlogById(Long id) {
        if (!isPositiveId(id)) {
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
            return Result.fail(LOGIN_REQUIRED);
        }
        return Result.ok(toViewDTOList(blogRepository.findByUserId(currentUser.getId(), normalizeCurrent(current))));
    }

    public Result<List<BlogViewDTO>> queryMyLikedBlogs() {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail(LOGIN_REQUIRED);
        }
        return Result.ok(queryUserLikedBlogs(currentUser.getId(), DEFAULT_LIKED_BLOG_LIMIT));
    }

    public Result<List<BlogViewDTO>> queryBlogByUser(Long userId, Integer current) {
        if (!isPositiveId(userId)) {
            return Result.fail("用户ID不合法");
        }
        return Result.ok(toViewDTOList(blogRepository.findByUserId(userId, normalizeCurrent(current))));
    }

    public Result<List<BlogViewDTO>> queryBlogByShop(Long shopId, Integer current) {
        if (!isPositiveId(shopId)) {
            return Result.fail("店铺ID不合法");
        }
        if (shopRepository.findById(shopId) == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(toViewDTOList(blogRepository.findByShopId(shopId, normalizeCurrent(current))));
    }

    /** 查询关注流。lastId 和 offset 是 Redis ZSet 滚动分页游标，不是普通页码。 */
    public Result<ScrollResultDTO<BlogViewDTO>> queryBlogOfFollow(Long lastId, Integer offset) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail(LOGIN_REQUIRED);
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

    /** 点赞或取消点赞。Redis 先切换状态，MySQL 点赞计数失败时再回滚 Redis。 */
    public Result<Void> likeBlog(Long id) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail(LOGIN_REQUIRED);
        }
        Blog blog = blogRepository.findById(id);
        if (blog == null) {
            return Result.fail("探店笔记不存在");
        }

        String userId = String.valueOf(currentUser.getId());
        String blogLikedKey = RedisConstants.BLOG_LIKED_KEY + id;
        String userLikedKey = RedisConstants.BLOG_LIKED_USER_KEY + userId;
        String now = String.valueOf(System.currentTimeMillis());
        Long action = stringRedisTemplate.execute(LIKE_SCRIPT, List.of(blogLikedKey, userLikedKey), userId, now, String.valueOf(id));
        if (action == null) {
            return Result.fail("点赞失败，请稍后重试");
        }

        int affectedRows = action > 0 ? blogRepository.increaseLiked(id) : blogRepository.decreaseLiked(id);
        if (affectedRows == 0) {
            stringRedisTemplate.execute(
                    ROLLBACK_LIKE_SCRIPT,
                    List.of(blogLikedKey, userLikedKey),
                    userId,
                    String.valueOf(action),
                    now,
                    String.valueOf(id));
            return Result.fail("点赞失败，请稍后重试");
        }
        return Result.ok(null);
    }

    public Result<Void> deleteBlog(Long id) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail(LOGIN_REQUIRED);
        }
        if (!isPositiveId(id)) {
            return Result.fail("探店笔记ID不合法");
        }
        Blog blog = blogRepository.findById(id);
        if (blog == null) {
            return Result.fail("探店笔记不存在");
        }
        if (!currentUser.getId().equals(blog.getUserId())) {
            return Result.fail("只能删除自己发布的探店笔记");
        }

        int affectedRows = blogRepository.deleteByIdAndUserId(id, currentUser.getId());
        if (affectedRows == 0) {
            return Result.fail("删除失败");
        }
        // 删除笔记后清理点赞集合，避免不存在的笔记仍展示残留点赞头像。
        stringRedisTemplate.delete(RedisConstants.BLOG_LIKED_KEY + id);
        return Result.ok(null);
    }

    public Result<List<UserDTO>> queryBlogLikes(Long id) {
        if (!isPositiveId(id)) {
            return Result.fail("探店笔记ID不合法");
        }
        Set<String> userIds = stringRedisTemplate.opsForZSet()
                .reverseRange(RedisConstants.BLOG_LIKED_KEY + id, 0, LIKE_PREVIEW_LIMIT - 1);
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

    /**
     * 查询用户点赞过的笔记。
     *
     * <p>优先走“用户->笔记”的新索引；如果旧数据没有维护这份索引，
     * 再回退扫描近期笔记，保证老用户仍能看到一部分历史点赞。</p>
     */
    public List<BlogViewDTO> queryUserLikedBlogs(Long userId, int limit) {
        int size = Math.max(1, Math.min(limit, MAX_LIKED_BLOG_LIMIT));
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

    private Blog createBlog(BlogCreateDTO createDTO, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        Blog blog = new Blog();
        blog.setId(redisIdWorker.nextId("blog"));
        blog.setShopId(createDTO.getShopId());
        blog.setUserId(userId);
        blog.setTitle(createDTO.getTitle().trim());
        blog.setImages(normalizeImages(createDTO.getImages()));
        blog.setContent(createDTO.getContent().trim());
        blog.setLiked(0);
        blog.setComments(0);
        blog.setCreateTime(now);
        blog.setUpdateTime(now);
        return blog;
    }

    private Result<Void> validateCreateDTO(BlogCreateDTO createDTO) {
        if (createDTO == null || !isPositiveId(createDTO.getShopId())) {
            return Result.fail("店铺ID不合法");
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

    private boolean isPositiveId(Long id) {
        return id != null && id > 0;
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
