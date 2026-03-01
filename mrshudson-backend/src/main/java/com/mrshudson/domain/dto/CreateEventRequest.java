package com.mrshudson.domain.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建事件请求
 */
@Data
public class CreateEventRequest {

    @NotBlank(message = "事件标题不能为空")
    private String title;

    private String description;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    private String location;

    private String category = "PERSONAL";

    private Integer reminderMinutes = 15;
}
