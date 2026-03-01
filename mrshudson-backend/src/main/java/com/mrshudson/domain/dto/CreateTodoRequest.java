package com.mrshudson.domain.dto;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建待办事项请求
 */
@Data
public class CreateTodoRequest {

    @NotBlank(message = "待办标题不能为空")
    private String title;

    private String description;

    private String priority = "MEDIUM";

    private LocalDateTime dueDate;
}
