package com.tars.spotai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 探店笔记发布事件，用于异步投递关注流。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlogPublishedMessage {
    private Long authorId;
    private Long blogId;
    private LocalDateTime eventTime;
}
