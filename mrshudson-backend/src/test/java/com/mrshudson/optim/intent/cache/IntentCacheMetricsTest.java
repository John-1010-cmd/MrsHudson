package com.mrshudson.optim.intent.cache;

import com.mrshudson.optim.intent.cache.dto.CacheStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IntentCacheMetrics 单元测试
 * 覆盖：计数器准确性、百分位计算、并发安全、重置
 */
class IntentCacheMetricsTest {

    private IntentCacheMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new IntentCacheMetrics();
    }

    // ========== 分层计数 ==========

    @Nested
    @DisplayName("分层计数")
    class TierCounting {

        @Test
        @DisplayName("L1 hit/miss 计数正确")
        void l1Counting() {
            metrics.recordL1Hit(1_000_000L); // 1ms
            metrics.recordL1Hit(2_000_000L); // 2ms
            metrics.recordL1Miss();

            CacheStatistics stats = metrics.getStatistics(10);
            assertEquals(2, stats.getL1Hits());
            assertEquals(1, stats.getL1Misses());
            assertTrue(stats.getL1HitRate() > 0.6);
        }

        @Test
        @DisplayName("L2 hit/miss 计数正确")
        void l2Counting() {
            metrics.recordL2Hit(5_000_000L); // 5ms
            metrics.recordL2Miss();
            metrics.recordL2Miss();

            CacheStatistics stats = metrics.getStatistics(5);
            assertEquals(1, stats.getL2Hits());
            assertEquals(2, stats.getL2Misses());
        }

        @Test
        @DisplayName("L3 hit/miss 计数正确")
        void l3Counting() {
            metrics.recordL3Hit(10_000_000L);
            metrics.recordL3Miss();

            CacheStatistics stats = metrics.getStatistics(0);
            assertEquals(1, stats.getL3Hits());
            assertEquals(1, stats.getL3Misses());
        }

        @Test
        @DisplayName("totalMiss 计数正确")
        void totalMissCounting() {
            metrics.recordTotalMiss(3_000_000L);
            metrics.recordTotalMiss(4_000_000L);

            CacheStatistics stats = metrics.getStatistics(0);
            assertEquals(2, stats.getTotalMisses());
        }

        @Test
        @DisplayName("初始值全为零")
        void initialValues() {
            CacheStatistics stats = metrics.getStatistics(0);
            assertEquals(0, stats.getL1Hits());
            assertEquals(0, stats.getL1Misses());
            assertEquals(0, stats.getL2Hits());
            assertEquals(0, stats.getTotalHits());
            assertEquals(0.0, stats.getAvgLatencyMs());
            assertEquals(0.0, stats.getP50LatencyMs());
        }
    }

    // ========== 延迟百分位 ==========

    @Nested
    @DisplayName("延迟百分位")
    class LatencyPercentiles {

        @Test
        @DisplayName("单次延迟平均值正确")
        void singleLatency() {
            metrics.recordL1Hit(5_000_000L); // 5ms

            CacheStatistics stats = metrics.getStatistics(0);
            // recordL1Hit 内部调用 recordLatency，totalRequests 递增
            // avgLatency = 5ms / 1 = 5ms (近似)
            assertTrue(stats.getAvgLatencyMs() >= 4.0 && stats.getAvgLatencyMs() <= 6.0,
                    "avgLatency should be around 5ms, was: " + stats.getAvgLatencyMs());
        }

        @Test
        @DisplayName("多次延迟百分位合理")
        void multipleLatencies() {
            // 记录 100 个从 1ms 到 100ms 的延迟
            for (int i = 1; i <= 100; i++) {
                long nanos = i * 1_000_000L;
                metrics.recordL1Hit(nanos);
            }

            CacheStatistics stats = metrics.getStatistics(0);
            // P50 ≈ 50ms, P95 ≈ 95ms, P99 ≈ 99ms
            assertTrue(stats.getP50LatencyMs() >= 45 && stats.getP50LatencyMs() <= 55,
                    "P50 should be around 50ms, was: " + stats.getP50LatencyMs());
            assertTrue(stats.getP95LatencyMs() >= 90,
                    "P95 should be >= 90ms, was: " + stats.getP95LatencyMs());
            assertTrue(stats.getP99LatencyMs() >= 95,
                    "P99 should be >= 95ms, was: " + stats.getP99LatencyMs());
        }

        @Test
        @DisplayName("空采样百分位为零")
        void emptySamples() {
            CacheStatistics stats = metrics.getStatistics(0);
            assertEquals(0.0, stats.getP50LatencyMs());
            assertEquals(0.0, stats.getP95LatencyMs());
            assertEquals(0.0, stats.getP99LatencyMs());
        }
    }

    // ========== 内存估算 ==========

    @Nested
    @DisplayName("内存估算")
    class MemoryEstimation {

        @Test
        @DisplayName("内存估算 = l1Size * 1024")
        void memoryEstimation() {
            CacheStatistics stats = metrics.getStatistics(50);
            assertEquals(50 * 1024L, stats.getMemoryUsageBytes());
            assertEquals(50, stats.getL1Size());
        }

        @Test
        @DisplayName("零条目内存为零")
        void zeroMemory() {
            CacheStatistics stats = metrics.getStatistics(0);
            assertEquals(0, stats.getMemoryUsageBytes());
        }
    }

    // ========== 重置 ==========

    @Nested
    @DisplayName("重置")
    class Reset {

        @Test
        @DisplayName("reset 后所有计数器归零")
        void resetClearsCounters() {
            metrics.recordL1Hit(1_000_000L);
            metrics.recordL2Hit(2_000_000L);
            metrics.recordL1Miss();
            metrics.reset();

            CacheStatistics stats = metrics.getStatistics(0);
            assertEquals(0, stats.getL1Hits());
            assertEquals(0, stats.getL2Hits());
            assertEquals(0, stats.getL1Misses());
            assertEquals(0.0, stats.getAvgLatencyMs());
        }

        @Test
        @DisplayName("reset 后 lastReset 更新")
        void resetUpdatesTimestamp() {
            metrics.recordL1Hit(1_000_000L);
            metrics.reset();
            CacheStatistics after = metrics.getStatistics(0);

            assertNotNull(after.getLastReset());
            assertEquals(0, after.getL1Hits());
        }
    }

    // ========== 并发安全 ==========

    @Nested
    @DisplayName("并发安全")
    class Concurrency {

        @Test
        @DisplayName("多线程并发记录不丢失计数")
        void concurrentRecording() throws InterruptedException {
            int threadCount = 8;
            int opsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                final int tier = t % 3;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < opsPerThread; i++) {
                            switch (tier) {
                                case 0 -> metrics.recordL1Hit(i * 1_000L);
                                case 1 -> metrics.recordL2Hit(i * 1_000L);
                                case 2 -> metrics.recordL3Hit(i * 1_000L);
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            executor.shutdown();

            CacheStatistics stats = metrics.getStatistics(0);
            long totalHits = stats.getL1Hits() + stats.getL2Hits() + stats.getL3Hits();
            assertEquals(threadCount * opsPerThread, totalHits,
                    "所有并发记录应无丢失");
        }
    }

    // ========== percentile 静态方法 ==========

    @Nested
    @DisplayName("percentile 计算工具")
    class PercentileCalculation {

        @Test
        @DisplayName("空数组返回 0")
        void emptyArray() {
            assertEquals(0.0, IntentCacheMetrics.percentile(new long[0], 0, 50));
        }

        @Test
        @DisplayName("单元素数组返回该元素")
        void singleElement() {
            assertEquals(42.0, IntentCacheMetrics.percentile(new long[]{42}, 1, 99));
        }

        @Test
        @DisplayName("已知数组百分位精确")
        void knownArray() {
            long[] sorted = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
            assertEquals(50.0, IntentCacheMetrics.percentile(sorted, 10, 50));
            assertEquals(90.0, IntentCacheMetrics.percentile(sorted, 10, 90));
            assertEquals(100.0, IntentCacheMetrics.percentile(sorted, 10, 100));
        }
    }
}
