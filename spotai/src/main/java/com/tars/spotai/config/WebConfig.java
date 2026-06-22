package com.tars.spotai.config;

import com.tars.spotai.interceptor.LoginInterceptor;
import com.tars.spotai.interceptor.RefreshTokenInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration that registers interceptors and CORS settings.
 * The refresh-token interceptor runs first (order 0), followed by the login interceptor (order 1).
 */
@Configuration
@EnableConfigurationProperties({AuthProperties.class, VoucherProperties.class, MinioProperties.class})
public class WebConfig implements WebMvcConfigurer {
    /* 1. 拦截器注入 */
    private final RefreshTokenInterceptor refreshTokenInterceptor;
    private final LoginInterceptor loginInterceptor;

    public WebConfig(RefreshTokenInterceptor refreshTokenInterceptor, LoginInterceptor loginInterceptor) {
        this.refreshTokenInterceptor = refreshTokenInterceptor;
        this.loginInterceptor = loginInterceptor;
    }

    /* 2. 注册拦截器链 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        /* 2.1 第一道：Token 刷新拦截器（放行所有请求，仅刷新登录状态） */
        registry.addInterceptor(refreshTokenInterceptor).addPathPatterns("/**").order(0);
        /* 2.2 第二道：登录校验拦截器（排除公开接口后拦截其余请求） */
        registry.addInterceptor(loginInterceptor)
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/shop/**",
                        "/shop-type/**",
                        "/voucher/activities",
                        "/blog/hot",
                        "/blog/of/shop",
                        "/review/of/shop",
                        "/stats/uv/**",
                        "/error",
                        "/favicon.ico",
                        "/",
                        "/index.html",
                        "/assets/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**")
                .addPathPatterns("/**")
                .order(1);
    }

    /* 3. CORS 跨域配置 */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
