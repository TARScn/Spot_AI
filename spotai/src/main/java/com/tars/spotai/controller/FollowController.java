package com.tars.spotai.controller;

import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.service.FollowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class FollowController {
    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @PutMapping("/follow/{id}/{isFollow}")
    public Result<Void> follow(@PathVariable("id") Long followUserId,
                               @PathVariable Boolean isFollow) {
        return followService.follow(followUserId, isFollow);
    }

    @GetMapping("/follow/or/not/{id}")
    public Result<Boolean> isFollow(@PathVariable("id") Long followUserId) {
        return followService.isFollow(followUserId);
    }

    @GetMapping("/follow/common/{id}")
    public Result<List<UserDTO>> commonFollow(@PathVariable("id") Long targetUserId) {
        return followService.commonFollow(targetUserId);
    }
}
