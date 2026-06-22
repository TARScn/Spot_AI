package com.tars.spotai.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tars.spotai.config.AuthProperties;
import com.tars.spotai.dto.LoginFormDTO;
import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.entity.User;
import com.tars.spotai.repository.UserRepository;
import com.tars.spotai.utils.RedisConstants;
import com.tars.spotai.utils.RegexUtils;
import com.tars.spotai.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Handles email-code login and first-login registration.
 */
@Service
public class UserService {
    private final StringRedisTemplate stringRedisTemplate;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AuthProperties authProperties;
    private final Random random = new Random();

    public UserService(StringRedisTemplate stringRedisTemplate,
                       UserRepository userRepository,
                       EmailService emailService,
                       AuthProperties authProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.authProperties = authProperties;
    }

    public Result<Void> sendCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (RegexUtils.isEmailInvalid(normalizedEmail)) {
            return Result.fail("邮箱格式错误");
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        stringRedisTemplate.opsForValue().set(
                RedisConstants.LOGIN_CODE_KEY + normalizedEmail,
                code,
                authProperties.getCodeTtlMinutes(),
                TimeUnit.MINUTES
        );
        emailService.sendCode(normalizedEmail, code);
        return Result.ok(null);
    }

    public Result<String> login(LoginFormDTO loginForm) {
        String email = normalizeEmail(loginForm.getEmail());
        String code = loginForm.getCode();

        if (RegexUtils.isEmailInvalid(email)) {
            return Result.fail("邮箱格式错误");
        }
        if (RegexUtils.isCodeInvalid(code)) {
            return Result.fail("验证码格式错误");
        }

        String codeKey = RedisConstants.LOGIN_CODE_KEY + email;
        String cacheCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (!StringUtils.hasText(cacheCode) || !cacheCode.equals(code)) {
            return Result.fail("验证码错误或已过期");
        }

        User user = userRepository.findByEmail(email);
        if (user == null) {
            user = createUser(email);
            userRepository.saveUserWithEmail(user, IdWorker.getId());
        }

        String token = compactUuid();
        UserDTO userDTO = toUserDTO(user);
        Map<String, String> userMap = new HashMap<>();
        userMap.put("id", String.valueOf(userDTO.getId()));
        userMap.put("nickName", userDTO.getNickName());
        userMap.put("icon", userDTO.getIcon() == null ? "" : userDTO.getIcon());

        String tokenKey = RedisConstants.LOGIN_TOKEN_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, authProperties.getTokenTtlMinutes(), TimeUnit.MINUTES);
        stringRedisTemplate.delete(codeKey);
        return Result.ok(token);
    }

    public Result<UserDTO> me() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        return Result.ok(user);
    }

    private User createUser(String email) {
        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setId(IdWorker.getId());
        user.setEmail(email);
        user.setPassword("");
        user.setNickName("user_" + compactUuid().substring(0, 8));
        user.setIcon("");
        user.setCreateTime(now);
        user.setUpdateTime(now);
        return user;
    }

    private UserDTO toUserDTO(User user) {
        return new UserDTO(user.getId(), user.getNickName(), user.getIcon());
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
