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

    /* 请求前置处理：校验登录态，未登录返回 401 */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        /* 1. 预检请求直接放行 */
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        if (isPublicBlogRequest(request)) {
            return true;
        }
        /* 2. UserHolder 中有用户 → 已登录，放行 */
        if (UserHolder.getUser() != null) {
            return true;
        }
        /* 3. 未登录 → 返回 401 + 错误信息 */
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail("请先登录")));
        return false;
    }

    private boolean isPublicBlogRequest(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return path.matches("^/blog/\\d+$")
                || "/blog/hot".equals(path)
                || "/blog/recent".equals(path)
                || "/blog/of/user".equals(path)
                || path.matches("^/blog/likes/\\d+$");
    }
}
