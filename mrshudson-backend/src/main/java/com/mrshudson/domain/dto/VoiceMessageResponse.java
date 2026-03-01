package com.mrshudson.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * 语音消息响应DTO
 */
@Data
public class VoiceMessageResponse {

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 语音识别出的文本
     */
    private String recognizedText;

    /**
     * AI回复内容
     */
    private String content;

    /**
     * 函数调用信息
     */
    private List<ToolCallInfo> functionCalls;

    /**
     * 创建时间
     */
    private String createdAt;

    /**
     * 工具调用信息
     */
    @Data
    public static class ToolCallInfo {
        private String name;
        private String arguments;
        private String result;
    }
}
