package com.tars.spotai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Login request with email and verification code.
 */
@Data
public class LoginFormDTO {
    @NotBlank(message = "邮箱不能为空")
    private String email;

    @NotBlank(message = "验证码不能为空")
    private String code;
}
