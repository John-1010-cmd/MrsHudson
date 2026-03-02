package com.mrshudson.domain.dto;

import lombok.Data;

/**
 * 创建会话请求
 */
@Data
public class CreateConversationRequest {

    /**
     * 会话标题（可选，默认为新对话）
     */
    private String title;
}
