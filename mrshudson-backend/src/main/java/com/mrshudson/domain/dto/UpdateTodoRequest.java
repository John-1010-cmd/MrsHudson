package com.mrshudson.domain.dto;


import lombok.Data;

import java.time.LocalDateTime;

/**
 * 更新待办事项请求
 */
@Data
public class UpdateTodoRequest {

    private String title;

    private String description;

    private String priority;

    private String status;

    private LocalDateTime dueDate;
}
