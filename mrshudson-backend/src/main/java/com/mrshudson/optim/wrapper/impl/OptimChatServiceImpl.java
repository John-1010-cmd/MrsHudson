package com.mrshudson.optim.wrapper.impl;

import com.mrshudson.domain.dto.*;
import com.mrshudson.optim.cache.SemanticCacheService;
import com.mrshudson.optim.compress.ConversationSummarizer;
import com.mrshudson.optim.config.OptimProperties;
import com.mrshudson.optim.intent.IntentType;
import com.mrshudson.optim.intent.RouteResult;
import com.mrshudson.optim.intent.impl.HybridIntentRouter;
import com.mrshudson.optim.monitor.CostMonitorService;
import com.mrshudson.optim.monitor.MetricsService;
import com.mrshudson.optim.wrapper.OptimChatService;
import com.mrshudson.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 优化后的对话服务实现类
 * 包装原始 ChatService，添加语义缓存、意图路由、对话压缩等优化层
 *
 * 执行顺序：
 * 1. 检查语义缓存
 * 2. 意图路由（三层混合）
 * 3. 对话压缩（如需要）
 * 4. 调用AI
 * 5. 更新语义缓存
 * 6. 记录成本统计
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class OptimChatServiceImpl implements OptimChatService {

    private final ChatService delegate;
    private final SemanticCacheService semanticCacheService;
    private final HybridIntentRouter hybridIntentRouter;
    private final ConversationSummarizer conversationSummarizer;
    private final CostMonitorService costMonitorService;
    private final MetricsService metricsService;
    private final OptimProperties optimProperties;

    // 运行时配置开关（可动态调整）
    private volatile boolean semanticCacheEnabled = true;
    private volatile boolean intentRouterEnabled = true;
    private volatile boolean compressionEnabled = true;
    private volatile boolean costMonitorEnabled = true;

    // 统计信息
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong intentRouterHandled = new AtomicLong(0);
    private final AtomicLong compressionCount = new AtomicLong(0);
    private final AtomicLong totalAiCalls = new AtomicLong(0);
    private final AtomicLong totalCostSaved = new AtomicLong(0);

    @Override
    public SendMessageResponse sendMessage(Long userId, SendMessageRequest request) {
        long startTime = System.currentTimeMillis();
        String userMessage = request.getMessage();
        Long conversationId = request.getConversationId();

        log.debug("OptimChatService 处理消息，用户ID: {}, 消息: {}", userId, userMessage);

        // ========== 第1层：检查语义缓存 ==========
        if (isSemanticCacheEnabled()) {
            Optional<String> cachedResponse = semanticCacheService.get(userId, userMessage);
            if (cachedResponse.isPresent()) {
                log.info("语义缓存命中，用户ID: {}", userId);
                cacheHits.incrementAndGet();
                metricsService.recordSemanticCacheHit(); // 记录实际指标

                // 记录缓存命中成本
                if (isCostMonitorEnabled()) {
                    costMonitorService.recordCacheHit(userId, conversationId, "kimi", "chat");
                }

                // 构建缓存响应
                return buildCachedResponse(cachedResponse.get());
            }
            cacheMisses.incrementAndGet();
            metricsService.recordSemanticCacheMiss(); // 记录实际指标
            log.debug("语义缓存未命中，用户ID: {}", userId);
        }

        // ========== 第2层：意图路由 ==========
        if (isIntentRouterEnabled()) {
            RouteResult routeResult = hybridIntentRouter.route(userMessage);

            if (routeResult.isHandled()) {
                log.info("意图路由直接处理，类型: {}, 层: {}",
                        routeResult.getIntentType(), routeResult.getRouterLayer());
                intentRouterHandled.incrementAndGet();

                // 记录成本节省（意图路由绕过AI调用）
                if (isCostMonitorEnabled()) {
                    recordIntentRouterCostSaving(userId, conversationId, routeResult);
                }

                // 更新语义缓存
                if (isSemanticCacheEnabled() && routeResult.getResponse() != null) {
                    semanticCacheService.put(userId, userMessage, routeResult.getResponse());
                }

                return buildRoutedResponse(routeResult);
            }

            log.debug("意图路由未处理，需要AI调用，意图类型: {}", routeResult.getIntentType());
        }

        // ========== 第3层：对话压缩 ==========
        // 对话压缩在原始 ChatService 内部处理消息列表时进行
        // 这里仅记录统计信息
        if (isCompressionEnabled()) {
            log.debug("对话压缩已启用，将在需要时自动执行");
        }

        // ========== 第4层：调用原始 ChatService（AI调用） ==========
        log.debug("调用原始 ChatService 进行AI处理");
        totalAiCalls.incrementAndGet();

        SendMessageResponse response = delegate.sendMessage(userId, request);

        // ========== 第5层：更新语义缓存 ==========
        if (isSemanticCacheEnabled() && response.getContent() != null) {
            semanticCacheService.put(userId, userMessage, response.getContent());
            log.debug("语义缓存已更新");
        }

        // ========== 第6层：记录成本统计 ==========
        if (isCostMonitorEnabled()) {
            recordAiCall(userId, conversationId, response);
        }

        long processTime = System.currentTimeMillis() - startTime;
        log.debug("OptimChatService 处理完成，耗时: {}ms", processTime);

        return response;
    }

    @Override
    public ChatHistoryResponse getChatHistory(Long userId, int limit) {
        return delegate.getChatHistory(userId, limit);
    }

    @Override
    public ChatHistoryResponse getChatHistoryByConversation(Long userId, Long conversationId, int limit) {
        return delegate.getChatHistoryByConversation(userId, conversationId, limit);
    }

    @Override
    public ConversationDTO createConversation(Long userId, CreateConversationRequest request) {
        return delegate.createConversation(userId, request);
    }

    @Override
    public ConversationListResponse getConversationList(Long userId, int limit) {
        return delegate.getConversationList(userId, limit);
    }

    @Override
    public void deleteConversation(Long userId, Long conversationId) {
        delegate.deleteConversation(userId, conversationId);
    }

    @Override
    public AIProviderResponse getAIProviders() {
        return delegate.getAIProviders();
    }

    @Override
    public OptimConfigStatus getOptimConfigStatus() {
        return new OptimConfigStatus(
                semanticCacheEnabled,
                intentRouterEnabled,
                compressionEnabled,
                costMonitorEnabled
        );
    }

    @Override
    public void updateOptimConfig(OptimConfigStatus configStatus) {
        this.semanticCacheEnabled = configStatus.isSemanticCacheEnabled();
        this.intentRouterEnabled = configStatus.isIntentRouterEnabled();
        this.compressionEnabled = configStatus.isCompressionEnabled();
        this.costMonitorEnabled = configStatus.isCostMonitorEnabled();

        log.info("优化层配置已更新: semanticCache={}, intentRouter={}, compression={}, costMonitor={}",
                semanticCacheEnabled, intentRouterEnabled, compressionEnabled, costMonitorEnabled);
    }

    @Override
    public OptimStatistics getStatistics() {
        OptimStatistics stats = new OptimStatistics();
        stats.setCacheHits(cacheHits.get());
        stats.setCacheMisses(cacheMisses.get());
        stats.setIntentRouterHandled(intentRouterHandled.get());
        stats.setCompressionCount(compressionCount.get());
        stats.setTotalAiCalls(totalAiCalls.get());
        stats.setTotalCostSaved(totalCostSaved.get());
        return stats;
    }

    @Override
    public void resetStatistics() {
        cacheHits.set(0);
        cacheMisses.set(0);
        intentRouterHandled.set(0);
        compressionCount.set(0);
        totalAiCalls.set(0);
        totalCostSaved.set(0);

        // 同时重置意图路由器的统计
        hybridIntentRouter.resetStatistics();

        log.info("优化层统计信息已重置");
    }

    /**
     * 检查语义缓存是否启用（运行时配置 && 应用配置）
     */
    private boolean isSemanticCacheEnabled() {
        return semanticCacheEnabled && optimProperties.getSemanticCache().isEnabled();
    }

    /**
     * 检查意图路由是否启用（运行时配置 && 应用配置）
     */
    private boolean isIntentRouterEnabled() {
        return intentRouterEnabled && optimProperties.getIntentRouter().getRule().isEnabled();
    }

    /**
     * 检查对话压缩是否启用（运行时配置 && 应用配置）
     */
    private boolean isCompressionEnabled() {
        return compressionEnabled && optimProperties.getCompression().isEnabled();
    }

    /**
     * 检查成本监控是否启用（运行时配置 && 应用配置）
     */
    private boolean isCostMonitorEnabled() {
        return costMonitorEnabled && optimProperties.getCostMonitor().isEnabled();
    }

    /**
     * 构建缓存命中的响应
     */
    private SendMessageResponse buildCachedResponse(String cachedContent) {
        return SendMessageResponse.builder()
                .messageId("cache_" + System.currentTimeMillis())
                .content(cachedContent)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * 构建意图路由处理的响应
     */
    private SendMessageResponse buildRoutedResponse(RouteResult routeResult) {
        return SendMessageResponse.builder()
                .messageId("route_" + System.currentTimeMillis())
                .content(routeResult.getResponse())
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * 记录意图路由节省的成本
     */
    private void recordIntentRouterCostSaving(Long userId, Long conversationId, RouteResult routeResult) {
        try {
            // 估算节省的token和成本
            // 意图路由通常节省一次完整的AI调用
            int estimatedInputTokens = 500;
            int estimatedOutputTokens = 200;
            BigDecimal estimatedCost = new BigDecimal("0.015"); // 估算每次调用成本约0.015元

            String routerLayer = routeResult.getRouterLayer();
            String callType = "intent_router_" + routerLayer;

            costMonitorService.recordAiCall(
                    userId,
                    conversationId,
                    0, // 实际输入token为0（未调用AI）
                    0, // 实际输出token为0（未调用AI）
                    BigDecimal.ZERO, // 实际成本为0
                    "kimi",
                    callType,
                    routerLayer
            );

            // 累计节省成本
            totalCostSaved.addAndGet(estimatedCost.multiply(new BigDecimal("1000")).longValue());

        } catch (Exception e) {
            log.warn("记录意图路由成本失败: {}", e.getMessage());
        }
    }

    /**
     * 记录AI调用成本
     */
    private void recordAiCall(Long userId, Long conversationId, SendMessageResponse response) {
        try {
            // 估算token数量（实际项目中可以通过tokenizer精确计算）
            String content = response.getContent();
            int estimatedOutputTokens = content != null ? content.length() / 2 : 0;
            int estimatedInputTokens = estimatedOutputTokens * 2; // 假设输入是输出的2倍

            // 估算成本（Kimi API 约 0.006元/1K tokens）
            BigDecimal estimatedCost = new BigDecimal(estimatedInputTokens + estimatedOutputTokens)
                    .multiply(new BigDecimal("0.000006"));

            costMonitorService.recordAiCall(
                    userId,
                    conversationId,
                    estimatedInputTokens,
                    estimatedOutputTokens,
                    estimatedCost,
                    "kimi",
                    "chat",
                    "full_ai"
            );

        } catch (Exception e) {
            log.warn("记录AI调用成本失败: {}", e.getMessage());
        }
    }
}
