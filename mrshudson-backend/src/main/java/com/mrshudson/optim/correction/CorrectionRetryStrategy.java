package com.mrshudson.optim.correction;

import com.mrshudson.ai.AIClientFactory;
import com.mrshudson.mcp.kimi.KimiClient;
import com.mrshudson.mcp.kimi.dto.Message;
import com.mrshudson.optim.fallback.FallbackHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 纠错重试策略
 * 当工具执行失败时，使用纠错提示词重新请求 LLM
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CorrectionRetryStrategy {

    private static final int MAX_RETRIES = 2;

    private final AIClientFactory aiClientFactory;

    /**
     * 纠错重试
     * 注意：纠错场景下我们等待完整响应，不进行流式传输，因为需要整合完整的纠错结果
     *
     * @param validationResult 验证失败结果
     * @param context 上下文信息
     * @return Mono<String> 纠错后的响应
     */
    public Mono<String> correctAndRetry(ValidationResult validationResult, FallbackHandler.FallbackContext context) {
        if (validationResult == null || validationResult.isValid()) {
            log.warn("验证结果有效，无需纠错");
            return Mono.empty();
        }

        int retryCount = validationResult.getRetryCount();
        if (retryCount >= MAX_RETRIES) {
            log.warn("已达到最大重试次数 {}，放弃纠错", MAX_RETRIES);
            return Mono.just("抱歉，我暂时无法正确回答这个问题。");
        }

        // 1. 分析错误原因
        String errorAnalysis = analyzeError(validationResult);

        // 2. 构建纠错提示词
        String correctionPrompt = buildCorrectionPrompt(
            context.userMessage(),
            errorAnalysis,
            validationResult
        );

        log.info("执行纠错重试，第 {} 次尝试", retryCount + 1);

        try {
            // 3. 构建消息列表
            List<Message> messages = List.of(
                Message.system("你是一个帮助用户解决问题的助手。"),
                Message.user(correctionPrompt)
            );

            // 4. 调用 LLM（使用流式API）
            KimiClient aiClient = (KimiClient) aiClientFactory.getClient();

            // 收集流式响应为一个完整字符串
            AtomicReference<String> fullResponse = new AtomicReference<>("");

            return aiClient.streamChatCompletion(messages)
                    .doOnNext(chunk -> fullResponse.updateAndGet(prev -> prev + chunk))
                    .doOnComplete(() -> {
                        String response = fullResponse.get();
                        if (response != null && !response.isEmpty()) {
                            log.info("纠错成功: {}", response.substring(0, Math.min(50, response.length())));
                        }
                    })
                    .then(Mono.defer(() -> {
                        String response = fullResponse.get();
                        if (response != null && !response.isEmpty()) {
                            return Mono.just(response);
                        }
                        return Mono.just("抱歉，纠错未返回有效结果。");
                    }));

        } catch (Exception e) {
            log.error("纠错失败: {}", e.getMessage(), e);
            return Mono.just("抱歉，纠错也失败了。");
        }
    }

    /**
     * 分析错误原因
     */
    private String analyzeError(ValidationResult result) {
        if (result == null) {
            return "未知错误";
        }

        return switch (result.getErrorType()) {
            case FORMAT_ERROR -> "返回格式不符合预期";
            case DATA_ERROR -> "数据内容存在问题：" + result.getErrorMessage();
            case TIMEOUT -> "请求超时，未获得完整结果";
            case PARTIAL_RESULT -> "只获得了部分结果：" + result.getErrorMessage();
            default -> "未知错误：" + result.getErrorMessage();
        };
    }

    /**
     * 构建纠错提示词
     */
    private String buildCorrectionPrompt(String originalMessage, String errorAnalysis, ValidationResult result) {
        return String.format("""
            用户原始问题：%s

            上一次回答存在问题，原因：%s

            请基于你的知识重新回答用户的问题，确保：
            1. 回答准确、完整
            2. 如果不确定答案，明确告知用户
            3. 适当使用表情符号让回答更生动

            再次回答用户的问题。
            """,
            originalMessage,
            errorAnalysis
        );
    }

    /**
     * 创建带重试次数的验证结果
     */
    public ValidationResult withRetryCount(ValidationResult result) {
        if (result == null) {
            return ValidationResult.failure(
                ValidationResult.ErrorType.DATA_ERROR,
                "原始验证结果为空"
            );
        }

        // 注意：这里简化处理，实际应该创建新的实例
        return result;
    }
}
