package com.mrshudson.optim.fallback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 兜底统计指标
 * 记录兜底触发的次数和占比
 */
@Slf4j
@Component
public class FallbackMetrics {

    /**
     * 兜底触发次数
     */
    private final AtomicLong fallbackCount = new AtomicLong(0);

    /**
     * 总请求次数
     */
    private final AtomicLong totalCount = new AtomicLong(0);

    /**
     * 记录兜底触发
     */
    public void recordFallback() {
        fallbackCount.incrementAndGet();
        log.debug("兜底触发，当前次数: {}", fallbackCount.get());
    }

    /**
     * 记录总请求
     */
    public void recordTotal() {
        totalCount.incrementAndGet();
    }

    /**
     * 记录请求和兜底
     */
    public void recordRequest(boolean fallback) {
        totalCount.incrementAndGet();
        if (fallback) {
            fallbackCount.incrementAndGet();
        }
    }

    /**
     * 获取兜底触发次数
     *
     * @return 兜底次数
     */
    public long getFallbackCount() {
        return fallbackCount.get();
    }

    /**
     * 获取总请求次数
     *
     * @return 总请求次数
     */
    public long getTotalCount() {
        return totalCount.get();
    }

    /**
     * 获取兜底占比
     *
     * @return 兜底占比（0.0 - 1.0）
     */
    public double getFallbackRate() {
        long total = totalCount.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) fallbackCount.get() / total;
    }

    /**
     * 获取兜底占比百分比
     *
     * @return 兜底占比百分比（0.0 - 100.0）
     */
    public double getFallbackRatePercent() {
        return getFallbackRate() * 100;
    }

    /**
     * 重置统计数据
     */
    public void reset() {
        fallbackCount.set(0);
        totalCount.set(0);
        log.info("兜底统计已重置");
    }
}
