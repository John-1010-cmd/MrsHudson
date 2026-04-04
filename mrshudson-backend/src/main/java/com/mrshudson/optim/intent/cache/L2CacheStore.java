package com.mrshudson.optim.intent.cache;

import com.alibaba.fastjson2.JSON;
import com.mrshudson.optim.intent.cache.dto.IntentCacheEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * L2 缓存存储 (Redis Hash)
 * 对齐 AI_ARCHITECTURE.md v3.5 规范
 * - TTL: 7天
 * - 结构: Redis Hash
 * - Key: intent:L2:{userId}:{fingerprint}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class L2CacheStore {

    private final IntentCacheProperties properties;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 获取缓存条目
     */
    public Optional<IntentCacheEntry> get(Long userId, String fingerprint) {
        if (!properties.getL2().isEnabled()) {
            return Optional.empty();
        }

        String key = buildKey(userId, fingerprint);
        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

        String json = hashOps.get(key, "data");
        if (json == null) {
            log.debug("L2 cache miss: key={}", key);
            return Optional.empty();
        }

        try {
            IntentCacheEntry entry = JSON.parseObject(json, IntentCacheEntry.class);
            entry.incrementAccessCount();
            log.debug("L2 cache hit: key={}", key);
            return Optional.of(entry);
        } catch (Exception e) {
            log.error("L2 cache deserialize error: key={}", key, e);
            return Optional.empty();
        }
    }

    /**
     * 存储缓存条目
     */
    public void put(Long userId, String fingerprint, IntentCacheEntry entry) {
        if (!properties.getL2().isEnabled()) {
            return;
        }

        String key = buildKey(userId, fingerprint);
        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

        String json = JSON.toJSONString(entry);
        hashOps.put(key, "data", json);

        // 设置 TTL
        Duration ttl = properties.getL2().getTtl();
        redisTemplate.expire(key, ttl.getSeconds(), TimeUnit.SECONDS);

        log.debug("L2 cache put: key={}, ttl={}s", key, ttl.getSeconds());
    }

    /**
     * 删除缓存条目
     */
    public void invalidate(Long userId, String fingerprint) {
        String key = buildKey(userId, fingerprint);
        redisTemplate.delete(key);
        log.debug("L2 cache invalidated: key={}", key);
    }

    /**
     * 删除用户的所有缓存
     */
    public void invalidateByUserId(Long userId) {
        String pattern = buildKeyPrefix(userId) + "*";
        // 使用 scan 避免阻塞
        var connection = redisTemplate.getConnectionFactory().getConnection();
        var scanOptions = org.springframework.data.redis.core.ScanOptions.scanOptions()
                .match(pattern)
                .count(100)
                .build();

        var cursor = connection.scan(scanOptions);
        int count = 0;
        while (cursor.hasNext()) {
            byte[] key = cursor.next();
            connection.del(key);
            count++;
        }

        log.info("L2 cache invalidated for user: userId={}, count={}", userId, count);
    }

    /**
     * 清空所有缓存 (慎用)
     */
    public void invalidateAll() {
        String pattern = properties.getL2().getKeyPrefix() + ":*";
        // 实际生产环境应该使用 scan
        log.warn("L2 cache invalidate all requested: pattern={}", pattern);
    }

    /**
     * 构建 Redis Key
     */
    private String buildKey(Long userId, String fingerprint) {
        return String.format("%s:%d:%s",
                properties.getL2().getKeyPrefix(),
                userId,
                fingerprint);
    }

    /**
     * 构建 Key 前缀
     */
    private String buildKeyPrefix(Long userId) {
        return String.format("%s:%d:",
                properties.getL2().getKeyPrefix(),
                userId);
    }

    /**
     * 是否启用
     */
    public boolean isEnabled() {
        return properties.getL2().isEnabled();
    }
}
