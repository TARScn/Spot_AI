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
    /* 1. 依赖注入 */
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

    /* ========== 业务方法 ========== */

    /* 2. 发送验证码 */
    public Result<Void> sendCode(String phone) {
        /* 2.1 校验手机号格式 */
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        /* 2.2 生成 6 位随机码并缓存到 Redis */
        String code = String.format("%06d", random.nextInt(1_000_000));
        stringRedisTemplate.opsForValue().set(
                RedisConstants.LOGIN_CODE_KEY + phone,
                code,
                authProperties.getCodeTtlMinutes(),
                TimeUnit.MINUTES
        );
        /* 2.3 发送验证码（开发环境为日志输出） */
        smsService.sendCode(phone, code);
        return Result.ok(null);
    }

    /* 3. 登录（手机号 + 验证码） */
    public Result<String> login(LoginFormDTO loginForm) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();

        /* 3.1 参数校验 */
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        if (RegexUtils.isCodeInvalid(code)) {
            return Result.fail("验证码格式错误");
        }

        /* 3.2 校验验证码 */
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if (!StringUtils.hasText(cacheCode) || !cacheCode.equals(code)) {
            return Result.fail("验证码错误或已过期");
        }

        /* 3.3 查询或创建用户 */
        User user = userRepository.findByPhone(phone);
        if (user == null) {
            user = createUser(phone);
            userRepository.saveUserWithPhone(user, IdWorker.getId());
        }

        /* 3.4 生成 Token，存入 Redis Hash */
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

    /* 4. 获取当前登录用户信息 */
    public Result<UserDTO> me() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        return Result.ok(user);
    }

    /* ========== 私有辅助方法 ========== */

    /* 5. 创建新用户 */
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

    /* 6. User -> UserDTO */
    private UserDTO toUserDTO(User user) {
        return new UserDTO(user.getId(), user.getNickName(), user.getIcon());
    }

    /* 7. 生成无横线的 UUID */
    private String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
