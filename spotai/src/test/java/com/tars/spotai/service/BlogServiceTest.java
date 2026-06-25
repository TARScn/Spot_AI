package com.tars.spotai.service;

import com.tars.spotai.dto.BlogCreateDTO;
import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.entity.Blog;
import com.tars.spotai.entity.Shop;
import com.tars.spotai.entity.User;
import com.tars.spotai.repository.BlogRepository;
import com.tars.spotai.repository.FollowRepository;
import com.tars.spotai.repository.ShopRepository;
import com.tars.spotai.repository.UserRepository;
import com.tars.spotai.utils.RedisConstants;
import com.tars.spotai.utils.RedisIdWorker;
import com.tars.spotai.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogServiceTest {
    @Mock
    private BlogRepository blogRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ShopRepository shopRepository;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private RedisIdWorker redisIdWorker;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private FeedService feedService;
    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private BlogService blogService;

    @BeforeEach
    void setUp() {
        blogService = new BlogService(blogRepository, userRepository, shopRepository, followRepository, redisIdWorker, stringRedisTemplate, feedService);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void saveBlogCreatesBlogForCurrentUser() {
        UserHolder.saveUser(new UserDTO(1001L, "alice", ""));
        BlogCreateDTO createDTO = createDTO();
        when(shopRepository.findById(1L)).thenReturn(new Shop());
        when(redisIdWorker.nextId("blog")).thenReturn(9001L);

        Result<Long> result = blogService.saveBlog(createDTO);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(9001L);
        ArgumentCaptor<Blog> captor = ArgumentCaptor.forClass(Blog.class);
        verify(blogRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1001L);
        assertThat(captor.getValue().getLiked()).isZero();
        assertThat(captor.getValue().getComments()).isZero();
        verify(feedService).pushBlogToFollowers(1001L, 9001L);
    }

    @Test
    void saveBlogFailsWhenUserIsNotLoggedIn() {
        Result<Long> result = blogService.saveBlog(createDTO());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMsg()).isEqualTo("请先登录");
        verify(blogRepository, never()).save(any());
        verify(feedService, never()).pushBlogToFollowers(any(), any());
    }

    @Test
    void likeBlogAddsLikeWhenRedisScriptReturnsPositiveAction() {
        UserHolder.saveUser(new UserDTO(1001L, "alice", ""));
        when(blogRepository.findById(9001L)).thenReturn(blog(9001L, 1002L));
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), eq("1001"), any(), eq("9001")))
                .thenReturn(1L);
        when(blogRepository.increaseLiked(9001L)).thenReturn(1);

        Result<Void> result = blogService.likeBlog(9001L);

        assertThat(result.isSuccess()).isTrue();
        verify(blogRepository).increaseLiked(9001L);
        verify(blogRepository, never()).decreaseLiked(9001L);
    }

    @Test
    void queryBlogLikesReturnsUsersInRedisOrder() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange(RedisConstants.BLOG_LIKED_KEY + 9001L, 0, 4))
                .thenReturn(new LinkedHashSet<>(List.of("1003", "1002")));
        when(userRepository.findById(1003L)).thenReturn(user(1003L, "third"));
        when(userRepository.findById(1002L)).thenReturn(user(1002L, "second"));

        Result<List<UserDTO>> result = blogService.queryBlogLikes(9001L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).extracting(UserDTO::getId).containsExactly(1003L, 1002L);
        assertThat(result.getData()).extracting(UserDTO::getNickName).containsExactly("third", "second");
    }

    private BlogCreateDTO createDTO() {
        BlogCreateDTO createDTO = new BlogCreateDTO();
        createDTO.setShopId(1L);
        createDTO.setTitle("A nice shop");
        createDTO.setImages("/a.jpg,/b.jpg");
        createDTO.setContent("Good food and service.");
        return createDTO;
    }

    private Blog blog(Long id, Long userId) {
        Blog blog = new Blog();
        blog.setId(id);
        blog.setUserId(userId);
        return blog;
    }

    private User user(Long id, String nickName) {
        User user = new User();
        user.setId(id);
        user.setNickName(nickName);
        user.setIcon("");
        return user;
    }
}
