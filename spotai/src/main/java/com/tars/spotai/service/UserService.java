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
 * Service for user authentication, registration, and profile retrieval.
 * Handles SMS code sending, phone-code login, and auto-registration of new users.
 */
@Service
public class UserService {
    private final StringRedisTemplate stringRedisTemplate;
    private final UserRepository userRepository;
    private final SmsService smsService;
    private final AuthProperties authProperties;
    private final Random random = new Random();

    public UserService(StringRedisTemplate stringRedisTemplate,
                       UserRepository userRepository,
                       SmsService smsService,
                       AuthProperties authProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.userRepository = userRepository;
        this.smsService = smsService;
        this.authProperties = authProperties;
    }

    /**
     * Sends a 6-digit verification code to the given phone number.
     * Validates the phone format first and caches the code in Redis.
     */
    public Result<Void> sendCode(String phone) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        stringRedisTemplate.opsForValue().set(
                RedisConstants.LOGIN_CODE_KEY + phone,
                code,
                authProperties.getCodeTtlMinutes(),
                TimeUnit.MINUTES
        );
        smsService.sendCode(phone, code);
        return Result.ok(null);
    }

    /**
     * Authenticates the user with phone and verification code.
     * On success, creates a token, caches user info in Redis, and returns the token.
     * Auto-registers the user if the phone number is new.
     */
    public Result<String> login(LoginFormDTO loginForm) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        if (RegexUtils.isCodeInvalid(code)) {
            return Result.fail("验证码格式错误");
        }

        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if (!StringUtils.hasText(cacheCode) || !cacheCode.equals(code)) {
            return Result.fail("验证码错误或已过期");
        }

        User user = userRepository.findByPhone(phone);
        if (user == null) {
            user = createUser(phone);
            userRepository.saveUserWithPhone(user, IdWorker.getId());
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
        stringRedisTemplate.delete(RedisConstants.LOGIN_CODE_KEY + phone);
        return Result.ok(token);
    }

    /**
     * Returns the profile of the currently logged-in user from the request context.
     */
    public Result<UserDTO> me() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        return Result.ok(user);
    }

    private User createUser(String phone) {
        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setId(IdWorker.getId());
        user.setPhone(phone);
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

    private String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
