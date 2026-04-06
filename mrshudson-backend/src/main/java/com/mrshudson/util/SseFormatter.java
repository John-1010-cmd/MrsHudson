package com.mrshudson.util;

import com.alibaba.fastjson2.JSON;

import reactor.core.publisher.Flux;

/**
 * SSE 格式化工具类
 *
 * 架构分层（遵循规范 5.1 节）： Service 层：调用本类便捷方法，返回纯 JSON 字符串，不含 data: 前缀 Controller 层：调用 addSsePrefix() 统一添加
 * data: 前缀和 \n\n 后缀
 */
public final class SseFormatter {

  private static final String SSE_DATA_PREFIX = "data: ";
  private static final String SSE_SUFFIX = "\n\n";

  private SseFormatter() {}

  // ==================== Controller 层专用：格式化为 SSE 字符串 ====================

  /**
   * 将 SseEvent 对象格式化为 SSE 字符串（Controller 层使用） 输出: data: {"type":"content","text":"Hello"}\n\n
   */
  public static String format(SseEvent event) {
    return SSE_DATA_PREFIX + JSON.toJSONString(event) + SSE_SUFFIX;
  }

  /**
   * 将 JSON 字符串格式化为 SSE 字符串（Controller 层使用）
   */
  public static String format(String jsonStr) {
    if (jsonStr == null)
      return "";
    return SSE_DATA_PREFIX + jsonStr + SSE_SUFFIX;
  }

  /**
   * Flux<String 纯JSON> → Flux<String SSE格式>（Controller 层统一调用） 示例:
   * flux.transform(SseFormatter::addSsePrefix)
   */
  public static Flux<String> addSsePrefix(Flux<String> flux) {
    return flux.map(jsonStr -> {
      if (jsonStr == null)
        return "";
      if (jsonStr.startsWith("data:")) {
        // 已有前缀，只添加后缀（防御性处理，正常情况不应出现）
        return jsonStr + SSE_SUFFIX;
      }
      return SSE_DATA_PREFIX + jsonStr + SSE_SUFFIX;
    });
  }

  // ==================== Service 层：返回纯 JSON（不含 data: 前缀）====================

  /**
   * JSON 字符串转义（由 SseFormatter 内部处理，遵循规范 5.5 节职责约定）
   * 将双引号、反斜杠、换行 Tab 等 JSON 特殊字符进行转义
   */
  private static String escapeJson(String text) {
    if (text == null) return "";
    return text
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  /**
   * AI 思考推理过程（增量）
   * 仅当模型返回 reasoning_content 时调用
   * text 为原始字符串，由 JSON.toJSONString() 自动完成转义，无需手动转义
   * （遵循规范 §5.5：SseFormatter 负责 JSON 结构拼接，JSON 序列化时自动转义）
   */
  public static String thinking(String text, Long conversationId, Long messageId) {
    return toJson(SseEvent.thinking(text, conversationId, messageId));
  }

  /** AI 增量内容（带 conversationId / messageId），由 JSON.toJSONString() 自动完成转义 */
  public static String content(String text, Long conversationId, Long messageId) {
    return toJson(SseEvent.content(text, conversationId, messageId));
  }

  /** AI 增量内容（不带 ID，向后兼容），由 JSON.toJSONString() 自动完成转义 */
  public static String content(String text) {
    return toJson(SseEvent.content(text));
  }

  /** AI 内容结束 */
  public static String contentDone(Long conversationId, Long messageId) {
    return toJson(SseEvent.contentDone(conversationId, messageId));
  }

  /** TTS 语音合成结束 */
  public static String audioDone(String url, boolean timeout, String error, Long conversationId,
      Long messageId) {
    return toJson(SseEvent.audioDone(url, timeout, error, conversationId, messageId));
  }

  /** Token 使用统计 */
  public static String tokenUsage(int inputTokens, int outputTokens, long duration, String model) {
    return toJson(SseEvent.tokenUsage(inputTokens, outputTokens, duration, model));
  }

  /** 工具调用（带 conversationId） */
  public static String toolCall(String name, String arguments, Long conversationId) {
    return toJson(SseEvent.toolCall(name, arguments, conversationId));
  }

  /** 工具调用（向后兼容） */
  public static String toolCall(String name, String arguments) {
    return toJson(SseEvent.toolCall(name, arguments, null));
  }

  /** 工具执行结果（带 conversationId） */
  public static String toolResult(String name, String result, Long conversationId) {
    return toJson(SseEvent.toolResult(name, result, conversationId));
  }

  /** 工具执行结果（向后兼容） */
  public static String toolResult(String name, String result) {
    return toJson(SseEvent.toolResult(name, result, null));
  }

  /** 缓存命中 */
  public static String cacheHit(String content) {
    return toJson(SseEvent.cacheHit(content));
  }

  /** 澄清提示 */
  public static String clarification(String content) {
    return toJson(SseEvent.clarification(content));
  }

  /** 错误信息 */
  public static String error(String message) {
    return toJson(SseEvent.error(message));
  }

  /** 错误信息（带可选 conversationId） */
  public static String error(String message, Long conversationId) {
    return toJson(SseEvent.error(message, conversationId));
  }

  /** 流式完成 */
  public static String done() {
    return toJson(SseEvent.done());
  }

  /** 流式完成（带可选 conversationId） */
  public static String done(Long conversationId) {
    return toJson(SseEvent.done(conversationId));
  }

  /** @deprecated 请使用 audioDone() */
  @Deprecated
  public static String audioUrl(String url) {
    return toJson(SseEvent.audioUrl(url));
  }

  // ==================== 内部工具 ====================

  public static String toJson(SseEvent event) {
    return JSON.toJSONString(event);
  }

  public static Flux<String> formatFlux(Flux<SseEvent> eventFlux) {
    return eventFlux.map(event -> format(event));
  }

  public static Flux<SseEvent> fluxOf(SseEvent... events) {
    return Flux.fromArray(events);
  }
}
