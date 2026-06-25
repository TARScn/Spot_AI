package com.tars.spotai.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AiChatMessageDTO {
    private String role;
    private String content;
}
