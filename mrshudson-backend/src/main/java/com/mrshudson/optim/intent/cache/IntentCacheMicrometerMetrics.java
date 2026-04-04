package com.mrshudson.optim.intent.cache;

import com.mrshudson.optim.intent.cache.dto.CacheStatistics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Intent Cache Micrometer 指标集成
 * 将缓存统计信息暴露给 Micrometer，支持 Prometheus 等监控系统
 *
 * <p>指标命名规范：
 * - intent_cache_hits_total (Counter) - 各层命中总数
 * - intent_cache_misses_total (Counter) - 各层未命中总数
 * - intent_cache_hit_rate (Gauge) - 命中率 (0-1)
 * - intent_cache_latency_seconds (Timer) - 查询延迟分布
 * - intent_cache_size (Gauge) - 缓存条目数
 * - intent_cache_memory_bytes (Gauge) - 内存使用估算
 * - intent_cache_circuit_breaker_state (Gauge) - 熔断器状态
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentCacheMicrometerMetrics {

    private final MeterRegistry meterRegistry;
    private final IntentCacheMetrics cacheMetrics;
    private final IntentCacheStore cacheStore;
    private final IntentCacheProperties properties;

    // 用于 Gauge 的实时值持有者
    private final AtomicLong l1Size = new AtomicLong(0);
    private final AtomicLong memoryBytes = new AtomicLong(0);

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("Intent Cache Micrometer metrics disabled");
            return;
        }

        registerMetrics();
        log.info("Intent Cache Micrometer metrics registered");
    }

    /**
     * 记录缓存命中（供外部调用）
     */
    public void recordHit(String tier) {
        Counter.builder("intent_cache_hits_total")
                .tag("tier", tier)
                .description("Intent cache hits by tier")
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录缓存未命中（供外部调用）
     */
    public void recordMiss(String tier) {
        Counter.builder("intent_cache_misses_total")
                .tag("tier", tier)
                .description("Intent cache misses by tier")
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录查询延迟（供外部调用）
     */
    public void recordLatency(long durationNanos) {
        Timer.builder("intent_cache_latency_seconds")
                .description("Intent cache lookup latency")
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * 注册 Gauges（动态值）
     */
    private void registerMetrics() {
        // 命中率 - L1
        Gauge.builder("intent_cache_hit_rate", this, m -> {
                    CacheStatistics stats = cacheStore.getStatistics();
                    return stats.getL1HitRate();
                })
                .tag("tier", "l1")
                .description("L1 cache hit rate")
                .register(meterRegistry);

        // 命中率 - L2
        Gauge.builder("intent_cache_hit_rate", this, m -> {
                    CacheStatistics stats = cacheStore.getStatistics();
                    return stats.getL2HitRate();
                })
                .tag("tier", "l2")
                .description("L2 cache hit rate")
                .register(meterRegistry);

        // 命中率 - L3
        Gauge.builder("intent_cache_hit_rate", this, m -> {
                    CacheStatistics stats = cacheStore.getStatistics();
                    return stats.getL3HitRate();
                })
                .tag("tier", "l3")
                .description("L3 cache hit rate")
                .register(meterRegistry);

        // 总命中率
        Gauge.builder("intent_cache_hit_rate", this, m -> {
                    CacheStatistics stats = cacheStore.getStatistics();
                    return stats.getOverallHitRate();
                })
                .tag("tier", "overall")
                .description("Overall cache hit rate")
                .register(meterRegistry);

        // L1 缓存大小
        Gauge.builder("intent_cache_size", this, m -> {
                    CacheStatistics stats = cacheStore.getStatistics();
                    return stats.getL1Size();
                })
                .tag("tier", "l1")
                .description("L1 cache size (number of entries)")
                .register(meterRegistry);

        // 内存使用估算
        Gauge.builder("intent_cache_memory_bytes", this, m -> {
                    CacheStatistics stats = cacheStore.getStatistics();
                    return stats.getMemoryUsageBytes();
                })
                .tag("tier", "l1")
                .description("L1 cache memory usage estimate")
                .register(meterRegistry);

        // 熔断器状态 (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
        Gauge.builder("intent_cache_circuit_breaker_state", this, m -> {
                    CacheCircuitBreaker.State state = cacheStore.getCircuitBreakerState();
                    return switch (state) {
                        case CLOSED -> 0;
                        case OPEN -> 1;
                        case HALF_OPEN -> 2;
                    };
                })
                .description("Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)")
                .register(meterRegistry);

        // 缓存启用状态
        Gauge.builder("intent_cache_enabled", this, m -> properties.isEnabled() ? 1 : 0)
                .description("Intent cache enabled (1=yes, 0=no)")
                .register(meterRegistry);

        // 各层启用状态
        Gauge.builder("intent_cache_tier_enabled", this, m -> properties.getL1().isEnabled() ? 1 : 0)
                .tag("tier", "l1")
                .description("Tier enabled (1=yes, 0=no)")
                .register(meterRegistry);

        Gauge.builder("intent_cache_tier_enabled", this, m -> properties.getL2().isEnabled() ? 1 : 0)
                .tag("tier", "l2")
                .description("Tier enabled (1=yes, 0=no)")
                .register(meterRegistry);

        Gauge.builder("intent_cache_tier_enabled", this, m -> properties.getL3().isEnabled() ? 1 : 0)
                .tag("tier", "l3")
                .description("Tier enabled (1=yes, 0=no)")
                .register(meterRegistry);
    }
}
