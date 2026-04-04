package com.mrshudson.optim.intent.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.mrshudson.optim.intent.cache.dto.IntentCacheEntry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * L1 缓存存储 (ConcurrentHashMap-based via Caffeine)
 * 对齐 AI_ARCHITECTURE.md v3.5 规范
 * - TTL: 5分钟
 * - 最大条目: 500
 * - 淘汰策略: LRU
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class L1CacheStore {

    private final IntentCacheProperties properties;

    private Cache<String, IntentCacheEntry> cache;

    @PostConstruct
    public void init() {
        IntentCacheProperties.L1CacheProperties l1Props = properties.getL1();

        this.cache = Caffeine.newBuilder()
                .maximumSize(l1Props.getMaxSize())
                .expireAfterWrite(l1Props.getTtl())
                .recordStats()
                .build();

        log.info("L1 Cache initialized: maxSize={}, ttl={}",
                l1Props.getMaxSize(), l1Props.getTtl());
    }

    /**
     * 获取缓存条目
     */
    public Optional<IntentCacheEntry> get(String key) {
        if (!properties.getL1().isEnabled()) {
            return Optional.empty();
        }

        IntentCacheEntry entry = cache.getIfPresent(key);
        if (entry != null) {
            entry.incrementAccessCount();
            log.debug("L1 cache hit: key={}", key);
        } else {
            log.debug("L1 cache miss: key={}", key);
        }
        return Optional.ofNullable(entry);
    }

    /**
     * 存储缓存条目
     */
    public void put(String key, IntentCacheEntry entry) {
        if (!properties.getL1().isEnabled()) {
            return;
        }

        cache.put(key, entry);
        log.debug("L1 cache put: key={}", key);
    }

    /**
     * 删除缓存条目
     */
    public void invalidate(String key) {
        cache.invalidate(key);
        log.debug("L1 cache invalidated: key={}", key);
    }

    /**
     * 删除用户的所有缓存
     */
    public void invalidateByUserId(Long userId) {
        // Caffeine 不支持按前缀删除，需要遍历
        // 实际生产环境可能需要更高效的方案
        cache.asMap().entrySet().removeIf(entry ->
                entry.getKey().startsWith(userId + ":"));
        log.info("L1 cache invalidated for user: userId={}", userId);
    }

    /**
     * 清空缓存
     */
    public void invalidateAll() {
        cache.invalidateAll();
        log.info("L1 cache invalidated all");
    }

    /**
     * 获取统计信息
     */
    public CacheStats getStats() {
        return cache.stats();
    }

    /**
     * 获取当前大小
     */
    public long getSize() {
        return cache.estimatedSize();
    }

    /**
     * 是否启用
     */
    public boolean isEnabled() {
        return properties.getL1().isEnabled();
    }
}
