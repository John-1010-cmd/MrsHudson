package com.mrshudson.domain.dto;


import lombok.Data;

import java.time.LocalDateTime;

/**
 * 更新事件请求
 */
@Data
public class UpdateEventRequest {

    private String title;

    private String description;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String location;

    private String category;

    private Integer reminderMinutes;
}
