package com.mrshudson.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提醒记录实体
 */
@Data
@TableName("reminder")
public class Reminder {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("type")
    private String type;

    @TableField("ref_id")
    private Long refId;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    @TableField("remind_at")
    private LocalDateTime remindAt;

    @TableField("is_read")
    private Boolean isRead = false;

    @TableField("channel")
    private String channel = Channel.IN_APP.name();

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 提醒类型枚举
     */
    public enum Type {
        EVENT,
        TODO,
        WEATHER,
        SYSTEM
    }

    /**
     * 提醒渠道枚举
     */
    public enum Channel {
        IN_APP,
        EMAIL,
        PUSH
    }
}
