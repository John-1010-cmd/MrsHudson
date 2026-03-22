package com.mrshudson.optim.fallback;

import com.mrshudson.ai.AIClientFactory;
import com.mrshudson.mcp.kimi.KimiClient;
import com.mrshudson.mcp.kimi.dto.Message;
import com.mrshudson.optim.config.OptimProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 兜底处理器默认实现
 * 当工具不可用或用户问题超出工具范围时，使用 LLM 直接回答
 * 支持真正的流式响应
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FallbackHandlerImpl implements FallbackHandler {

    private final AIClientFactory aiClientFactory;
    private final FallbackDecisionStrategy decisionStrategy;
    private final FallbackPromptBuilder promptBuilder;
    private final OptimProperties optimProperties;

    @Override
    public boolean shouldFallback(String message, com.mrshudson.optim.intent.IntentType intentType, Object toolResult) {
        return decisionStrategy.needFallback(message, intentType, toolResult);
    }

    @Override
    public Mono<String> executeFallback(String message, FallbackContext context) {
        log.info("执行兜底回答，用户消息: {}, 原始意图: {}", message, context.originalIntent());

        try {
            // 1. 构建系统提示词
            String currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
            String systemPrompt = promptBuilder.buildPrompt(message, context.availableTools(), currentDate);

            // 2. 构建消息列表
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system(systemPrompt));

            // 3. 添加历史消息（如果有）
            if (context.conversationHistory() != null && !context.conversationHistory().isEmpty()) {
                messages.addAll(context.conversationHistory());
            }

            // 4. 添加当前用户消息
            messages.add(Message.user(message));

            // 5. 获取 AI Client
            KimiClient aiClient = (KimiClient) aiClientFactory.getClient();

            // 6. 调用流式 AI API 并收集完整响应
            return aiClient.streamChatCompletion(messages)
                    .collectList()
                    .map(chunks -> {
                        String fullResponse = String.join("", chunks);
                        String indicator = promptBuilder.getFallbackIndicator();
                        return indicator + "\n\n" + fullResponse;
                    })
                    .doOnSuccess(result -> log.info("兜底回答完成，响应长度: {}", result.length()))
                    .onErrorResume(e -> {
                        log.error("兜底回答失败: {}", e.getMessage(), e);
                        return Mono.just("抱歉，我现在无法回答这个问题，请稍后再试。");
                    });

        } catch (Exception e) {
            log.error("兜底回答失败: {}", e.getMessage(), e);
            return Mono.just("抱歉，我现在无法回答这个问题，请稍后再试。");
        }
    }
}
