package com.mrshudson.optim.intent.cache;

import com.mrshudson.optim.intent.cache.dto.CacheStatistics;
import com.mrshudson.optim.intent.cache.dto.IntentCacheEntry;
import com.mrshudson.optim.intent.cache.dto.IntentFingerprint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IntentCacheStore (Facade) 单元测试
 * 覆盖：L1→L2 级联查询、回填、统计、禁用、熔断降级
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IntentCacheStoreTest {

    @Mock private IntentCacheProperties properties;
    @Mock private QueryNormalizer queryNormalizer;
    @Mock private L1CacheStore l1Cache;
    @Mock private L2CacheStore l2Cache;
    @Mock private L3CacheStore l3Cache;
    @Mock private CacheCircuitBreaker circuitBreaker;
    @Mock private IntentCacheMetrics cacheMetrics;

    private IntentCacheStore cacheStore;

    private static final String RAW_INPUT = "今天天气怎么样";
    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        when(properties.isEnabled()).thenReturn(true);
        when(l1Cache.isEnabled()).thenReturn(true);
        when(l2Cache.isEnabled()).thenReturn(true);
        when(l3Cache.isEnabled()).thenReturn(false); // L3 默认禁用
        cacheStore = new IntentCacheStore(properties, queryNormalizer, l1Cache, l2Cache, l3Cache, circuitBreaker, cacheMetrics);
    }

    private IntentFingerprint createFingerprint() {
        return IntentFingerprint.builder()
                .rawInput(RAW_INPUT)
                .normalizedInput("2026-04-04天气怎么样")
                .fingerprintHash("abc123")
                .build();
    }

    private IntentCacheEntry createEntry() {
        return IntentCacheEntry.builder()
                .intentType("WEATHER_QUERY")
                .confidence(0.95)
                .createdAt(LocalDateTime.now())
                .ttlSeconds(300)
                .accessCount(0)
                .build();
    }

    /** 模拟 circuitBreaker 直接执行操作（不拦截） */
    @SuppressWarnings("unchecked")
    private void stubCircuitBreakerPassThrough() {
        when(circuitBreaker.execute(any(Supplier.class), any(Supplier.class))).thenAnswer(inv -> {
            Supplier<Optional<IntentCacheEntry>> op = inv.getArgument(0);
            return op.get();
        });
    }

    /** 模拟 circuitBreaker 触发 fallback */
    @SuppressWarnings("unchecked")
    private void stubCircuitBreakerFallback() {
        when(circuitBreaker.execute(any(Supplier.class), any(Supplier.class))).thenAnswer(inv -> {
            Supplier<Optional<IntentCacheEntry>> fallback = inv.getArgument(1);
            return fallback.get();
        });
    }

    /** 模拟 circuitBreaker 执行（返回 null 的 Void supplier） */
    @SuppressWarnings("unchecked")
    private void stubCircuitBreakerVoid() {
        when(circuitBreaker.execute(any(Supplier.class), any(Supplier.class))).thenAnswer(inv -> {
            Supplier<Object> op = inv.getArgument(0);
            return op.get();
        });
    }

    // ========== 级联查询 ==========

    @Nested
    @DisplayName("L1 → L2 级联查询")
    class CascadeLookup {

        @Test
        @DisplayName("L1 命中直接返回")
        void l1Hit() {
            when(queryNormalizer.normalize(RAW_INPUT)).thenReturn(createFingerprint());
            when(l1Cache.get(anyString())).thenReturn(Optional.of(createEntry()));
            stubCircuitBreakerPassThrough();

            Optional<IntentCacheEntry> result = cacheStore.lookup(RAW_INPUT, USER_ID);

            assertTrue(result.isPresent());
            assertEquals("WEATHER_QUERY", result.get().getIntentType());
            verify(l2Cache, never()).get(anyLong(), anyString());
            verify(cacheMetrics).recordL1Hit(anyLong());
        }

        @Test
        @DisplayName("L1 miss → L2 命中 → 回填 L1")
        void l1MissL2Hit() {
            IntentCacheEntry entry = createEntry();
            when(queryNormalizer.normalize(RAW_INPUT)).thenReturn(createFingerprint());
            when(l1Cache.get(anyString())).thenReturn(Optional.empty());
            when(l2Cache.get(eq(USER_ID), eq("abc123"))).thenReturn(Optional.of(entry));
            stubCircuitBreakerPassThrough();

            Optional<IntentCacheEntry> result = cacheStore.lookup(RAW_INPUT, USER_ID);

            assertTrue(result.isPresent());
            verify(l1Cache).put(anyString(), eq(entry));
            verify(cacheMetrics).recordL2Hit(anyLong());
        }

        @Test
        @DisplayName("L1 + L2 全 miss 返回 empty")
        void fullMiss() {
            when(queryNormalizer.normalize(RAW_INPUT)).thenReturn(createFingerprint());
            when(l1Cache.get(anyString())).thenReturn(Optional.empty());
            when(l2Cache.get(anyLong(), anyString())).thenReturn(Optional.empty());
            stubCircuitBreakerPassThrough();

            assertTrue(cacheStore.lookup(RAW_INPUT, USER_ID).isEmpty());
            verify(cacheMetrics).recordL1Miss();
            verify(cacheMetrics).recordL2Miss();
        }
    }

    // ========== 存储 ==========

    @Nested
    @DisplayName("缓存存储")
    class Store {

        @Test
        @DisplayName("store 同时写 L1 和 L2")
        void storeToBothTiers() {
            IntentCacheEntry entry = createEntry();
            when(queryNormalizer.normalize(RAW_INPUT)).thenReturn(createFingerprint());
            stubCircuitBreakerVoid();

            cacheStore.store(RAW_INPUT, USER_ID, entry);

            verify(l1Cache).put(anyString(), eq(entry));
            verify(l2Cache).put(eq(USER_ID), eq("abc123"), eq(entry));
        }

        @Test
        @DisplayName("缓存禁用时不存储")
        void disabledNoStore() {
            when(properties.isEnabled()).thenReturn(false);
            cacheStore.store(RAW_INPUT, USER_ID, createEntry());
            verify(l1Cache, never()).put(anyString(), any());
            verify(l2Cache, never()).put(anyLong(), anyString(), any());
        }
    }

    // ========== 熔断降级 ==========

    @Nested
    @DisplayName("熔断降级")
    class CircuitBreakerFallback {

        @Test
        @DisplayName("熔断触发 fallback 返回 empty")
        void circuitOpenReturnsEmpty() {
            when(queryNormalizer.normalize(RAW_INPUT)).thenReturn(createFingerprint());
            stubCircuitBreakerFallback();

            assertTrue(cacheStore.lookup(RAW_INPUT, USER_ID).isEmpty());
        }
    }

    // ========== 边界条件 ==========

    @Nested
    @DisplayName("边界条件")
    class EdgeCases {

        @Test
        @DisplayName("null 输入返回 empty")
        void nullInput() {
            when(queryNormalizer.normalize(null)).thenReturn(null);
            assertTrue(cacheStore.lookup(null, USER_ID).isEmpty());
        }

        @Test
        @DisplayName("null userId 正常处理")
        void nullUserId() {
            when(queryNormalizer.normalize("test")).thenReturn(null);
            assertTrue(cacheStore.lookup("test", null).isEmpty());
        }

        @Test
        @DisplayName("缓存全局禁用时 lookup 返回 empty")
        void globalDisabled() {
            when(properties.isEnabled()).thenReturn(false);
            assertTrue(cacheStore.lookup(RAW_INPUT, USER_ID).isEmpty());
        }
    }

    // ========== 统计 ==========

    @Nested
    @DisplayName("统计信息")
    class Statistics {

        @Test
        @DisplayName("getStatistics 委托给 CacheMetrics")
        void statsDelegation() {
            CacheStatistics mockStats = CacheStatistics.builder()
                    .l1Hits(10)
                    .l1Misses(5)
                    .totalHits(15)
                    .totalMisses(5)
                    .build();
            when(cacheMetrics.getStatistics(anyLong())).thenReturn(mockStats);
            when(l1Cache.getSize()).thenReturn(10L);

            CacheStatistics stats = cacheStore.getStatistics();
            assertNotNull(stats);
            assertEquals(10, stats.getL1Hits());
            assertEquals(15, stats.getTotalHits());
        }

        @Test
        @DisplayName("resetStatistics 委托给 CacheMetrics")
        void resetStats() {
            cacheStore.resetStatistics();
            verify(cacheMetrics).reset();
        }
    }

    // ========== invalidate ==========

    @Nested
    @DisplayName("缓存失效")
    class Invalidation {

        @Test
        @DisplayName("invalidate 调用 L1 + L2 + L3")
        void invalidate() {
            cacheStore.invalidate(USER_ID);
            verify(l1Cache).invalidateByUserId(USER_ID);
            verify(l2Cache).invalidateByUserId(USER_ID);
            verify(l3Cache).invalidateByUserId(USER_ID);
        }
    }
}
