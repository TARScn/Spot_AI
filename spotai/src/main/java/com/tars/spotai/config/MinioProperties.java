package com.tars.spotai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for MinIO object storage.
 */
@ConfigurationProperties(prefix = "spotai.minio")
public class MinioProperties {
    private String endpoint = "http://localhost:9000";
    private String externalEndpoint = "http://localhost:9000";
    private String accessKey = "admin";
    private String secretKey = "";
    private String bucket = "spotai";
    private String blogPrefix = "blog";
    private boolean publicRead = true;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getExternalEndpoint() {
        return externalEndpoint;
    }

    public void setExternalEndpoint(String externalEndpoint) {
        this.externalEndpoint = externalEndpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getBlogPrefix() {
        return blogPrefix;
    }

    public void setBlogPrefix(String blogPrefix) {
        this.blogPrefix = blogPrefix;
    }

    public boolean isPublicRead() {
        return publicRead;
    }

    public void setPublicRead(boolean publicRead) {
        this.publicRead = publicRead;
    }
}
