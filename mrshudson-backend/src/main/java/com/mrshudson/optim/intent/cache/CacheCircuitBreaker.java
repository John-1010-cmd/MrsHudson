package com.mrshudson.optim.intent.cache;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 缓存熔断器
 * 使用 Resilience4j 实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheCircuitBreaker {

    private final IntentCacheProperties properties;

    private io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker;

    @PostConstruct
    public void init() {
        IntentCacheProperties.CircuitBreakerProperties cbProps = properties.getCircuitBreaker();

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // 失败率阈值 50%
                .slowCallRateThreshold(50) // 慢调用阈值 50%
                .slowCallDurationThreshold(Duration.ofSeconds(2)) // 慢调用定义：2秒
                .permittedNumberOfCallsInHalfOpenState(cbProps.getSuccessThreshold()) // HALF_OPEN 允许调用数
                .maxWaitDurationInHalfOpenState(cbProps.getWaitDurationInOpenState()) // HALF_OPEN 等待时间
                .slidingWindowSize(cbProps.getSlidingWindowSize()) // 滑动窗口大小
                .minimumNumberOfCalls(5) // 最小调用数
                .waitDurationInOpenState(cbProps.getWaitDurationInOpenState()) // OPEN 状态等待时间
                .recordExceptions(Exception.class) // 记录所有异常
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        this.circuitBreaker = registry.circuitBreaker("intentCache", config);

        log.info("Cache Circuit Breaker initialized: failureThreshold={}, waitDuration={}",
                cbProps.getFailureThreshold(), cbProps.getWaitDurationInOpenState());
    }

    /**
     * 执行带熔断保护的操作
     */
    public <T> T execute(Supplier<T> operation, Supplier<T> fallback) {
        if (!properties.getCircuitBreaker().isEnabled()) {
            return operation.get();
        }

        try {
            return circuitBreaker.executeSupplier(operation);
        } catch (Exception e) {
            log.warn("Circuit breaker fallback triggered: state={}, error={}",
                    getState(), e.getMessage());
            return fallback.get();
        }
    }

    /**
     * 获取当前状态
     */
    public State getState() {
        io.github.resilience4j.circuitbreaker.CircuitBreaker.State state = circuitBreaker.getState();
        return State.valueOf(state.name());
    }

    /**
     * 熔断器状态
     */
    public enum State {
        CLOSED,     // 关闭 - 正常
        OPEN,       // 打开 - 熔断
        HALF_OPEN   // 半开 - 试探
    }

    /**
     * 手动重置熔断器
     */
    public void reset() {
        circuitBreaker.reset();
        log.info("Circuit breaker manually reset");
    }

    /**
     * 获取熔断器指标
     */
    public io.github.resilience4j.circuitbreaker.CircuitBreaker.Metrics getMetrics() {
        return circuitBreaker.getMetrics();
    }
}
