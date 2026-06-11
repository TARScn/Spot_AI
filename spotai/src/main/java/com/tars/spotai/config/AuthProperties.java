package com.tars.spotai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for authentication settings.
 * Reads values prefixed with {@code spotai.auth} from application configuration.
 */
@ConfigurationProperties(prefix = "spotai.auth")
public class AuthProperties {
    /* 1. 验证码有效期（分钟），默认 5 分钟 */
    private long codeTtlMinutes = 5;

    /* 2. Token 有效期（分钟），默认 30 分钟 */
    private long tokenTtlMinutes = 30;

    public long getCodeTtlMinutes() {
        return codeTtlMinutes;
    }

    public void setCodeTtlMinutes(long codeTtlMinutes) {
        this.codeTtlMinutes = codeTtlMinutes;
    }

    public long getTokenTtlMinutes() {
        return tokenTtlMinutes;
    }

    public void setTokenTtlMinutes(long tokenTtlMinutes) {
        this.tokenTtlMinutes = tokenTtlMinutes;
    }
}
