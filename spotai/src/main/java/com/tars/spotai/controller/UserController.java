package com.tars.spotai.controller;

import com.tars.spotai.dto.LoginFormDTO;
import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.UserDTO;
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
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Sends a login verification code to the given phone number.
     */
    @PostMapping("/user/code")
    public Result<Void> code(@RequestParam String phone) {
        return userService.sendCode(phone);
    }

    /**
     * Logs in with phone number and verification code.
     * Returns a token on success.
     */
    @PostMapping("/user/login")
    public Result<String> login(@Valid @RequestBody LoginFormDTO loginForm) {
        return userService.login(loginForm);
    }

    /**
     * Returns the currently authenticated user's profile.
     */
    @GetMapping("/user/me")
    public Result<UserDTO> me() {
        return userService.me();
    }
}
