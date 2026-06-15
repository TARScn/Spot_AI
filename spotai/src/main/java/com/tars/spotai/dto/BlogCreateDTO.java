package com.tars.spotai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BlogCreateDTO {
    @NotNull(message = "商户ID不能为空")
    private Long shopId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 255, message = "标题不能超过255个字符")
    private String title;

    @Size(max = 2048, message = "图片地址不能超过2048个字符")
    private String images;

    @NotBlank(message = "正文不能为空")
    @Size(max = 2048, message = "正文不能超过2048个字符")
    private String content;
}
