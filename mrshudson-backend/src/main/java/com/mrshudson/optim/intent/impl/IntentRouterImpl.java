package com.mrshudson.optim.intent.impl;

import com.mrshudson.optim.intent.IntentHandler;
import com.mrshudson.optim.intent.IntentRouter;
import com.mrshudson.optim.intent.IntentType;
import com.mrshudson.optim.intent.RouteResult;
import com.mrshudson.optim.intent.extract.ExtractionResult;
import com.mrshudson.optim.intent.extract.ParameterExtractor;
import com.mrshudson.optim.intent.handler.AbstractIntentHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 意图路由器实现类
 * 基于规则匹配实现意图分类和路由分发
 */
@Slf4j
public class IntentRouterImpl implements IntentRouter {

    /**
     * 置信度阈值，低于此值返回UNKNOWN
     */
    private static final double CONFIDENCE_THRESHOLD = 0.7;

    /**
     * 处理器注册表，使用ConcurrentHashMap保证线程安全
     */
    private final Map<IntentType, AbstractIntentHandler> handlers = new ConcurrentHashMap<>();

    /**
     * 参数提取器
     */
    private final ParameterExtractor parameterExtractor;

    /**
     * 路由器模式
     */
    private final String mode;

    /**
     * 路由器名称
     */
    private final String name;

    public IntentRouterImpl(ParameterExtractor parameterExtractor) {
        this(parameterExtractor, "rule-only", "RuleBasedIntentRouter");
    }

    public IntentRouterImpl(ParameterExtractor parameterExtractor, String mode, String name) {
        this.parameterExtractor = parameterExtractor;
        this.mode = mode;
        this.name = name;
    }

    @Override
    public IntentClassification classify(String query) {
        if (query == null || query.trim().isEmpty()) {
            log.warn("查询为空，返回UNKNOWN意图");
            return IntentClassification.unknown();
        }

        String trimmedQuery = query.trim();
        log.debug("开始意图分类: {}", trimmedQuery);

        // 收集所有处理器的匹配结果
        List<IntentMatch> matches = new ArrayList<>();

        for (Map.Entry<IntentType, AbstractIntentHandler> entry : handlers.entrySet()) {
            IntentHandler handler = entry.getValue();
            double confidence = handler.canHandle(trimmedQuery);

            if (confidence > 0) {
                matches.add(new IntentMatch(entry.getKey(), confidence, handler));
                log.debug("意图 {} 匹配置信度: {}", entry.getKey(), confidence);
            }
        }

        // 如果没有匹配结果，返回UNKNOWN
        if (matches.isEmpty()) {
            log.debug("无匹配意图，返回UNKNOWN");
            return IntentClassification.unknown();
        }

        // 按置信度排序，取最高者
        matches.sort(Comparator.comparingDouble(IntentMatch::getConfidence).reversed());
        IntentMatch bestMatch = matches.get(0);

        // 检查是否达到阈值
        if (bestMatch.getConfidence() < CONFIDENCE_THRESHOLD) {
            log.debug("最佳匹配置信度 {} 低于阈值 {}，返回UNKNOWN",
                    bestMatch.getConfidence(), CONFIDENCE_THRESHOLD);
            return IntentClassification.unknown();
        }

        log.info("意图分类结果: {}, 置信度: {}", bestMatch.getIntentType(), bestMatch.getConfidence());
        return IntentClassification.rule(bestMatch.getIntentType(), bestMatch.getConfidence());
    }

    @Override
    public RouteResult route(Long userId, String query) {
        long startTime = System.currentTimeMillis();

        if (userId == null) {
            log.error("用户ID为空");
            return RouteResult.error("用户ID不能为空");
        }

        if (query == null || query.trim().isEmpty()) {
            log.error("查询为空");
            return RouteResult.error("查询内容不能为空");
        }

        String trimmedQuery = query.trim();
        log.info("[{}] 开始路由用户 {} 的查询: {}", name, userId, trimmedQuery);

        try {
            // 1. 意图分类
            IntentClassification classification = classify(trimmedQuery);

            // 2. 如果置信度不足，标记为需要AI处理
            if (classification.getIntentType() == IntentType.UNKNOWN) {
                log.info("意图识别置信度不足，转交AI处理");
                RouteResult result = RouteResult.needAi(IntentType.GENERAL_CHAT, classification.getConfidence());
                result.setProcessTimeMs(System.currentTimeMillis() - startTime);
                return result;
            }

            // 3. 获取对应处理器
            AbstractIntentHandler handler = handlers.get(classification.getIntentType());
            if (handler == null) {
                log.warn("未找到意图 {} 的处理器，转交AI处理", classification.getIntentType());
                RouteResult result = RouteResult.needAi(classification.getIntentType(), classification.getConfidence());
                result.setProcessTimeMs(System.currentTimeMillis() - startTime);
                return result;
            }

            // 4. 参数提取
            Map<String, Object> parameters;
            if (handler.needParameterExtraction() && parameterExtractor != null) {
                ExtractionResult extractionResult = parameterExtractor.extract(trimmedQuery, classification.getIntentType());
                if (extractionResult.isSuccess()) {
                    parameters = extractionResult.getParameters();
                    log.debug("参数提取成功: {}", parameters);
                } else {
                    log.warn("参数提取失败: {}", extractionResult.getFailureReason());
                    parameters = extractionResult.getParameters(); // 使用部分提取的参数
                }
            } else {
                parameters = java.util.Collections.emptyMap();
            }

            // 5. 执行处理
            RouteResult result = handler.handle(userId, trimmedQuery, parameters);
            result.setRouterLayer(classification.getRouterLayer());
            result.setConfidence(classification.getConfidence());

            long processTime = System.currentTimeMillis() - startTime;
            result.setProcessTimeMs(processTime);

            log.info("[{}] 路由完成，处理时间: {}ms, 结果: handled={}",
                    name, processTime, result.isHandled());

            return result;

        } catch (Exception e) {
            log.error("[{}] 路由处理异常: {}", name, e.getMessage(), e);
            RouteResult errorResult = RouteResult.error("路由处理失败: " + e.getMessage());
            errorResult.setProcessTimeMs(System.currentTimeMillis() - startTime);
            return errorResult;
        }
    }

    @Override
    public void registerHandler(AbstractIntentHandler handler) {
        if (handler == null) {
            log.warn("尝试注册空的处理器，忽略");
            return;
        }

        IntentType intentType = handler.getIntentType();
        handlers.put(intentType, handler);
        log.info("[{}] 注册意图处理器: {} -> {}", name, intentType, handler.getClass().getSimpleName());
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getMode() {
        return mode;
    }

    /**
     * 获取已注册的处理器数量
     *
     * @return 处理器数量
     */
    public int getHandlerCount() {
        return handlers.size();
    }

    /**
     * 检查是否已注册指定意图类型的处理器
     *
     * @param intentType 意图类型
     * @return 是否已注册
     */
    public boolean hasHandler(IntentType intentType) {
        return handlers.containsKey(intentType);
    }

    /**
     * 意图匹配结果内部类
     */
    private static class IntentMatch {
        private final IntentType intentType;
        private final double confidence;
        private final IntentHandler handler;

        IntentMatch(IntentType intentType, double confidence, IntentHandler handler) {
            this.intentType = intentType;
            this.confidence = confidence;
            this.handler = handler;
        }

        IntentType getIntentType() {
            return intentType;
        }

        double getConfidence() {
            return confidence;
        }

        IntentHandler getHandler() {
            return handler;
        }
    }
}
