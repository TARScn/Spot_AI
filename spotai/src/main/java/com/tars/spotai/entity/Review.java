package com.tars.spotai.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Review {
    private Long id;
    private Long shopId;
    private Long userId;
    private Long orderId;
    private Integer score;
    private String content;
    private Integer status;
    private Integer liked;
    private Integer imagesCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
