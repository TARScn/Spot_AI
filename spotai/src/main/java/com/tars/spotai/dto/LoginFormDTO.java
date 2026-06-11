package com.tars.spotai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Data transfer object for the login request — encapsulates phone number and verification code.
 */
@Data
public class LoginFormDTO {
    /* 1. 登录凭证 */
    @NotBlank(message = "手机号不能为空")
    private String phone;

    @NotBlank(message = "验证码不能为空")
    private String code;
}
