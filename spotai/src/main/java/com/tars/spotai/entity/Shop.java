package com.tars.spotai.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Shop entity mapped from tb_shop.
 */
@Data
public class Shop {
    /* 1. 基础信息 */
    private Long id;
    private String name;
    private Long typeId;
    private String images;
    private String area;
    private String address;

    /* 2. 坐标 */
    private Double x;
    private Double y;

    /* 3. 经营数据 */
    private Long avgPrice;
    private Integer sold;
    private Integer comments;
    private Integer score;
    private String openHours;
    private Double distance;

    /* 4. 时间戳 */
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
