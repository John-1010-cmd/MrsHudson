package com.mrshudson.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发送消息响应DTO
 */
@Data
@Builder
public class SendMessageResponse {

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * AI回复内容
     */
    private String content;

    /**
     * 调用的工具列表
     */
    private List<ToolCallInfo> functionCalls;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 语音URL（TTS生成的音频文件地址）
     */
    private String audioUrl;

    /**
     * 工具调用信息
     */
    @Data
    @Builder
    public static class ToolCallInfo {
        /**
         * 工具名称
         */
        private String name;

        /**
         * 参数
         */
        private String arguments;

        /**
         * 执行结果
         */
        private String result;
    }
}
