package com.tars.spotai.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.dto.Result;
import com.tars.spotai.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * Interceptor that enforces authentication for protected endpoints.
 * If no authenticated user is found in {@link UserHolder}, responds with 401 Unauthorized.
 * Pre-flight OPTIONS requests are always allowed through.
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {
    private final ObjectMapper objectMapper;

    public LoginInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        if (UserHolder.getUser() != null) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail("请先登录")));
        return false;
    }
}
