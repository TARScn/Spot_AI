package com.tars.spotai.controller;

import com.tars.spotai.dto.BlogCreateDTO;
import com.tars.spotai.dto.BlogViewDTO;
import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.ScrollResultDTO;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.service.BlogService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 探店笔记接口。
 *
 * <p>前端“发现/关注/我的”三个页面都会调用这里的接口，因此这里只做参数转发和
 * 返回值适配，具体权限、点赞、关注流等业务规则统一收敛在 {@link BlogService}。</p>
 */
@RestController
@RequestMapping("/blog")
public class BlogController {
    private final BlogService blogService;

    public BlogController(BlogService blogService) {
        this.blogService = blogService;
    }

    /**
     * 发布探店笔记。
     *
     * <p>历史前端期望返回字符串 ID，这里保留 String 返回值，避免破坏现有调用。</p>
     */
    @PostMapping
    public Result<String> saveBlog(@Valid @RequestBody BlogCreateDTO createDTO) {
        Result<Long> result = blogService.saveBlog(createDTO);
        if (!result.isSuccess()) {
            return Result.fail(result.getErrorMsg());
        }
        return Result.ok(String.valueOf(result.getData()));
    }

    /** 首页热门笔记。 */
    @GetMapping("/hot")
    public Result<List<BlogViewDTO>> queryHotBlog(@RequestParam(defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    /** 发现页按时间倒序展示的笔记流。 */
    @GetMapping("/recent")
    public Result<List<BlogViewDTO>> queryRecentBlog(@RequestParam(defaultValue = "1") Integer current) {
        return blogService.queryRecentBlog(current);
    }

    /** 当前登录用户发布过的笔记。 */
    @GetMapping("/of/me")
    public Result<List<BlogViewDTO>> queryMyBlog(@RequestParam(defaultValue = "1") Integer current) {
        return blogService.queryMyBlog(current);
    }

    /** 当前登录用户点赞过的笔记，用于“我的”页面。 */
    @GetMapping("/liked/me")
    public Result<List<BlogViewDTO>> queryMyLikedBlogs() {
        return blogService.queryMyLikedBlogs();
    }

    /** 指定用户主页中的笔记列表。 */
    @GetMapping("/of/user")
    public Result<List<BlogViewDTO>> queryBlogByUser(@RequestParam("id") Long userId,
                                                     @RequestParam(defaultValue = "1") Integer current) {
        return blogService.queryBlogByUser(userId, current);
    }

    /** 店铺详情页下的探店笔记列表。 */
    @GetMapping("/of/shop")
    public Result<List<BlogViewDTO>> queryBlogByShop(@RequestParam("id") Long shopId,
                                                     @RequestParam(defaultValue = "1") Integer current) {
        return blogService.queryBlogByShop(shopId, current);
    }

    /** 关注流滚动分页：lastId + offset 对应 Redis ZSet 的滚动游标。 */
    @GetMapping("/of/follow")
    public Result<ScrollResultDTO<BlogViewDTO>> queryBlogOfFollow(@RequestParam Long lastId,
                                                                  @RequestParam(defaultValue = "0") Integer offset) {
        return blogService.queryBlogOfFollow(lastId, offset);
    }

    /** 笔记详情。 */
    @GetMapping("/{id}")
    public Result<BlogViewDTO> queryBlogById(@PathVariable Long id) {
        return blogService.queryBlogById(id);
    }

    /** 点赞/取消点赞，同一个接口按当前状态自动切换。 */
    @PutMapping("/like/{id}")
    public Result<Void> likeBlog(@PathVariable Long id) {
        return blogService.likeBlog(id);
    }

    /** 删除自己的笔记。 */
    @DeleteMapping("/{id}")
    public Result<Void> deleteBlog(@PathVariable Long id) {
        return blogService.deleteBlog(id);
    }

    /** 查询前几个点赞用户头像，用于详情页社交证明。 */
    @GetMapping("/likes/{id}")
    public Result<List<UserDTO>> queryBlogLikes(@PathVariable Long id) {
        return blogService.queryBlogLikes(id);
    }
}
