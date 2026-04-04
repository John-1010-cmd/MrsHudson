package com.mrshudson.optim.intent.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Intent Vector Cache 配置属性
 * 对齐 AI_ARCHITECTURE.md v3.5 规范
 */
@Data
@Component
@ConfigurationProperties(prefix = "intent.cache")
public class IntentCacheProperties {

    /**
     * L1 缓存配置 (ConcurrentHashMap)
     */
    private L1CacheProperties l1 = new L1CacheProperties();

    /**
     * L2 缓存配置 (Redis Hash)
     */
    private L2CacheProperties l2 = new L2CacheProperties();

    /**
     * L3 缓存配置 (Redis Vector)
     */
    private L3CacheProperties l3 = new L3CacheProperties();

    /**
     * 熔断器配置
     */
    private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();

    /**
     * 是否启用缓存
     */
    private boolean enabled = true;

    /**
     * L1 缓存配置
     */
    @Data
    public static class L1CacheProperties {
        /**
         * 最大条目数 (默认 500，对齐 AI_ARCHITECTURE.md)
         */
        private int maxSize = 500;

        /**
         * TTL (默认 5 分钟)
         */
        private Duration ttl = Duration.ofMinutes(5);

        /**
         * 是否启用 L1
         */
        private boolean enabled = true;
    }

    /**
     * L2 缓存配置
     */
    @Data
    public static class L2CacheProperties {
        /**
         * Redis key 前缀
         */
        private String keyPrefix = "intent:L2";

        /**
         * TTL (默认 7 天)
         */
        private Duration ttl = Duration.ofDays(7);

        /**
         * 是否启用 L2
         */
        private boolean enabled = true;
    }

    /**
     * L3 缓存配置 (Redis Vector)
     */
    @Data
    public static class L3CacheProperties {
        /**
         * Redis Vector index 名称
         */
        private String indexName = "intent_l3_vector_idx";

        /**
         * 相似度阈值 (默认 0.92)
         */
        private double similarityThreshold = 0.92;

        /**
         * TTL (默认 30 天)
         */
        private Duration ttl = Duration.ofDays(30);

        /**
         * 向量维度 (MiniMax: 768, Kimi: 1536)
         */
        private int vectorDimension = 768;

        /**
         * 是否启用 L3 (默认 false，需要 Redis 7.2+ with RediSearch)
         */
        private boolean enabled = false;
    }

    /**
     * 熔断器配置
     */
    @Data
    public static class CircuitBreakerProperties {
        /**
         * 失败阈值 (连续失败次数)
         */
        private int failureThreshold = 10;

        /**
         * 熔断持续时间
         */
        private Duration waitDurationInOpenState = Duration.ofSeconds(60);

        /**
         * HALF_OPEN 状态下的成功阈值
         */
        private int successThreshold = 3;

        /**
         * 滑动窗口大小
         */
        private int slidingWindowSize = 100;

        /**
         * 是否启用熔断器
         */
        private boolean enabled = true;
    }
}
