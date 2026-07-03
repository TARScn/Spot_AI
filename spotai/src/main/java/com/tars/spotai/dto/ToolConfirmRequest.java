package com.tars.spotai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ToolConfirmRequest {
    @NotBlank
    private String confirmToken;
    private boolean confirmed;
}
