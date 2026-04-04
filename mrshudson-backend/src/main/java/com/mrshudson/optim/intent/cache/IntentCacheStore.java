package com.mrshudson.optim.intent.cache;

import com.mrshudson.optim.intent.cache.dto.CacheStatistics;
import com.mrshudson.optim.intent.cache.dto.IntentCacheEntry;
import com.mrshudson.optim.intent.cache.dto.IntentFingerprint;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Intent Cache Store - 三级缓存 Facade
 * L1: Caffeine (内存) - TTL 5分钟, 500条目
 * L2: Redis Hash - TTL 7天
 * L3: Redis Vector (可选) - TTL 30天
 * <p>
 * 对齐 AI_ARCHITECTURE.md v3.5 规范
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentCacheStore {

    private final IntentCacheProperties properties;
    private final QueryNormalizer queryNormalizer;
    private final L1CacheStore l1Cache;
    private final L2CacheStore l2Cache;
    private final L3CacheStore l3Cache;
    private final CacheCircuitBreaker circuitBreaker;
    private final IntentCacheMetrics cacheMetrics;

    @PostConstruct
    public void init() {
        log.info("IntentCacheStore initialized: L1={}, L2={}, L3={}",
                l1Cache.isEnabled(),
                l2Cache.isEnabled(),
                l3Cache.isEnabled());
    }

    /**
     * 查询缓存 (L1 → L2 → L3 级联)
     *
     * @param userInput 用户原始输入
     * @param userId    用户ID
     * @return 缓存条目 (Optional)
     */
    public Optional<IntentCacheEntry> lookup(String userInput, Long userId) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }

        // 1. 归一化输入
        IntentFingerprint fingerprint = queryNormalizer.normalize(userInput);
        if (fingerprint == null) {
            return Optional.empty();
        }

        String cacheKey = fingerprint.generateCacheKey(userId);

        // 2. 带熔断保护的查询
        return circuitBreaker.execute(
                () -> doLookup(cacheKey, userId, fingerprint.getFingerprintHash(), userInput),
                () -> Optional.empty() // fallback: 直接返回空
        );
    }

    /**
     * 执行实际查询逻辑
     */
    private Optional<IntentCacheEntry> doLookup(String cacheKey, Long userId, String fingerprint, String userInput) {
        long startNanos = System.nanoTime();

        // L1 查询
        Optional<IntentCacheEntry> l1Result = l1Cache.get(cacheKey);
        if (l1Result.isPresent()) {
            cacheMetrics.recordL1Hit(System.nanoTime() - startNanos);
            return l1Result;
        }
        cacheMetrics.recordL1Miss();

        // L2 查询
        Optional<IntentCacheEntry> l2Result = l2Cache.get(userId, fingerprint);
        if (l2Result.isPresent()) {
            cacheMetrics.recordL2Hit(System.nanoTime() - startNanos);
            // 回填 L1
            l1Cache.put(cacheKey, l2Result.get());
            return l2Result;
        }
        cacheMetrics.recordL2Miss();

        // L3 查询 (向量相似度搜索)
        Optional<IntentCacheEntry> l3Result = l3Cache.search(userId, fingerprint, userInput);
        if (l3Result.isPresent()) {
            cacheMetrics.recordL3Hit(System.nanoTime() - startNanos);
            // 回填 L2, L1
            backfill(l3Result.get(), cacheKey, userId, fingerprint);
            return l3Result;
        }
        cacheMetrics.recordL3Miss();

        cacheMetrics.recordTotalMiss(System.nanoTime() - startNanos);
        return Optional.empty();
    }

    /**
     * 存储缓存条目
     *
     * @param userInput 用户原始输入
     * @param userId    用户ID
     * @param entry     缓存条目
     */
    public void store(String userInput, Long userId, IntentCacheEntry entry) {
        if (!properties.isEnabled()) {
            return;
        }

        IntentFingerprint fingerprint = queryNormalizer.normalize(userInput);
        if (fingerprint == null) {
            return;
        }

        String cacheKey = fingerprint.generateCacheKey(userId);

        // 异步存储，不阻塞主流程
        circuitBreaker.execute(
                () -> {
                    doStore(cacheKey, userId, fingerprint.getFingerprintHash(), entry, userInput);
                    return null;
                },
                () -> null
        );
    }

    /**
     * 执行实际存储逻辑
     */
    private void doStore(String cacheKey, Long userId, String fingerprint, IntentCacheEntry entry, String userInput) {
        // 存储到 L1
        if (l1Cache.isEnabled()) {
            l1Cache.put(cacheKey, entry);
        }

        // 存储到 L2
        if (l2Cache.isEnabled()) {
            l2Cache.put(userId, fingerprint, entry);
        }

        // 存储到 L3 (异步，需原始文本生成向量)
        if (l3Cache.isEnabled()) {
            l3Cache.put(userId, fingerprint, entry, userInput);
        }

        log.debug("Intent cached: userId={}, fingerprint={}", userId, fingerprint);
    }

    /**
     * 回填缓存 (L3 -> L2 -> L1)
     */
    private void backfill(IntentCacheEntry entry, String cacheKey, Long userId, String fingerprint) {
        l2Cache.put(userId, fingerprint, entry);
        l1Cache.put(cacheKey, entry);
    }

    /**
     * 使某用户的所有缓存失效
     */
    public void invalidate(Long userId) {
        l1Cache.invalidateByUserId(userId);
        l2Cache.invalidateByUserId(userId);
        l3Cache.invalidateByUserId(userId);
        log.info("Cache invalidated for user: userId={}", userId);
    }

    /**
     * 获取统计信息
     */
    public CacheStatistics getStatistics() {
        return cacheMetrics.getStatistics(l1Cache.getSize());
    }

    /**
     * 重置统计
     */
    public void resetStatistics() {
        cacheMetrics.reset();
    }

    /**
     * 获取熔断器状态
     */
    public CacheCircuitBreaker.State getCircuitBreakerState() {
        return circuitBreaker.getState();
    }
}
