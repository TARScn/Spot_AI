package com.tars.spotai.controller;

import com.tars.spotai.dto.LoginFormDTO;
import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.dto.UserProfileDTO;
import com.tars.spotai.service.SignService;
import com.tars.spotai.service.UserProfileService;
import com.tars.spotai.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for user authentication and profile operations.
 * Provides endpoints for sending verification codes, logging in, and retrieving the current user.
 */
@RestController
public class UserController {
    /* 1. 依赖注入 */
    private final UserService userService;
    private final SignService signService;
    private final UserProfileService userProfileService;

    public UserController(UserService userService, SignService signService, UserProfileService userProfileService) {
        this.userService = userService;
        this.signService = signService;
        this.userProfileService = userProfileService;
    }

    /* 2. 发送登录验证码 */
    @PostMapping("/user/code")
    public Result<Void> code(@RequestParam String email) {
        return userService.sendCode(email);
    }

    /* 3. 邮箱 + 验证码登录，成功返回 token */
    @PostMapping("/user/login")
    public Result<String> login(@Valid @RequestBody LoginFormDTO loginForm) {
        return userService.login(loginForm);
    }

    /* 4. 获取当前登录用户信息 */
    @GetMapping("/user/me")
    public Result<UserDTO> me() {
        return userService.me();
    }

    @GetMapping("/user/profile")
    public Result<UserProfileDTO> profile() {
        return userProfileService.queryCurrentProfile();
    }

    @PostMapping("/user/sign")
    public Result<Void> sign() {
        return signService.sign();
    }

    @GetMapping("/user/sign/count")
    public Result<Integer> signCount() {
        return signService.countContinuousSignDays();
    }
}
