package com.mrshudson.optim.intent.cache.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 缓存统计信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheStatistics {

    /** L1 命中次数 */
    private long l1Hits;

    /** L1 未命中次数 */
    private long l1Misses;

    /** L1 命中率 */
    private double l1HitRate;

    /** L2 命中次数 */
    private long l2Hits;

    /** L2 未命中次数 */
    private long l2Misses;

    /** L2 命中率 */
    private double l2HitRate;

    /** L3 命中次数 */
    private long l3Hits;

    /** L3 未命中次数 */
    private long l3Misses;

    /** L3 命中率 */
    private double l3HitRate;

    /** 总命中次数 */
    private long totalHits;

    /** 总未命中次数 */
    private long totalMisses;

    /** 平均延迟 (毫秒) */
    private double avgLatencyMs;

    /** P50 延迟 (毫秒) */
    private double p50LatencyMs;

    /** P95 延迟 (毫秒) */
    private double p95LatencyMs;

    /** P99 延迟 (毫秒) */
    private double p99LatencyMs;

    /** L1 内存使用 (字节估算) */
    private long memoryUsageBytes;

    /** L1 条目数 */
    private long l1Size;

    /** 上次重置时间 */
    private LocalDateTime lastReset;

    /**
     * 获取总命中率
     */
    public double getOverallHitRate() {
        long total = totalHits + totalMisses;
        return total == 0 ? 0.0 : (double) totalHits / total;
    }

    /**
     * 重置统计
     */
    public void reset() {
        this.l1Hits = 0;
        this.l1Misses = 0;
        this.l2Hits = 0;
        this.l2Misses = 0;
        this.l3Hits = 0;
        this.l3Misses = 0;
        this.totalHits = 0;
        this.totalMisses = 0;
        this.avgLatencyMs = 0.0;
        this.p50LatencyMs = 0.0;
        this.p95LatencyMs = 0.0;
        this.p99LatencyMs = 0.0;
        this.lastReset = LocalDateTime.now();
    }
}
