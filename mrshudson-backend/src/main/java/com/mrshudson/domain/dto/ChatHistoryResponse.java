package com.mrshudson.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话历史响应DTO
 */
@Data
@Builder
public class ChatHistoryResponse {

    /**
     * 消息列表
     */
    private List<MessageInfo> messages;

    /**
     * 消息信息
     */
    @Data
    @Builder
    public static class MessageInfo {
        /**
         * 消息ID
         */
        private String id;

        /**
         * 角色：user, assistant, system
         */
        private String role;

        /**
         * 内容
         */
        private String content;

        /**
         * 创建时间
         */
        private LocalDateTime createdAt;

        /**
         * 工具调用信息（AI回复时可能有）
         */
        private String functionCall;

        /**
         * 音频URL（语音合成结果）
         */
        private String audioUrl;
    }
}
