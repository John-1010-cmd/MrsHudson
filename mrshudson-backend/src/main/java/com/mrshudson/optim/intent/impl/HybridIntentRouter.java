package com.mrshudson.optim.intent.impl;

import com.mrshudson.optim.config.OptimProperties;
import com.mrshudson.optim.intent.cache.IntentCacheStore;
import com.mrshudson.optim.intent.cache.dto.IntentCacheEntry;
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
import java.util.Optional;
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
    private final IntentCacheStore intentCacheStore;

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

        // L2 缓存查询：IntentCacheStore
        if (intentCacheStore != null && userId != null) {
            Optional<IntentCacheEntry> cacheResult = intentCacheStore.lookup(query, userId);
            if (cacheResult.isPresent()) {
                IntentCacheEntry entry = cacheResult.get();
                log.debug("缓存命中，用户ID: {}, 意图: {}, 耗时: {}ms",
                    userId, entry.getIntentType(), System.currentTimeMillis() - startTime);
                return RouteResult.builder()
                    .handled(true)
                    .response(null) // 缓存命中不返回响应，只返回意图识别结果
                    .intentType(IntentType.valueOf(entry.getIntentType()))
                    .confidence(entry.getConfidence())
                    .parameters(Map.of()) // 简化处理，实际可从缓存恢复参数
                    .routerLayer("intent_cache")
                    .processTimeMs(System.currentTimeMillis() - startTime)
                    .build();
            }
        }

        // 第1层：规则提取
        RouteResult ruleResult = tryRuleLayer(userId, query);
        if (ruleResult != null && ruleResult.isHandled()) {
            log.debug("规则层处理成功，耗时: {}ms", System.currentTimeMillis() - startTime);
            // 缓存结果
            cacheResult(userId, query, ruleResult);
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
     * 缓存路由结果
     */
    private void cacheResult(Long userId, String query, RouteResult result) {
        if (intentCacheStore == null || userId == null || result == null) {
            return;
        }
        try {
            IntentCacheEntry entry = IntentCacheEntry.builder()
                .userId(userId)
                .intentType(result.getIntentType().name())
                .confidence(result.getConfidence())
                .needsClarification(false)
                .createdAt(java.time.LocalDateTime.now())
                .ttlSeconds((int) java.time.Duration.ofDays(7).getSeconds()) // L2 TTL
                .accessCount(1)
                .build();
            intentCacheStore.store(query, userId, entry);
        } catch (Exception e) {
            log.warn("缓存结果失败: {}", e.getMessage());
        }
    }

    /**
     * 简单的意图识别器
     * 创建类意图优先于查询类意图匹配
     * 特定实体关键词优先于通用创建关键词
     */
    private static class IntentRecognizer {

        // 创建类关键词 - 出现这些词时优先考虑创建意图
        private static final String[] CREATE_KEYWORDS = {
            "创建", "添加", "新建", "增加", "记", "记录"
        };

        // 删除/取消类关键词
        private static final String[] DELETE_KEYWORDS = {
            "删除", "取消", "移除", "删掉", "去掉"
        };

        // 完成类关键词
        private static final String[] COMPLETE_KEYWORDS = {
            "完成", "做完", "划掉", "结束", "搞定"
        };

        // 待办特定关键词（高优先级）
        private static final String[] TODO_SPECIFIC_KEYWORDS = {
            "待办", "任务", "todo"
        };

        // 日历特定关键词（高优先级）
        private static final String[] CALENDAR_SPECIFIC_KEYWORDS = {
            "会议", "日程", "约会", "日历"
        };

        static IntentType recognize(String query) {
            String lowerQuery = query.toLowerCase();
            log.debug("[IntentRecognizer] 开始识别意图: {}", query);

            // 第一步：优先检查特定实体关键词（避免"创建"通用词的歧义）
            // 检查待办相关（高优先级）
            for (String keyword : TODO_SPECIFIC_KEYWORDS) {
                if (lowerQuery.contains(keyword)) {
                    log.debug("[IntentRecognizer] 匹配到待办关键词: {}", keyword);
                    // 同时有创建关键词，确定是创建待办
                    if (hasCreateKeyword(lowerQuery)) {
                        log.debug("[IntentRecognizer] 识别为 TODO_CREATE（有创建关键词）");
                        return IntentType.TODO_CREATE;
                    }
                    // 没有创建关键词，可能是待办查询
                    log.debug("[IntentRecognizer] 识别为 TODO_QUERY（无创建关键词）");
                    return IntentType.TODO_QUERY;
                }
            }

            // 检查日历相关（高优先级）
            for (String keyword : CALENDAR_SPECIFIC_KEYWORDS) {
                if (lowerQuery.contains(keyword)) {
                    log.debug("[IntentRecognizer] 匹配到日历关键词: {}", keyword);
                    // 同时有创建关键词，确定是创建日程
                    if (hasCreateKeyword(lowerQuery)) {
                        log.debug("[IntentRecognizer] 识别为 CALENDAR_CREATE（有创建关键词）");
                        return IntentType.CALENDAR_CREATE;
                    }
                    // 没有创建关键词，可能是日程查询
                    log.debug("[IntentRecognizer] 识别为 CALENDAR_QUERY（无创建关键词）");
                    return IntentType.CALENDAR_QUERY;
                }
            }

            // 第二步：检查是否是创建类操作（通用创建关键词）
            if (hasCreateKeyword(lowerQuery)) {
                log.debug("[IntentRecognizer] 有创建关键词，检查通用匹配");
                // 检查日历创建
                if (containsAnyKeyword(lowerQuery, IntentType.CALENDAR_CREATE.getKeywords())) {
                    log.debug("[IntentRecognizer] 识别为 CALENDAR_CREATE（通用匹配）");
                    return IntentType.CALENDAR_CREATE;
                }
                // 检查待办创建
                if (containsAnyKeyword(lowerQuery, IntentType.TODO_CREATE.getKeywords())) {
                    log.debug("[IntentRecognizer] 识别为 TODO_CREATE（通用匹配）");
                    return IntentType.TODO_CREATE;
                }
            }

            // 第三步：检查删除/取消操作
            if (hasDeleteKeyword(lowerQuery)) {
                log.debug("[IntentRecognizer] 有删除关键词，检查通用匹配");
                // 优先检查日程删除
                if (containsAnyKeyword(lowerQuery, IntentType.CALENDAR_DELETE.getKeywords())) {
                    log.debug("[IntentRecognizer] 识别为 CALENDAR_DELETE");
                    return IntentType.CALENDAR_DELETE;
                }
                // 检查待办删除
                if (containsAnyKeyword(lowerQuery, IntentType.TODO_DELETE.getKeywords())) {
                    log.debug("[IntentRecognizer] 识别为 TODO_DELETE");
                    return IntentType.TODO_DELETE;
                }
            }

            // 第四步：检查完成操作
            if (hasCompleteKeyword(lowerQuery)) {
                log.debug("[IntentRecognizer] 有完成关键词，检查通用匹配");
                // 检查待办完成
                if (containsAnyKeyword(lowerQuery, IntentType.TODO_COMPLETE.getKeywords())) {
                    log.debug("[IntentRecognizer] 识别为 TODO_COMPLETE");
                    return IntentType.TODO_COMPLETE;
                }
            }

            // 第五步：按原有顺序检查其他意图类型
            for (IntentType type : IntentType.values()) {
                // 跳过创建类意图（已处理）
                if (type == IntentType.CALENDAR_CREATE || type == IntentType.TODO_CREATE) {
                    continue;
                }
                if (type.getKeywords() != null && !type.getKeywords().isEmpty()) {
                    for (String keyword : type.getKeywords()) {
                        if (lowerQuery.contains(keyword.toLowerCase())) {
                            log.debug("[IntentRecognizer] 识别为 {}（关键词: {}）", type, keyword);
                            return type;
                        }
                    }
                }
            }

            log.debug("[IntentRecognizer] 无法识别意图，返回 UNKNOWN");
            return IntentType.UNKNOWN;
        }

        private static boolean hasCreateKeyword(String query) {
            for (String keyword : CREATE_KEYWORDS) {
                if (query.contains(keyword)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean hasDeleteKeyword(String query) {
            for (String keyword : DELETE_KEYWORDS) {
                if (query.contains(keyword)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean hasCompleteKeyword(String query) {
            for (String keyword : COMPLETE_KEYWORDS) {
                if (query.contains(keyword)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean containsAnyKeyword(String query, java.util.Set<String> keywords) {
            if (keywords == null || keywords.isEmpty()) {
                return false;
            }
            for (String keyword : keywords) {
                if (query.contains(keyword.toLowerCase())) {
                    return true;
                }
            }
            return false;
        }
    }
}
