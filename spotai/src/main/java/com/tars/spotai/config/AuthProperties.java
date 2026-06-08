package com.tars.spotai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for authentication settings.
 * Reads values prefixed with {@code spotai.auth} from application configuration.
 */
@ConfigurationProperties(prefix = "spotai.auth")
public class AuthProperties {
    /** Verification code TTL in minutes (default 5). */
    private long codeTtlMinutes = 5;

    /** Login token TTL in minutes (default 30). */
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
