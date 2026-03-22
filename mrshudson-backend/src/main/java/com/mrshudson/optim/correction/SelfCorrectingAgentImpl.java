package com.mrshudson.optim.correction;

import com.mrshudson.optim.fallback.FallbackHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 自纠错代理实现
 * 集成 ToolResultValidator、CorrectionRetryStrategy、ErrorPatternLearner
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelfCorrectingAgentImpl implements SelfCorrectingAgent {

    private final ToolResultValidator toolResultValidator;
    private final CorrectionRetryStrategy correctionRetryStrategy;
    private final ErrorPatternLearner errorPatternLearner;

    @Override
    public ValidationResult validate(String toolName, String result, Object originalRequest) {
        // 调用工具结果校验器进行验证
        ValidationResult validationResult = toolResultValidator.validate(toolName, result, originalRequest);

        // 如果验证失败，记录错误模式
        if (!validationResult.isValid()) {
            log.warn("工具 {} 验证失败: {}", toolName, validationResult.getErrorMessage());
            errorPatternLearner.recordErrorPattern(
                toolName,
                validationResult.getErrorType().name(),
                originalRequest != null ? originalRequest.toString() : ""
            );
        }

        return validationResult;
    }

    @Override
    public Mono<String> correctAndRetry(ValidationResult validationResult, FallbackHandler.FallbackContext context) {
        if (validationResult == null || validationResult.isValid()) {
            log.warn("验证结果有效，无需纠错");
            return Mono.empty();
        }

        // 检查重试次数
        int currentRetryCount = validationResult.getRetryCount();
        if (currentRetryCount >= 2) {
            log.warn("已达到最大重试次数，放弃纠错");
            return Mono.just("抱歉，我暂时无法正确回答这个问题。");
        }

        // 创建带重试次数的验证结果
        ValidationResult retryResult = ValidationResult.builder()
            .isValid(validationResult.isValid())
            .errorType(validationResult.getErrorType())
            .errorMessage(validationResult.getErrorMessage())
            .confidence(validationResult.getConfidence())
            .retryCount(currentRetryCount + 1)
            .build();

        log.info("执行纠错重试，第 {} 次尝试", retryResult.getRetryCount());

        // 执行纠错重试
        return correctionRetryStrategy.correctAndRetry(retryResult, context);
    }
}
