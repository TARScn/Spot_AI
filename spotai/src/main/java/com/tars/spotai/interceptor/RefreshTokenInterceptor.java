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
    private final StringRedisTemplate stringRedisTemplate;
    private final AuthProperties authProperties;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate, AuthProperties authProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.authProperties = authProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            return true;
        }

        String key = RedisConstants.LOGIN_TOKEN_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        if (userMap == null || userMap.isEmpty()) {
            return true;
        }

        UserDTO user = new UserDTO(
                Long.valueOf(String.valueOf(userMap.get("id"))),
                String.valueOf(userMap.get("nickName")),
                String.valueOf(userMap.get("icon"))
        );
        UserHolder.saveUser(user);
        stringRedisTemplate.expire(key, authProperties.getTokenTtlMinutes(), TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserHolder.removeUser();
    }

    /**
     * Extracts the Bearer token from the Authorization header.
     * Checks both "Authorization" and "authorization" header names.
     */
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
