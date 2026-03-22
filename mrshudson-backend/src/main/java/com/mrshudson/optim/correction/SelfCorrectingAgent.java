package com.mrshudson.optim.correction;

import com.mrshudson.optim.fallback.FallbackHandler;
import reactor.core.publisher.Mono;

/**
 * 自纠错代理接口
 * 执行后验证结果，失败时自动重试或降级
 */
public interface SelfCorrectingAgent {

    /**
     * 验证工具执行结果
     *
     * @param toolName       工具名称
     * @param result         执行结果
     * @param originalRequest 原始请求
     * @return ValidationResult 验证结果
     */
    ValidationResult validate(String toolName, String result, Object originalRequest);

    /**
     * 执行纠错
     *
     * @param validationResult 验证失败的结果
     * @param context         上下文
     * @return Mono<String> 纠错后的响应
     */
    Mono<String> correctAndRetry(ValidationResult validationResult, FallbackHandler.FallbackContext context);
}
