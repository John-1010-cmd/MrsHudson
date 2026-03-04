package com.mrshudson.optim.monitor.impl;

import com.mrshudson.domain.entity.AiCostRecord;
import com.mrshudson.mapper.AiCostRecordMapper;
import com.mrshudson.optim.config.OptimProperties;
import com.mrshudson.optim.monitor.CostMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 成本监控服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostMonitorServiceImpl implements CostMonitorService {

    private final AiCostRecordMapper aiCostRecordMapper;
    private final OptimProperties optimProperties;

    /**
     * 默认token成本（元/1000 tokens）
     */
    private static final BigDecimal DEFAULT_TOKEN_COST = new BigDecimal("0.003");

    @Override
    @Async
    public void recordAiCall(Long userId, Long conversationId,
                             Integer inputTokens, Integer outputTokens,
                             BigDecimal cost, String model,
                             String callType, String routerLayer) {
        if (!optimProperties.getCostMonitor().isEnabled()) {
            return;
        }

        try {
            AiCostRecord record = new AiCostRecord();
            record.setUserId(userId);
            record.setConversationId(conversationId);
            record.setInputTokens(inputTokens != null ? inputTokens : 0);
            record.setOutputTokens(outputTokens != null ? outputTokens : 0);
            record.setCost(cost != null ? cost : calculateCost(inputTokens, outputTokens));
            record.setModel(model);
            record.setCallType(callType);
            record.setRouterLayer(routerLayer);
            record.setCacheHit(0);

            aiCostRecordMapper.insert(record);

            if (optimProperties.getCostMonitor().isDetailedLog()) {
                log.info("[CostMonitor] 记录AI调用 - userId: {}, callType: {}, cost: {}, model: {}",
                        userId, callType, record.getCost(), model);
            }

            // 异步检查告警
            checkAndAlert(userId);
        } catch (Exception e) {
            log.error("[CostMonitor] 记录AI调用失败", e);
        }
    }

    @Override
    @Async
    public void recordCacheHit(Long userId, Long conversationId,
                               String model, String callType) {
        if (!optimProperties.getCostMonitor().isEnabled()) {
            return;
        }

        try {
            AiCostRecord record = new AiCostRecord();
            record.setUserId(userId);
            record.setConversationId(conversationId);
            record.setInputTokens(0);
            record.setOutputTokens(0);
            record.setCost(BigDecimal.ZERO);
            record.setModel(model);
            record.setCallType(callType);
            record.setCacheHit(1);

            aiCostRecordMapper.insert(record);

            if (optimProperties.getCostMonitor().isDetailedLog()) {
                log.info("[CostMonitor] 记录缓存命中 - userId: {}, callType: {}",
                        userId, callType);
            }
        } catch (Exception e) {
            log.error("[CostMonitor] 记录缓存命中失败", e);
        }
    }

    @Override
    public Map<String, Object> getStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new HashMap<>();

        // 总成本
        BigDecimal totalCost = aiCostRecordMapper.sumCostByTimeRange(startTime, endTime);
        stats.put("totalCost", totalCost != null ? totalCost : BigDecimal.ZERO);

        // 按调用类型统计
        List<Map<String, Object>> callTypeStats = aiCostRecordMapper.selectStatsByCallType(startTime, endTime);
        stats.put("callTypeStats", callTypeStats);

        // 按路由层统计
        List<Map<String, Object>> routerLayerStats = aiCostRecordMapper.selectStatsByRouterLayer(startTime, endTime);
        stats.put("routerLayerStats", routerLayerStats);

        // 缓存命中率
        Map<String, Object> cacheStats = aiCostRecordMapper.selectCacheHitStats(startTime, endTime);
        stats.put("cacheStats", cacheStats);

        // 计算命中率百分比
        if (cacheStats != null) {
            Long hitCount = cacheStats.get("hit_count") != null ?
                    ((Number) cacheStats.get("hit_count")).longValue() : 0L;
            Long totalCount = cacheStats.get("total_count") != null ?
                    ((Number) cacheStats.get("total_count")).longValue() : 0L;

            double hitRate = totalCount > 0 ?
                    (double) hitCount / totalCount * 100 : 0.0;
            stats.put("cacheHitRate", BigDecimal.valueOf(hitRate)
                    .setScale(2, RoundingMode.HALF_UP));
        }

        return stats;
    }

    @Override
    public Map<String, Object> getUserStatistics(Long userId,
                                                 LocalDateTime startTime,
                                                 LocalDateTime endTime) {
        Map<String, Object> stats = new HashMap<>();

        // 用户总成本
        BigDecimal totalCost = aiCostRecordMapper.sumCostByUserAndTimeRange(userId, startTime, endTime);
        stats.put("totalCost", totalCost != null ? totalCost : BigDecimal.ZERO);

        // 用户缓存命中率
        Map<String, Object> allStats = getStatistics(startTime, endTime);
        stats.put("cacheStats", allStats.get("cacheStats"));
        stats.put("cacheHitRate", allStats.get("cacheHitRate"));

        return stats;
    }

    @Override
    public void checkAndAlert(Long userId) {
        if (!optimProperties.getCostMonitor().isEnabled()) {
            return;
        }

        try {
            BigDecimal todayCost = getTodayCost(userId);
            double threshold = optimProperties.getCostMonitor().getDailyCostAlertThreshold();

            if (todayCost.compareTo(BigDecimal.valueOf(threshold)) > 0) {
                if (userId != null) {
                    log.warn("[CostMonitor] 告警: 用户 {} 今日成本 {} 元，超过阈值 {} 元",
                            userId, todayCost, threshold);
                } else {
                    log.warn("[CostMonitor] 告警: 系统今日总成本 {} 元，超过阈值 {} 元",
                            todayCost, threshold);
                }

                // TODO: 可以扩展为发送邮件、短信或Webhook通知
            }
        } catch (Exception e) {
            log.error("[CostMonitor] 检查告警失败", e);
        }
    }

    @Override
    public BigDecimal getTodayCost(Long userId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);

        BigDecimal cost;
        if (userId != null) {
            cost = aiCostRecordMapper.sumCostByUserAndTimeRange(userId, startOfDay, endOfDay);
        } else {
            cost = aiCostRecordMapper.sumCostByTimeRange(startOfDay, endOfDay);
        }

        return cost != null ? cost : BigDecimal.ZERO;
    }

    /**
     * 根据token数计算成本
     */
    private BigDecimal calculateCost(Integer inputTokens, Integer outputTokens) {
        int totalTokens = (inputTokens != null ? inputTokens : 0) +
                (outputTokens != null ? outputTokens : 0);
        return DEFAULT_TOKEN_COST
                .multiply(BigDecimal.valueOf(totalTokens))
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
    }
}
