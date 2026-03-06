package com.mrshudson.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备Token实体
 */
@Data
@TableName("device_token")
public class DeviceToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("device_token")
    private String deviceToken;

    @TableField("platform")
    private String platform;

    @TableField("is_active")
    private Boolean isActive = true;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 平台类型枚举
     */
    public enum Platform {
        ANDROID,
        IOS
    }
}
