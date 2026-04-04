package com.mrshudson.optim.intent.cache;

import com.mrshudson.optim.intent.cache.dto.CacheStatistics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * 缓存指标收集器
 * 分层追踪 hit/miss 计数、延迟百分位、内存使用
 * <p>
 * 设计原则：
 * - 无锁并发（AtomicLong / AtomicLongArray）
 * - 固定大小延迟窗口（O(1) 写入），排序后计算百分位
 * - 零外部依赖，Phase 4 可选接入 Micrometer
 */
@Slf4j
@Component
public class IntentCacheMetrics {

    // ========== 分层计数器 ==========

    private final AtomicLong l1Hits = new AtomicLong();
    private final AtomicLong l1Misses = new AtomicLong();
    private final AtomicLong l2Hits = new AtomicLong();
    private final AtomicLong l2Misses = new AtomicLong();
    private final AtomicLong l3Hits = new AtomicLong();
    private final AtomicLong l3Misses = new AtomicLong();

    /** 全层未命中计数（所有 tier 都 miss） */
    private final AtomicLong totalMissCount = new AtomicLong();

    // ========== 延迟追踪 ==========

    /** 延迟采样窗口大小 */
    private static final int LATENCY_WINDOW_SIZE = 1024;

    /** 延迟采样环形缓冲区 (毫秒) */
    private final AtomicLongArray latencyWindow = new AtomicLongArray(LATENCY_WINDOW_SIZE);

    /** 下一个写入位置 */
    private final AtomicLong latencyIndex = new AtomicLong();

    /** 累计延迟总和 (用于平均) */
    private final AtomicLong totalLatencyNanos = new AtomicLong();

    /** 累计请求数 */
    private final AtomicLong totalRequests = new AtomicLong();

    /** 最近一次重置时间 */
    private volatile LocalDateTime lastReset = LocalDateTime.now();

    // ========== 记录方法 ==========

    /** 记录 L1 命中 */
    public void recordL1Hit(long durationNanos) {
        l1Hits.incrementAndGet();
        recordLatency(durationNanos);
    }

    /** 记录 L1 未命中 */
    public void recordL1Miss() {
        l1Misses.incrementAndGet();
    }

    /** 记录 L2 命中 */
    public void recordL2Hit(long durationNanos) {
        l2Hits.incrementAndGet();
        recordLatency(durationNanos);
    }

    /** 记录 L2 未命中 */
    public void recordL2Miss() {
        l2Misses.incrementAndGet();
    }

    /** 记录 L3 命中 */
    public void recordL3Hit(long durationNanos) {
        l3Hits.incrementAndGet();
        recordLatency(durationNanos);
    }

    /** 记录 L3 未命中 */
    public void recordL3Miss() {
        l3Misses.incrementAndGet();
    }

    /** 记录总 miss（所有层未命中） */
    public void recordTotalMiss(long durationNanos) {
        totalMissCount.incrementAndGet();
        recordLatency(durationNanos);
    }

    /**
     * 记录延迟采样
     * @param durationNanos 耗时（纳秒）
     */
    private void recordLatency(long durationNanos) {
        totalRequests.incrementAndGet();
        totalLatencyNanos.addAndGet(durationNanos);

        long idx = latencyIndex.getAndIncrement() % LATENCY_WINDOW_SIZE;
        latencyWindow.set((int) idx, Duration.ofNanos(durationNanos).toMillis());
    }

    // ========== 查询方法 ==========

    /** 获取完整统计快照 */
    public CacheStatistics getStatistics(long l1Size) {
        long l1H = l1Hits.get();
        long l1M = l1Misses.get();
        long l2H = l2Hits.get();
        long l2M = l2Misses.get();
        long l3H = l3Hits.get();
        long l3M = l3Misses.get();

        long totalH = l1H + l2H + l3H;
        long totalM = totalMissCount.get();
        long requests = totalRequests.get();

        double avgLatency = requests > 0
                ? (double) Duration.ofNanos(totalLatencyNanos.get()).toMillis() / requests
                : 0.0;

        long[] samples = getSortedLatencySamples();
        int sampleCount = samples.length;

        // 内存估算: 每条目约 1KB (IntentCacheEntry + key + Caffeine overhead)
        long memoryBytes = l1Size * 1024L;

        return CacheStatistics.builder()
                .l1Hits(l1H)
                .l1Misses(l1M)
                .l1HitRate(calculateRate(l1H, l1H + l1M))
                .l2Hits(l2H)
                .l2Misses(l2M)
                .l2HitRate(calculateRate(l2H, l2H + l2M))
                .l3Hits(l3H)
                .l3Misses(l3M)
                .l3HitRate(calculateRate(l3H, l3H + l3M))
                .totalHits(totalH)
                .totalMisses(totalM)
                .avgLatencyMs(avgLatency)
                .p50LatencyMs(percentile(samples, sampleCount, 50))
                .p95LatencyMs(percentile(samples, sampleCount, 95))
                .p99LatencyMs(percentile(samples, sampleCount, 99))
                .memoryUsageBytes(memoryBytes)
                .l1Size(l1Size)
                .lastReset(lastReset)
                .build();
    }

    /** 重置所有计数器 */
    public void reset() {
        l1Hits.set(0);
        l1Misses.set(0);
        l2Hits.set(0);
        l2Misses.set(0);
        l3Hits.set(0);
        l3Misses.set(0);
        totalMissCount.set(0);
        totalLatencyNanos.set(0);
        totalRequests.set(0);
        latencyIndex.set(0);
        lastReset = LocalDateTime.now();
        log.info("CacheMetrics reset");
    }

    // ========== 百分位计算 ==========

    /**
     * 获取排序后的延迟采样（毫秒）
     * 注意：环形缓冲区可能包含初始零值，需过滤
     */
    private long[] getSortedLatencySamples() {
        long currentIdx = latencyIndex.get();
        int validCount = (int) Math.min(currentIdx, LATENCY_WINDOW_SIZE);

        if (validCount == 0) {
            return new long[0];
        }

        // 只取有效采样（最近的 validCount 条）
        long[] samples = new long[validCount];
        for (int i = 0; i < validCount; i++) {
            // 从最新的往回取
            long readIdx = (currentIdx - 1 - i) % LATENCY_WINDOW_SIZE;
            if (readIdx < 0) readIdx += LATENCY_WINDOW_SIZE;
            samples[i] = latencyWindow.get((int) readIdx);
        }

        Arrays.sort(samples);
        return samples;
    }

    /**
     * 计算百分位延迟
     */
    static double percentile(long[] sortedSamples, int count, int percentile) {
        if (count == 0) return 0.0;

        int idx = (int) Math.ceil(count * percentile / 100.0) - 1;
        idx = Math.max(0, Math.min(idx, count - 1));
        return sortedSamples[idx];
    }

    private static double calculateRate(long hits, long total) {
        return total == 0 ? 0.0 : (double) hits / total;
    }
}
