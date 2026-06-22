package com.tars.spotai.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReviewViewDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long shopId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String userName;
    private String userIcon;
    private Integer score;
    private String content;
    private Integer liked;
    private List<String> images;
    private LocalDateTime createTime;
}
