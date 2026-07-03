package com.tars.spotai.service;

import org.springframework.util.StringUtils;

public record UserMemoryKey(String namespace, String key) {
    public static final String DEFAULT_NAMESPACE = "preference";

    public UserMemoryKey {
        namespace = normalize(namespace, DEFAULT_NAMESPACE);
        key = normalize(key, "");
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("memory key cannot be blank");
        }
    }

    public static UserMemoryKey of(String namespace, String key) {
        return new UserMemoryKey(namespace, key);
    }

    public static UserMemoryKey fromLegacy(String memoryKey, String memoryType) {
        String normalizedKey = normalize(memoryKey, "");
        String normalizedType = normalize(memoryType, DEFAULT_NAMESPACE);
        if (!StringUtils.hasText(normalizedKey)) {
            throw new IllegalArgumentException("memory key cannot be blank");
        }
        int lastDot = normalizedKey.lastIndexOf('.');
        if (lastDot > 0 && lastDot < normalizedKey.length() - 1) {
            return new UserMemoryKey(normalizedKey.substring(0, lastDot), normalizedKey.substring(lastDot + 1));
        }
        return new UserMemoryKey(normalizedType, normalizedKey);
    }

    public String physicalKey() {
        return namespace + "." + key;
    }

    private static String normalize(String value, String fallback) {
        return StringUtils.hasText(value) ? value.strip() : fallback;
    }
}
