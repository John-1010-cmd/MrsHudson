package com.mrshudson.util;

import com.alibaba.fastjson2.JSON;
import lombok.Builder;
import lombok.Data;

/**
 * SSE 事件统一数据结构
 *
 * 所有流式响应事件应使用此类构建，确保格式统一
 */
@Data
@Builder
public class SseEvent {

    /**
     * 事件类型
     */
    private String type;

    /**
     * 事件数据 - 内容文本
     */
    private String text;

    /**
     * 事件数据 - 内容（与 text 等效，兼容多种场景）
     */
    private String content;

    /**
     * 事件数据 - URL
     */
    private String url;

    /**
     * 事件数据 - 错误信息
     */
    private String message;

    /**
     * 事件数据 - 工具调用信息
     */
    private ToolCallInfo toolCall;

    /**
     * 事件数据 - 工具执行结果
     */
    private ToolResultInfo toolResult;

    /**
     * 事件数据 - Token 使用统计
     */
    private TokenUsageInfo tokenUsage;

    /**
     * 工具调用信息
     */
    @Data
    @Builder
    public static class ToolCallInfo {
        private String name;
        private String arguments;
    }

    /**
     * 工具执行结果信息
     */
    @Data
    @Builder
    public static class ToolResultInfo {
        private String name;
        private String result;
    }

    /**
     * Token 使用统计信息
     */
    @Data
    @Builder
    public static class TokenUsageInfo {
        private Integer inputTokens;
        private Integer outputTokens;
        private Long duration;
        private String model;
    }

    /**
     * 转换为 JSON 字符串
     */
    public String toJson() {
        return JSON.toJSONString(this);
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建内容事件
     */
    public static SseEvent content(String text) {
        return SseEvent.builder().type("content").text(text).build();
    }

    /**
     * 创建音频 URL 事件
     */
    public static SseEvent audioUrl(String url) {
        return SseEvent.builder().type("audio_url").url(url).build();
    }

    /**
     * 创建 Token 使用统计事件
     */
    public static SseEvent tokenUsage(int inputTokens, int outputTokens, long duration, String model) {
        return SseEvent.builder()
                .type("token_usage")
                .tokenUsage(TokenUsageInfo.builder()
                        .inputTokens(inputTokens)
                        .outputTokens(outputTokens)
                        .duration(duration)
                        .model(model)
                        .build())
                .build();
    }

    /**
     * 创建工具调用事件
     */
    public static SseEvent toolCall(String name, String arguments) {
        return SseEvent.builder()
                .type("tool_call")
                .toolCall(ToolCallInfo.builder()
                        .name(name)
                        .arguments(arguments)
                        .build())
                .build();
    }

    /**
     * 创建工具结果事件
     */
    public static SseEvent toolResult(String name, String result) {
        return SseEvent.builder()
                .type("tool_result")
                .toolResult(ToolResultInfo.builder()
                        .name(name)
                        .result(result)
                        .build())
                .build();
    }

    /**
     * 创建缓存命中事件
     */
    public static SseEvent cacheHit(String content) {
        return SseEvent.builder().type("cache_hit").content(content).build();
    }

    /**
     * 创建澄清提示事件
     */
    public static SseEvent clarification(String content) {
        return SseEvent.builder().type("clarification").content(content).build();
    }

    /**
     * 创建错误事件
     */
    public static SseEvent error(String message) {
        return SseEvent.builder().type("error").message(message).build();
    }

    /**
     * 创建完成事件
     */
    public static SseEvent done() {
        return SseEvent.builder().type("done").build();
    }
}
