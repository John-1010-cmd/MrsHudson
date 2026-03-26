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
   * 事件数据 - URL（audio_done 成功时的音频地址）
   */
  private String url;

  /**
   * 事件数据 - 错误信息
   */
  private String message;

  /**
   * 会话 ID（content / content_done / audio_done 必填）
   */
  private Long conversationId;

  /**
   * 消息 ID（用于将 SSE 事件精确关联到对应消息气泡）
   */
  private Long messageId;

  /**
   * TTS 是否超时（audio_done 专用）
   */
  private Boolean timeout;

  /**
   * TTS 提供商返回空（audio_done 专用）
   */
  private Boolean noaudio;

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
   * 创建内容事件（带 conversationId / messageId）
   */
  public static SseEvent content(String text, Long conversationId, Long messageId) {
    return SseEvent.builder().type("content").text(text)
        .conversationId(conversationId).messageId(messageId).build();
  }

  /**
   * 创建内容事件（不带 conversationId，向后兼容）
   */
  public static SseEvent content(String text) {
    return SseEvent.builder().type("content").text(text).build();
  }

  /**
   * 创建 AI 内容结束事件
   */
  public static SseEvent contentDone(Long conversationId, Long messageId) {
    return SseEvent.builder().type("content_done")
        .conversationId(conversationId).messageId(messageId).build();
  }

  /**
   * 创建 TTS 语音合成结束事件
   *
   * @param url            音频 URL；超时/失败/noaudio 时传 null
   * @param timeout        是否超时（超时后后台继续合成，历史消息可能有 URL）
   * @param error          异常信息；超时时传 null
   * @param conversationId 会话 ID
   * @param messageId      消息 ID
   */
  public static SseEvent audioDone(String url, boolean timeout, String error,
      Long conversationId, Long messageId) {
    SseEvent.SseEventBuilder builder = SseEvent.builder()
        .type("audio_done")
        .conversationId(conversationId)
        .messageId(messageId);
    if (timeout) {
      builder.timeout(true);
    } else if (error != null && !error.isEmpty()) {
      builder.message(error);
    } else if (url == null || url.isEmpty()) {
      builder.noaudio(true);
    } else {
      builder.url(url);
    }
    return builder.build();
  }

  /**
   * 创建音频 URL 事件（旧格式，保留向后兼容）
   * 
   * @deprecated 请使用 audioDone()
   */
  @Deprecated
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
