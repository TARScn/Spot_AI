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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class BlogController {
    private final BlogService blogService;

    public BlogController(BlogService blogService) {
        this.blogService = blogService;
    }

    @PostMapping("/blog")
    public Result<String> saveBlog(@Valid @RequestBody BlogCreateDTO createDTO) {
        Result<Long> result = blogService.saveBlog(createDTO);
        if (!result.isSuccess()) {
            return Result.fail(result.getErrorMsg());
        }
        return Result.ok(String.valueOf(result.getData()));
    }

    @GetMapping("/blog/hot")
    public Result<List<BlogViewDTO>> queryHotBlog(@RequestParam(defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    @GetMapping("/blog/recent")
    public Result<List<BlogViewDTO>> queryRecentBlog(@RequestParam(defaultValue = "1") Integer current) {
        return blogService.queryRecentBlog(current);
    }

    @GetMapping("/blog/of/me")
    public Result<List<BlogViewDTO>> queryMyBlog(@RequestParam(defaultValue = "1") Integer current) {
        return blogService.queryMyBlog(current);
    }

    @GetMapping("/blog/liked/me")
    public Result<List<BlogViewDTO>> queryMyLikedBlogs() {
        return blogService.queryMyLikedBlogs();
    }

    @GetMapping("/blog/of/user")
    public Result<List<BlogViewDTO>> queryBlogByUser(@RequestParam("id") Long userId,
                                                     @RequestParam(defaultValue = "1") Integer current) {
        return blogService.queryBlogByUser(userId, current);
    }

    @GetMapping("/blog/of/shop")
    public Result<List<BlogViewDTO>> queryBlogByShop(@RequestParam("id") Long shopId,
                                                     @RequestParam(defaultValue = "1") Integer current) {
        return blogService.queryBlogByShop(shopId, current);
    }

    @GetMapping("/blog/of/follow")
    public Result<ScrollResultDTO<BlogViewDTO>> queryBlogOfFollow(@RequestParam Long lastId,
                                                                  @RequestParam(defaultValue = "0") Integer offset) {
        return blogService.queryBlogOfFollow(lastId, offset);
    }

    @GetMapping("/blog/{id}")
    public Result<BlogViewDTO> queryBlogById(@PathVariable Long id) {
        return blogService.queryBlogById(id);
    }

    @PutMapping("/blog/like/{id}")
    public Result<Void> likeBlog(@PathVariable Long id) {
        return blogService.likeBlog(id);
    }

    @DeleteMapping("/blog/{id}")
    public Result<Void> deleteBlog(@PathVariable Long id) {
        return blogService.deleteBlog(id);
    }

    @GetMapping("/blog/likes/{id}")
    public Result<List<UserDTO>> queryBlogLikes(@PathVariable Long id) {
        return blogService.queryBlogLikes(id);
    }
}
