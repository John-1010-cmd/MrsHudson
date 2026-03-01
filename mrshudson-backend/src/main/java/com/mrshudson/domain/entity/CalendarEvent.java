package com.mrshudson.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 日历事件实体
 */
@Data
@TableName("calendar_event")
public class CalendarEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("title")
    private String title;

    @TableField("description")
    private String description;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField("location")
    private String location;

    @TableField("category")
    private String category = Category.PERSONAL.name();

    @TableField("reminder_minutes")
    private Integer reminderMinutes = 15;

    @TableField("is_recurring")
    private Boolean isRecurring = false;

    @TableField("recurrence_rule")
    private String recurrenceRule;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 事件分类枚举
     */
    public enum Category {
        WORK,
        PERSONAL,
        FAMILY
    }
}
