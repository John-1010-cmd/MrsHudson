package com.mrshudson.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话会话实体
 */
@Data
@TableName("conversation")
public class Conversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 会话标题
     */
    @TableField("title")
    private String title;

    /**
     * AI模型提供者
     */
    @TableField("provider")
    private String provider;

    /**
     * 最后消息时间
     */
    @TableField(value = "last_message_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime lastMessageAt;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除
     */
    @TableField("deleted")
    @TableLogic
    private Integer deleted;
}
