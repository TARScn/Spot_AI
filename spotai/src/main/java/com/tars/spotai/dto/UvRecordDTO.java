package com.tars.spotai.dto;

import lombok.Data;

@Data
public class UvRecordDTO {
    private String targetType;
    private Long targetId;
    private String pageCode;
    private String visitor;
}
