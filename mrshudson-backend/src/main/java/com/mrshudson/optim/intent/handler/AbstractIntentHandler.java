package com.mrshudson.optim.intent.handler;

import com.mrshudson.optim.intent.IntentHandler;
import com.mrshudson.optim.intent.IntentType;
import com.mrshudson.optim.intent.RouteResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 意图处理器抽象基类
 * 提供通用功能实现
 */
@Slf4j
public abstract class AbstractIntentHandler implements IntentHandler {

    /**
     * 记录处理日志
     */
    protected void logHandle(Long userId, String query, IntentType intentType) {
        log.info("[{}] 处理用户{}的查询: {}, 意图: {}",
                this.getClass().getSimpleName(), userId, query, intentType.getDescription());
    }

    /**
     * 记录参数提取日志
     */
    protected void logParameterExtraction(Map<String, Object> parameters) {
        if (log.isDebugEnabled()) {
            log.debug("提取参数: {}", parameters);
        }
    }

    /**
     * 记录处理结果
     */
    protected void logResult(RouteResult result) {
        if (log.isDebugEnabled()) {
            log.debug("路由结果: handled={}, intent={}, confidence={}",
                    result.isHandled(), result.getIntentType(), result.getConfidence());
        }
    }

    /**
     * 记录错误
     */
    protected void logError(String operation, Throwable error) {
        log.error("[{}] {} 失败: {}", this.getClass().getSimpleName(), operation, error.getMessage(), error);
    }

    /**
     * 创建成功结果（简化版）
     */
    protected RouteResult createSuccessResult(String response, Map<String, Object> parameters) {
        return RouteResult.success(getIntentType(), response, parameters);
    }

    /**
     * 创建错误结果
     */
    protected RouteResult createErrorResult(String errorMessage) {
        log.error("意图处理错误: {}", errorMessage);
        return RouteResult.error(errorMessage);
    }

    /**
     * 从参数中提取字符串值
     */
    protected String getStringParameter(Map<String, Object> parameters, String key, String defaultValue) {
        if (parameters == null || !parameters.containsKey(key)) {
            return defaultValue;
        }
        Object value = parameters.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * 从参数中提取整数值
     */
    protected int getIntParameter(Map<String, Object> parameters, String key, int defaultValue) {
        if (parameters == null || !parameters.containsKey(key)) {
            return defaultValue;
        }
        Object value = parameters.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public RouteResult handle(Long userId, String query, Map<String, Object> parameters) {
        long startTime = System.currentTimeMillis();
        try {
            logHandle(userId, query, getIntentType());
            logParameterExtraction(parameters);

            RouteResult result = doHandle(userId, query, parameters);

            long processTime = System.currentTimeMillis() - startTime;
            result.setProcessTimeMs(processTime);
            logResult(result);

            return result;
        } catch (Exception e) {
            logError("handle", e);
            return createErrorResult("处理失败: " + e.getMessage());
        }
    }

    /**
     * 子类实现的具体处理逻辑
     */
    protected abstract RouteResult doHandle(Long userId, String query, Map<String, Object> parameters);
}
