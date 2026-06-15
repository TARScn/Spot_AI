package com.tars.spotai.controller;

import com.tars.spotai.dto.LoginFormDTO;
import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.service.SignService;
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

    public UserController(UserService userService, SignService signService) {
        this.userService = userService;
        this.signService = signService;
    }

    /* 2. 发送登录验证码 */
    @PostMapping("/user/code")
    public Result<Void> code(@RequestParam String phone) {
        return userService.sendCode(phone);
    }

    /* 3. 手机号 + 验证码登录，成功返回 token */
    @PostMapping("/user/login")
    public Result<String> login(@Valid @RequestBody LoginFormDTO loginForm) {
        return userService.login(loginForm);
    }

    /* 4. 获取当前登录用户信息 */
    @GetMapping("/user/me")
    public Result<UserDTO> me() {
        return userService.me();
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
