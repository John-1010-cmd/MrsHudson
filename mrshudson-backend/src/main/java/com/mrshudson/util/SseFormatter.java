package com.mrshudson.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;

import reactor.core.publisher.Flux;

/**
 * SSE 格式化工具类
 *
 * 提供统一的 SSE 事件格式化和 Flux 流式处理方法
 */
public final class SseFormatter {

  private static final Logger log = LoggerFactory.getLogger(SseFormatter.class);

  private static final String SSE_DATA_PREFIX = "data: ";
  private static final String SSE_SUFFIX = "\n\n";

  private SseFormatter() {
    // 工具类不允许实例化
  }

  // ==================== 格式化方法 ====================

  /**
   * 将 SseEvent 对象格式化为 SSE 字符串
   *
   * @param event SSE 事件对象
   * @return SSE 格式字符串，如: data: {"type":"content","text":"Hello"}\n\n
   */
  public static String format(SseEvent event) {
    return SSE_DATA_PREFIX + JSON.toJSONString(event, JSONWriter.Feature.WriteNulls) + SSE_SUFFIX;
  }

  /**
   * 将 JSON 字符串格式化为 SSE 字符串
   *
   * @param jsonStr JSON 字符串（如: {"type":"content","text":"Hello"}）
   * @return SSE 格式字符串
   */
  public static String format(String jsonStr) {
    if (jsonStr == null) {
      return "";
    }
    if (jsonStr.startsWith(SSE_DATA_PREFIX)) {
      // 已经是 SSE 格式，直接返回
      return jsonStr;
    }
    return SSE_DATA_PREFIX + jsonStr + SSE_SUFFIX;
  }

  /**
   * 将 SseEvent 对象序列化为 JSON 字符串（不添加 SSE 前缀）
   *
   * @param event SSE 事件对象
   * @return JSON 字符串
   */
  public static String toJson(SseEvent event) {
    return JSON.toJSONString(event, JSONWriter.Feature.WriteNulls);
  }

  // ==================== Flux 转换方法 ====================

  /**
   * 将 Flux<SseEvent> 转换为标准 SSE 格式的 Flux<String>
   *
   * @param eventFlux SseEvent 对象的 Flux 流
   * @return SSE 格式字符串的 Flux 流
   */
  public static Flux<String> formatFlux(Flux<SseEvent> eventFlux) {
    return eventFlux.map(SseFormatter::format);
  }

  /**
   * 将 Flux<JSON字符串> 转换为标准 SSE 格式的 Flux<String>
   *
   * @param jsonFlux JSON 字符串的 Flux 流
   * @return SSE 格式字符串的 Flux 流
   */
  public static Flux<String> formatFluxFromJson(Flux<String> jsonFlux) {
    return jsonFlux.map(SseFormatter::format);
  }

  /**
   * 为 Flux<String> 添加统一的 SSE 格式化处理
   *
   * 如果字符串已以 "data: " 开头，则保持不变
   * 否则添加 "data: " 前缀和 "\n\n" 后缀
   *
   * @param flux JSON 字符串的 Flux 流
   * @return SSE 格式字符串的 Flux 流
   */
  public static Flux<String> addSsePrefix(Flux<String> flux) {
    return flux.map(jsonStr -> {
      if (jsonStr == null) {
        return "";
      }
      if (jsonStr.startsWith(SSE_DATA_PREFIX)) {
        return jsonStr;
      }
      return SSE_DATA_PREFIX + jsonStr + SSE_SUFFIX;
    });
  }

  /**
   * 创建一个发射 SseEvent 的 Flux
   *
   * @param events SseEvent 数组
   * @return SseEvent 对象的 Flux
   */
  public static Flux<SseEvent> fluxOf(SseEvent... events) {
    return Flux.fromArray(events);
  }

  // ==================== 便捷构建方法 ====================

  /**
   * 创建内容事件并格式化为 SSE 字符串（带 conversationId / messageId）
   */
  public static String content(String text, Long conversationId, Long messageId) {
    return format(SseEvent.content(text, conversationId, messageId));
  }

  /**
   * 创建内容事件并格式化为 SSE 字符串
   */
  public static String content(String text) {
    return format(SseEvent.content(text));
  }

  /**
   * 创建 AI 内容结束事件并格式化为 SSE 字符串
   */
  public static String contentDone(Long conversationId, Long messageId) {
    return format(SseEvent.contentDone(conversationId, messageId));
  }

  /**
   * 创建 TTS 语音合成结束事件并格式化为 SSE 字符串
   */
  public static String audioDone(String url, boolean timeout, String error,
      Long conversationId, Long messageId) {
    return format(SseEvent.audioDone(url, timeout, error, conversationId, messageId));
  }

  /**
   * 创建音频 URL 事件并格式化为 SSE 字符串（旧格式，保留向后兼容）
   * 
   * @deprecated 请使用 audioDone()
   */
  @Deprecated
  public static String audioUrl(String url) {
    return format(SseEvent.audioUrl(url));
  }

  /**
   * 创建 Token 使用统计事件并格式化为 SSE 字符串
   */
  public static String tokenUsage(int inputTokens, int outputTokens, long duration, String model) {
    return format(SseEvent.tokenUsage(inputTokens, outputTokens, duration, model));
  }

  /**
   * 创建工具调用事件并格式化为 SSE 字符串
   */
  public static String toolCall(String name, String arguments) {
    return format(SseEvent.toolCall(name, arguments));
  }

  /**
   * 创建工具结果事件并格式化为 SSE 字符串
   */
  public static String toolResult(String name, String result) {
    return format(SseEvent.toolResult(name, result));
  }

  /**
   * 创建缓存命中事件并格式化为 SSE 字符串
   */
  public static String cacheHit(String content) {
    return format(SseEvent.cacheHit(content));
  }

  /**
   * 创建澄清提示事件并格式化为 SSE 字符串
   */
  public static String clarification(String content) {
    return format(SseEvent.clarification(content));
  }

  /**
   * 创建错误事件并格式化为 SSE 字符串
   */
  public static String error(String message) {
    return format(SseEvent.error(message));
  }

  /**
   * 创建完成事件并格式化为 SSE 字符串
   */
  public static String done() {
    return format(SseEvent.done());
  }
}
