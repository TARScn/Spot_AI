package com.tars.spotai.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Shop entity mapped from tb_shop.
 */
@Data
public class Shop {
    private Long id;
    private String name;
    private Long typeId;
    private String images;
    private String area;
    private String address;
    private Double x;
    private Double y;
    private Long avgPrice;
    private Integer sold;
    private Integer comments;
    private Integer score;
    private String openHours;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
