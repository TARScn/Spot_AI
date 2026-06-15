package com.tars.spotai.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BlogViewDTO {
    private Long id;
    private Long shopId;
    private Long userId;
    private String title;
    private String images;
    private String content;
    private Integer liked;
    private Integer comments;
    private Boolean isLike;
    private String name;
    private String icon;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
