package com.mrshudson.optim.intent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 意图路由结果
 * 封装意图识别和处理的完整结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteResult {

    /**
     * 是否已处理（无需再走AI）
     */
    private boolean handled;

    /**
     * 直接返回的响应（如工具查询结果）
     */
    private String response;

    /**
     * 识别到的意图类型
     */
    private IntentType intentType;

    /**
     * 置信度 (0-1)
     */
    private double confidence;

    /**
     * 提取的参数
     */
    private Map<String, Object> parameters;

    /**
     * 使用的路由层：rule, lightweight_ai, full_ai
     */
    private String routerLayer;

    /**
     * 处理时间（毫秒）
     */
    private long processTimeMs;

    /**
     * 错误信息（如果有）
     */
    private String errorMessage;

    /**
     * 创建成功结果
     */
    public static RouteResult success(IntentType intentType, String response, Map<String, Object> parameters) {
        return RouteResult.builder()
                .handled(true)
                .response(response)
                .intentType(intentType)
                .confidence(0.95)
                .parameters(parameters)
                .routerLayer("rule")
                .build();
    }

    /**
     * 创建需要AI处理的结果
     */
    public static RouteResult needAi(IntentType intentType, double confidence) {
        return RouteResult.builder()
                .handled(false)
                .intentType(intentType != null ? intentType : IntentType.GENERAL_CHAT)
                .confidence(confidence)
                .routerLayer("full_ai")
                .build();
    }

    /**
     * 创建失败结果
     */
    public static RouteResult error(String errorMessage) {
        return RouteResult.builder()
                .handled(false)
                .intentType(IntentType.UNKNOWN)
                .confidence(0.0)
                .errorMessage(errorMessage)
                .routerLayer("error")
                .build();
    }

    /**
     * 创建闲聊响应结果
     */
    public static RouteResult smallTalk(String response) {
        return RouteResult.builder()
                .handled(true)
                .response(response)
                .intentType(IntentType.SMALL_TALK)
                .confidence(1.0)
                .routerLayer("rule")
                .build();
    }
}
