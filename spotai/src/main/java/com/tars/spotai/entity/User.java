package com.tars.spotai.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing a registered user stored in the sharded user table.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
    /* 1. 标识 */
    private Long id;
    private String email;
    private String password;
    private String nickName;
    private String icon;

    /* 2. 时间戳 */
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
