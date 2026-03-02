package com.mrshudson.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话会话DTO
 */
@Data
public class ConversationDTO {

    /**
     * 会话ID
     */
    private String id;

    /**
     * 会话标题
     */
    private String title;

    /**
     * AI模型提供者
     */
    private String provider;

    /**
     * 最后消息时间
     */
    private LocalDateTime lastMessageAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
