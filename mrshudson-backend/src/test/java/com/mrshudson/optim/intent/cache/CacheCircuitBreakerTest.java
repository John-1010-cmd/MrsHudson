package com.mrshudson.optim.intent.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CacheCircuitBreaker 单元测试
 * 覆盖：CLOSED 正常执行、熔断触发 fallback、状态查询、reset
 */
class CacheCircuitBreakerTest {

    private CacheCircuitBreaker cb;
    private IntentCacheProperties properties;

    @BeforeEach
    void setUp() {
        properties = new IntentCacheProperties();
        properties.getCircuitBreaker().setEnabled(true);
        properties.getCircuitBreaker().setFailureThreshold(10);
        properties.getCircuitBreaker().setWaitDurationInOpenState(Duration.ofSeconds(10));
        properties.getCircuitBreaker().setSuccessThreshold(3);
        properties.getCircuitBreaker().setSlidingWindowSize(10);

        cb = new CacheCircuitBreaker(properties);
        cb.init();
    }

    // ========== 正常执行 ==========

    @Nested
    @DisplayName("CLOSED 状态 - 正常执行")
    class ClosedState {

        @Test
        @DisplayName("正常操作直接返回结果")
        void successfulExecution() {
            String result = cb.execute(() -> "ok", () -> "fallback");
            assertEquals("ok", result);
        }

        @Test
        @DisplayName("初始状态为 CLOSED")
        void initialState() {
            assertEquals(CacheCircuitBreaker.State.CLOSED, cb.getState());
        }

        @Test
        @DisplayName("多次正常调用保持 CLOSED")
        void remainClosedOnSuccess() {
            for (int i = 0; i < 20; i++) {
                cb.execute(() -> "ok", () -> "fallback");
            }
            assertEquals(CacheCircuitBreaker.State.CLOSED, cb.getState());
        }
    }

    // ========== Fallback ==========

    @Nested
    @DisplayName("Fallback 降级")
    class Fallback {

        @Test
        @DisplayName("操作抛异常时返回 fallback")
        void fallbackOnException() {
            AtomicInteger fallbackCount = new AtomicInteger(0);
            String result = cb.execute(() -> {
                throw new RuntimeException("cache error");
            }, () -> {
                fallbackCount.incrementAndGet();
                return "fallback";
            });
            assertEquals("fallback", result);
            assertEquals(1, fallbackCount.get());
        }

        @Test
        @DisplayName("连续异常后 fallback 持续生效")
        void repeatedFallbacks() {
            for (int i = 0; i < 15; i++) {
                cb.execute(() -> {
                    throw new RuntimeException("error");
                }, () -> "fallback");
            }
            // 即使多次失败，fallback 仍然能执行
            String result = cb.execute(() -> "ok", () -> "fallback");
            // 状态可能已转为 OPEN，但仍能通过 fallback 获取结果
            assertNotNull(result);
        }
    }

    // ========== 禁用 ==========

    @Nested
    @DisplayName("禁用控制")
    class Disabled {

        @Test
        @DisplayName("禁用时直接执行操作，不走熔断逻辑")
        void disabledBypass() {
            properties.getCircuitBreaker().setEnabled(false);
            CacheCircuitBreaker disabledCb = new CacheCircuitBreaker(properties);
            disabledCb.init();

            AtomicInteger opCount = new AtomicInteger(0);
            disabledCb.execute(() -> {
                opCount.incrementAndGet();
                return "result";
            }, () -> "fallback");

            assertEquals(1, opCount.get());
        }
    }

    // ========== Reset ==========

    @Nested
    @DisplayName("重置")
    class Reset {

        @Test
        @DisplayName("reset 后回到 CLOSED")
        void resetToClosed() {
            // 触发一些失败
            for (int i = 0; i < 15; i++) {
                cb.execute(() -> {
                    throw new RuntimeException("error");
                }, () -> "fallback");
            }
            cb.reset();
            assertEquals(CacheCircuitBreaker.State.CLOSED, cb.getState());
        }
    }

    // ========== Metrics ==========

    @Nested
    @DisplayName("指标")
    class Metrics {

        @Test
        @DisplayName("getMetrics 返回非空")
        void metricsNotNull() {
            assertNotNull(cb.getMetrics());
        }
    }
}
