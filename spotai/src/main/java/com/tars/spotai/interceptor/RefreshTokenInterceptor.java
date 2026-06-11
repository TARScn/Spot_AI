package com.tars.spotai.interceptor;

import com.tars.spotai.config.AuthProperties;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.utils.RedisConstants;
import com.tars.spotai.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Interceptor that resolves the authentication token from the request header,
 * loads the corresponding user from Redis, and populates {@link UserHolder}.
 * Also refreshes the token TTL on each request. Executes at order 0 so it runs
 * before {@link LoginInterceptor}.
 */
@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {
    /* 1. 依赖注入 */
    private final StringRedisTemplate stringRedisTemplate;
    private final AuthProperties authProperties;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate, AuthProperties authProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.authProperties = authProperties;
    }

    /* 2. 请求前置处理：解析 Token → 加载用户 → 刷新 TTL */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        /* 2.1 从请求头中提取 token */
        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            return true; // 无 token 仍放行，后续 LoginInterceptor 拦截
        }

        /* 2.2 从 Redis Hash 中加载用户信息 */
        String key = RedisConstants.LOGIN_TOKEN_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        if (userMap == null || userMap.isEmpty()) {
            return true; // token 过期或无效，放行由 LoginInterceptor 拒绝
        }

        /* 2.3 写入 UserHolder 并刷新 token 有效期 */
        UserDTO user = new UserDTO(
                Long.valueOf(String.valueOf(userMap.get("id"))),
                String.valueOf(userMap.get("nickName")),
                String.valueOf(userMap.get("icon"))
        );
        UserHolder.saveUser(user);
        stringRedisTemplate.expire(key, authProperties.getTokenTtlMinutes(), TimeUnit.MINUTES);
        return true;
    }

    /* 3. 请求完成后清理 ThreadLocal */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserHolder.removeUser();
    }

    /* 4. 从 Authorization 头中提取 Bearer token */
    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization)) {
            authorization = request.getHeader("authorization");
        }
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }
}
