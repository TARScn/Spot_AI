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
    private Long id;
    private String phone;
    private String password;
    private String nickName;
    private String icon;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
