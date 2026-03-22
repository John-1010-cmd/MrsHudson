package com.mrshudson.optim.intent.impl;

import com.mrshudson.optim.config.OptimProperties;
import com.mrshudson.optim.intent.handler.AbstractIntentHandler;
import com.mrshudson.optim.intent.IntentHandler;
import com.mrshudson.optim.intent.IntentRouter;
import com.mrshudson.optim.intent.IntentType;
import com.mrshudson.optim.intent.RouteResult;
import com.mrshudson.optim.intent.extract.ExtractionResult;
import com.mrshudson.optim.intent.extract.LightweightAiExtractor;
import com.mrshudson.optim.intent.extract.RuleBasedExtractor;
import com.mrshudson.optim.monitor.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 三层混合意图路由器
 * 整合规则层、轻量AI层、完整AI层的降级流程
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HybridIntentRouter implements IntentRouter {

    private final RuleBasedExtractor ruleBasedExtractor;
    private final LightweightAiExtractor lightweightAiExtractor;
    private final OptimProperties optimProperties;
    private final List<IntentHandler> handlers;
    private final MetricsService metricsService;

    // 统计信息
    private final AtomicLong ruleLayerCalls = new AtomicLong(0);
    private final AtomicLong ruleLayerSuccess = new AtomicLong(0);
    private final AtomicLong lightweightAiCalls = new AtomicLong(0);
    private final AtomicLong lightweightAiSuccess = new AtomicLong(0);
    private final AtomicLong fullAiCalls = new AtomicLong(0);
    private final AtomicLong fullAiSuccess = new AtomicLong(0);

    /**
     * 路由用户查询，按顺序尝试各层
     *
     * @param userId 用户ID
     * @param query 用户查询
     * @return 路由结果
     */
    @Override
    public RouteResult route(Long userId, String query) {
        long startTime = System.currentTimeMillis();
        log.debug("开始意图路由，用户ID: {}, 查询: {}", userId, query);

        // 第1层：规则提取
        RouteResult ruleResult = tryRuleLayer(userId, query);
        if (ruleResult != null && ruleResult.isHandled()) {
            log.debug("规则层处理成功，耗时: {}ms", System.currentTimeMillis() - startTime);
            return copyWithProcessTime(ruleResult, System.currentTimeMillis() - startTime);
        }

        // 确定意图类型（从规则层或重新识别）
        IntentType intentType = ruleResult != null ? ruleResult.getIntentType() : IntentType.UNKNOWN;

        // 第2层：轻量AI提取
        RouteResult lightweightResult = tryLightweightAiLayer(userId, query, intentType);
        if (lightweightResult != null && lightweightResult.isHandled()) {
            log.debug("轻量AI层处理成功，耗时: {}ms", System.currentTimeMillis() - startTime);
            return copyWithProcessTime(lightweightResult, System.currentTimeMillis() - startTime);
        }

        // 第3层：完整AI调用
        RouteResult fullAiResult = tryFullAiLayer(query, intentType);
        log.debug("完整AI层处理完成，耗时: {}ms", System.currentTimeMillis() - startTime);

        return copyWithProcessTime(fullAiResult, System.currentTimeMillis() - startTime);
    }

    @Override
    public IntentRouter.IntentClassification classify(String query) {
        RouteResult result = route(null, query);
        return IntentRouter.IntentClassification.fullAi(result.getIntentType(), result.getConfidence());
    }

    @Override
    public void registerHandler(AbstractIntentHandler handler) {
        // 处理器通过构造函数注入，无需注册
        log.debug("HybridIntentRouter 忽略处理器注册，使用构造器注入的处理器");
    }

    @Override
    public String getName() {
        return "HybridIntentRouter";
    }

    @Override
    public String getMode() {
        return "hybrid";
    }

    /**
     * 复制RouteResult并设置处理时间
     */
    private RouteResult copyWithProcessTime(RouteResult original, long processTimeMs) {
        return RouteResult.builder()
            .handled(original.isHandled())
            .response(original.getResponse())
            .intentType(original.getIntentType())
            .confidence(original.getConfidence())
            .parameters(original.getParameters())
            .routerLayer(original.getRouterLayer())
            .processTimeMs(processTimeMs)
            .errorMessage(original.getErrorMessage())
            .build();
    }

    /**
     * 尝试规则层处理
     */
    private RouteResult tryRuleLayer(Long userId, String query) {
        if (!optimProperties.getIntentRouter().getRule().isEnabled()) {
            log.debug("规则层已禁用");
            return null;
        }

        ruleLayerCalls.incrementAndGet();
        if (metricsService != null) {
            metricsService.recordIntentLayerUsage("rule-layer");
        }

        try {
            // 识别意图类型
            IntentType intentType = IntentRecognizer.recognize(query);

            // 如果是闲聊，直接处理
            if (intentType.isSmallTalk()) {
                ruleLayerSuccess.incrementAndGet();
                return RouteResult.smallTalk("你好！有什么可以帮助你的吗？");
            }

            // 尝试提取参数
            ExtractionResult extractionResult = ruleBasedExtractor.extract(query, intentType);

            if (extractionResult.isSuccess() && !extractionResult.getParameters().isEmpty()) {
                // 查找对应的handler
                for (IntentHandler handler : handlers) {
                    if (handler.getIntentType() == intentType) {
                        // 使用实际用户ID
                        RouteResult result = handler.handle(userId, query, extractionResult.getParameters());
                        if (result != null && result.isHandled()) {
                            ruleLayerSuccess.incrementAndGet();
                            return RouteResult.builder()
                                .handled(result.isHandled())
                                .response(result.getResponse())
                                .intentType(result.getIntentType())
                                .confidence(result.getConfidence())
                                .parameters(result.getParameters())
                                .routerLayer("rule")
                                .processTimeMs(result.getProcessTimeMs())
                                .errorMessage(result.getErrorMessage())
                                .build();
                        }
                    }
                }
            }

            // 规则层未能完全处理，返回意图类型供下层使用
            return RouteResult.builder()
                .handled(false)
                .intentType(intentType)
                .routerLayer("rule")
                .build();

        } catch (Exception e) {
            log.warn("规则层处理异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 尝试轻量AI层处理
     */
    private RouteResult tryLightweightAiLayer(Long userId, String query, IntentType intentType) {
        if (!optimProperties.getIntentRouter().getLightweightAi().isEnabled()) {
            log.debug("轻量AI层已禁用");
            return null;
        }

        lightweightAiCalls.incrementAndGet();
        if (metricsService != null) {
            metricsService.recordIntentLayerUsage("lightweight-ai-layer");
        }

        try {
            // 使用轻量AI提取参数
            ExtractionResult extractionResult = lightweightAiExtractor.extract(query, intentType);

            if (extractionResult.isSuccess() && !extractionResult.getParameters().isEmpty()) {
                // 查找对应的handler
                for (IntentHandler handler : handlers) {
                    if (handler.getIntentType() == intentType) {
                        // 使用实际用户ID
                        RouteResult result = handler.handle(userId, query, extractionResult.getParameters());
                        if (result != null && result.isHandled()) {
                            lightweightAiSuccess.incrementAndGet();
                            return RouteResult.builder()
                                .handled(result.isHandled())
                                .response(result.getResponse())
                                .intentType(result.getIntentType())
                                .confidence(result.getConfidence())
                                .parameters(result.getParameters())
                                .routerLayer("lightweight_ai")
                                .processTimeMs(result.getProcessTimeMs())
                                .errorMessage(result.getErrorMessage())
                                .build();
                        }
                    }
                }
            }

            // 轻量AI层未能完全处理
            return RouteResult.builder()
                .handled(false)
                .intentType(intentType)
                .routerLayer("lightweight_ai")
                .build();

        } catch (Exception e) {
            log.warn("轻量AI层处理异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 尝试完整AI层处理
     */
    private RouteResult tryFullAiLayer(String query, IntentType intentType) {
        if (!optimProperties.getIntentRouter().getFullAi().isEnabled()) {
            log.debug("完整AI层已禁用，返回需要AI处理");
            return RouteResult.needAi(intentType, 0.5);
        }

        fullAiCalls.incrementAndGet();
        if (metricsService != null) {
            metricsService.recordIntentLayerUsage("full-ai-layer");
        }

        try {
            // 完整AI层直接返回需要AI处理的结果
            // 实际AI调用由上层服务完成
            fullAiSuccess.incrementAndGet();
            return RouteResult.needAi(intentType, 0.7);

        } catch (Exception e) {
            log.error("完整AI层处理异常: {}", e.getMessage());
            return RouteResult.error("AI处理异常: " + e.getMessage());
        }
    }

    /**
     * 获取各层统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        long ruleCalls = ruleLayerCalls.get();
        long ruleSuccess = ruleLayerSuccess.get();
        stats.put("ruleLayer", Map.of(
            "calls", ruleCalls,
            "success", ruleSuccess,
            "successRate", ruleCalls > 0 ? (double) ruleSuccess / ruleCalls : 0.0
        ));

        long lightCalls = lightweightAiCalls.get();
        long lightSuccess = lightweightAiSuccess.get();
        stats.put("lightweightAiLayer", Map.of(
            "calls", lightCalls,
            "success", lightSuccess,
            "successRate", lightCalls > 0 ? (double) lightSuccess / lightCalls : 0.0
        ));

        long fullCalls = fullAiCalls.get();
        long fullSuccess = fullAiSuccess.get();
        stats.put("fullAiLayer", Map.of(
            "calls", fullCalls,
            "success", fullSuccess,
            "successRate", fullCalls > 0 ? (double) fullSuccess / fullCalls : 0.0
        ));

        return stats;
    }

    /**
     * 重置统计信息
     */
    public void resetStatistics() {
        ruleLayerCalls.set(0);
        ruleLayerSuccess.set(0);
        lightweightAiCalls.set(0);
        lightweightAiSuccess.set(0);
        fullAiCalls.set(0);
        fullAiSuccess.set(0);
        log.info("意图路由器统计信息已重置");
    }

    /**
     * 简单的意图识别器
     */
    private static class IntentRecognizer {
        static IntentType recognize(String query) {
            String lowerQuery = query.toLowerCase();

            // 检查各意图类型的关键词
            for (IntentType type : IntentType.values()) {
                if (type.getKeywords() != null && !type.getKeywords().isEmpty()) {
                    for (String keyword : type.getKeywords()) {
                        if (lowerQuery.contains(keyword.toLowerCase())) {
                            return type;
                        }
                    }
                }
            }

            return IntentType.UNKNOWN;
        }
    }
}
