package com.tars.spotai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class AiChatRequestDTO {
    @NotBlank(message = "请输入要咨询的问题")
    @Size(max = 1000, message = "单次问题不能超过 1000 字")
    private String message;

    @Size(max = 12, message = "上下文消息不能超过 12 条")
    private List<AiChatMessageDTO> history = List.of();

    private Long shopId;
    private String route = "CHAT";
}
