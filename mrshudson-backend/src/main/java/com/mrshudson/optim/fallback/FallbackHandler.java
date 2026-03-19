package com.mrshudson.optim.fallback;

import com.mrshudson.optim.intent.IntentType;
import com.mrshudson.mcp.kimi.dto.Message;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 兜底回答处理器接口
 * 当工具不可用或用户问题超出工具范围时，使用 LLM 直接回答
 */
public interface FallbackHandler {

    /**
     * 判断是否需要兜底
     *
     * @param message 用户消息
     * @param intentType 识别的意图类型
     * @param toolResult 工具执行结果（可能为 null 或失败）
     * @return boolean 是否需要兜底
     */
    boolean shouldFallback(String message, IntentType intentType, Object toolResult);

    /**
     * 执行兜底回答
     *
     * @param message 用户消息
     * @param context 上下文信息
     * @return Flux<String> 流式响应
     */
    Flux<String> executeFallback(String message, FallbackContext context);

    /**
     * 兜底上下文
     */
    record FallbackContext(
        String userMessage,
        Long userId,
        Long conversationId,
        IntentType originalIntent,
        Object failedToolResult,
        List<String> availableTools,
        List<Message> conversationHistory
    ) {}
}
