package com.tars.spotai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UV 统计上报事件。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UvRecordMessage {
    private String targetType;
    private Long targetId;
    private String pageCode;
    private String visitor;
    private String day;
}
