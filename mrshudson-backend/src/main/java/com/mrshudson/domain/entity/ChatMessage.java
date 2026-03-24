package com.mrshudson.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话消息实体
 */
@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("role")
    private String role;

    @TableField("content")
    private String content;

    @TableField("function_call")
    private String functionCall;

    /**
     * 音频URL（语音合成结果）
     */
    @TableField("audio_url")
    private String audioUrl;

    /**
     * 会话ID
     */
    @TableField("conversation_id")
    private Long conversationId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 消息角色枚举
     */
    public enum Role {
        USER,
        ASSISTANT,
        SYSTEM
    }
}
