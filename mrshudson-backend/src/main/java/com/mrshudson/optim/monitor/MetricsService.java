package com.mrshudson.optim.monitor;

import com.mrshudson.optim.cache.ToolCacheManager;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 指标收集服务
 * 收集和统计优化层的实际运行数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final CostMonitorService costMonitorService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ToolCacheManager toolCacheManager;

    // 意图路由分层使用统计
    private final Map<String, AtomicLong> intentLayerStats = new ConcurrentHashMap<>();

    // 语义缓存统计
    private final AtomicLong semanticCacheHits = new AtomicLong(0);
    private final AtomicLong semanticCacheMisses = new AtomicLong(0);

    // 工具缓存统计
    private final Map<String, AtomicLong> toolCacheHits = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> toolCacheMisses = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 初始化意图路由分层统计
        intentLayerStats.put("rule-layer", new AtomicLong(0));
        intentLayerStats.put("lightweight-ai-layer", new AtomicLong(0));
        intentLayerStats.put("full-ai-layer", new AtomicLong(0));
    }

    /**
     * 记录意图路由层使用情况
     */
    public void recordIntentLayerUsage(String layer) {
        AtomicLong counter = intentLayerStats.get(layer);
        if (counter != null) {
            counter.incrementAndGet();
        }
    }

    /**
     * 记录语义缓存命中
     */
    public void recordSemanticCacheHit() {
        semanticCacheHits.incrementAndGet();
    }

    /**
     * 记录语义缓存未命中
     */
    public void recordSemanticCacheMiss() {
        semanticCacheMisses.incrementAndGet();
    }

    /**
     * 记录工具缓存命中
     */
    public void recordToolCacheHit(String toolName) {
        toolCacheHits.computeIfAbsent(toolName, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * 记录工具缓存未命中
     */
    public void recordToolCacheMiss(String toolName) {
        toolCacheMisses.computeIfAbsent(toolName, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * 获取当前指标快照
     */
    public MetricsSnapshot getCurrentMetrics() {
        MetricsSnapshot snapshot = new MetricsSnapshot();
        snapshot.setTimestamp(LocalDateTime.now());

        // 意图路由分层统计
        Map<String, Long> intentStats = new HashMap<>();
        intentLayerStats.forEach((k, v) -> intentStats.put(k, v.get()));
        snapshot.setIntentLayerStats(intentStats);

        // 计算意图路由各层占比
        long totalIntentCalls = intentStats.values().stream().mapToLong(Long::longValue).sum();
        if (totalIntentCalls > 0) {
            Map<String, Double> intentLayerPercentages = new HashMap<>();
            intentStats.forEach((k, v) -> {
                intentLayerPercentages.put(k, (v * 100.0) / totalIntentCalls);
            });
            snapshot.setIntentLayerPercentages(intentLayerPercentages);
        }

        // 语义缓存统计
        long hits = semanticCacheHits.get();
        long misses = semanticCacheMisses.get();
        long total = hits + misses;
        snapshot.setSemanticCacheHits(hits);
        snapshot.setSemanticCacheMisses(misses);
        snapshot.setSemanticCacheHitRate(total > 0 ? (hits * 100.0) / total : 0);

        // 工具缓存统计
        long toolHits = toolCacheHits.values().stream().mapToLong(AtomicLong::get).sum();
        long toolMisses = toolCacheMisses.values().stream().mapToLong(AtomicLong::get).sum();
        long toolTotal = toolHits + toolMisses;
        snapshot.setToolCacheHits(toolHits);
        snapshot.setToolCacheMisses(toolMisses);
        snapshot.setToolCacheHitRate(toolTotal > 0 ? (toolHits * 100.0) / toolTotal : 0);

        // Redis中的缓存条目数（估算）
        try {
            long semanticCacheKeys = redisTemplate.keys("semantic_cache:*").size();
            snapshot.setSemanticCacheEntries(semanticCacheKeys);
        } catch (Exception e) {
            log.warn("Failed to count semantic cache keys: {}", e.getMessage());
            snapshot.setSemanticCacheEntries(0L);
        }

        return snapshot;
    }

    /**
     * 获取日统计
     */
    public DailyMetrics getDailyMetrics(LocalDate date) {
        // 这里可以从数据库查询历史数据
        // 目前返回模拟数据，实际应该查询 AiCostRecord 表
        DailyMetrics metrics = new DailyMetrics();
        metrics.setDate(date);
        metrics.setAiCallCount(1000L); // 应该从数据库查询
        metrics.setCacheHitCount(750L);
        metrics.setTotalCost(300.0);
        metrics.setSavedCost(2700.0);
        return metrics;
    }

    /**
     * 获取趋势数据（最近N天）
     */
    public List<DailyMetrics> getTrend(int days) {
        List<DailyMetrics> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            trend.add(getDailyMetrics(date));
        }

        return trend;
    }

    /**
     * 重置统计（用于测试或月初清零）
     */
    public void resetStats() {
        intentLayerStats.values().forEach(counter -> counter.set(0));
        semanticCacheHits.set(0);
        semanticCacheMisses.set(0);
        toolCacheHits.clear();
        toolCacheMisses.clear();
        log.info("Metrics stats reset");
    }

    /**
     * 指标快照
     */
    public static class MetricsSnapshot {
        private LocalDateTime timestamp;
        private Map<String, Long> intentLayerStats;
        private Map<String, Double> intentLayerPercentages;
        private long semanticCacheHits;
        private long semanticCacheMisses;
        private double semanticCacheHitRate;
        private long toolCacheHits;
        private long toolCacheMisses;
        private double toolCacheHitRate;
        private long semanticCacheEntries;

        // Getters and Setters
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        public Map<String, Long> getIntentLayerStats() { return intentLayerStats; }
        public void setIntentLayerStats(Map<String, Long> intentLayerStats) { this.intentLayerStats = intentLayerStats; }
        public Map<String, Double> getIntentLayerPercentages() { return intentLayerPercentages; }
        public void setIntentLayerPercentages(Map<String, Double> intentLayerPercentages) { this.intentLayerPercentages = intentLayerPercentages; }
        public long getSemanticCacheHits() { return semanticCacheHits; }
        public void setSemanticCacheHits(long semanticCacheHits) { this.semanticCacheHits = semanticCacheHits; }
        public long getSemanticCacheMisses() { return semanticCacheMisses; }
        public void setSemanticCacheMisses(long semanticCacheMisses) { this.semanticCacheMisses = semanticCacheMisses; }
        public double getSemanticCacheHitRate() { return semanticCacheHitRate; }
        public void setSemanticCacheHitRate(double semanticCacheHitRate) { this.semanticCacheHitRate = semanticCacheHitRate; }
        public long getToolCacheHits() { return toolCacheHits; }
        public void setToolCacheHits(long toolCacheHits) { this.toolCacheHits = toolCacheHits; }
        public long getToolCacheMisses() { return toolCacheMisses; }
        public void setToolCacheMisses(long toolCacheMisses) { this.toolCacheMisses = toolCacheMisses; }
        public double getToolCacheHitRate() { return toolCacheHitRate; }
        public void setToolCacheHitRate(double toolCacheHitRate) { this.toolCacheHitRate = toolCacheHitRate; }
        public long getSemanticCacheEntries() { return semanticCacheEntries; }
        public void setSemanticCacheEntries(long semanticCacheEntries) { this.semanticCacheEntries = semanticCacheEntries; }
    }

    /**
     * 日统计
     */
    public static class DailyMetrics {
        private LocalDate date;
        private long aiCallCount;
        private long cacheHitCount;
        private double totalCost;
        private double savedCost;

        // Getters and Setters
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
        public long getAiCallCount() { return aiCallCount; }
        public void setAiCallCount(long aiCallCount) { this.aiCallCount = aiCallCount; }
        public long getCacheHitCount() { return cacheHitCount; }
        public void setCacheHitCount(long cacheHitCount) { this.cacheHitCount = cacheHitCount; }
        public double getTotalCost() { return totalCost; }
        public void setTotalCost(double totalCost) { this.totalCost = totalCost; }
        public double getSavedCost() { return savedCost; }
        public void setSavedCost(double savedCost) { this.savedCost = savedCost; }
    }
}
