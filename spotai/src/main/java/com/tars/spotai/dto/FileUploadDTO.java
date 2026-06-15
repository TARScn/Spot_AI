package com.tars.spotai.dto;

/**
 * Uploaded file metadata returned to clients.
 */
public class FileUploadDTO {
    private String name;
    private String url;
    private String contentType;
    private long size;

    public FileUploadDTO() {
    }

    public FileUploadDTO(String name, String url, String contentType, long size) {
        this.name = name;
        this.url = url;
        this.contentType = contentType;
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}
