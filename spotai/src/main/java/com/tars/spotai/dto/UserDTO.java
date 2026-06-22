package com.tars.spotai.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data transfer object for user profile information exposed via API responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;       // 用户 ID
    private String nickName; // 昵称
    private String icon;   // 头像 URL
}
