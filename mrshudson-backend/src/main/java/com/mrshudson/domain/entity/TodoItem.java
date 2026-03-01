package com.mrshudson.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待办事项实体
 */
@Data
@TableName("todo_item")
public class TodoItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("title")
    private String title;

    @TableField("description")
    private String description;

    @TableField("priority")
    private String priority = Priority.MEDIUM.name();

    @TableField("status")
    private String status = Status.PENDING.name();

    @TableField("due_date")
    private LocalDateTime dueDate;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 优先级枚举
     */
    public enum Priority {
        LOW,
        MEDIUM,
        HIGH
    }

    /**
     * 状态枚举
     */
    public enum Status {
        PENDING,
        IN_PROGRESS,
        COMPLETED
    }
}
