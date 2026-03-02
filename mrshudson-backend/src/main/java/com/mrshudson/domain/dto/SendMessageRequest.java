package com.mrshudson.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送消息请求DTO
 */
@Data
public class SendMessageRequest {

    /**
     * 用户消息内容
     */
    @NotBlank(message = "消息内容不能为空")
    private String message;

    /**
     * 会话ID（可选，用于多轮对话）
     */
    private String sessionId;

    /**
     * 对话会话ID（可选，用于关联会话）
     */
    private Long conversationId;
}
