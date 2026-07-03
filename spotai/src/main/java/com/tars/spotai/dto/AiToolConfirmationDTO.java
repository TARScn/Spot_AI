package com.tars.spotai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiToolConfirmationDTO {
    private String toolName;
    private String confirmToken;
    private String title;
    private String description;
}
