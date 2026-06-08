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
@EnableConfigurationProperties(AuthProperties.class)
public class WebConfig implements WebMvcConfigurer {
    private final RefreshTokenInterceptor refreshTokenInterceptor;
    private final LoginInterceptor loginInterceptor;

    public WebConfig(RefreshTokenInterceptor refreshTokenInterceptor, LoginInterceptor loginInterceptor) {
        this.refreshTokenInterceptor = refreshTokenInterceptor;
        this.loginInterceptor = loginInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(refreshTokenInterceptor).addPathPatterns("/**").order(0);
        registry.addInterceptor(loginInterceptor)
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
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
