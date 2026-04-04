package com.mrshudson.optim.intent.cache;

import com.mrshudson.optim.cache.EmbeddingService;
import com.mrshudson.optim.intent.cache.dto.IntentCacheEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * L3 缓存存储 (Redis Vector / 简化版)
 * 使用 Redis Hash 存储意图缓存条目和向量嵌入
 * 通过遍历计算余弦相似度进行向量搜索
 * <p>
 * 注意：此实现为简化版，不依赖 RediSearch 模块
 * 大规模场景建议升级到 Redis 7.2+ with RediSearch HNSW 索引
 * <p>
 * 对齐 AI_ARCHITECTURE.md v3.5 规范
 * - TTL: 30天
 * - 相似度阈值: 0.92 (可配置)
 * - 向量维度: 768 (MiniMax) / 1536 (Kimi)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class L3CacheStore {

    private static final String KEY_PREFIX = "intent:L3:";
    private static final String FIELD_FINGERPRINT = "fingerprint";
    private static final String FIELD_INTENT_TYPE = "intentType";
    private static final String FIELD_CONFIDENCE = "confidence";
    private static final String FIELD_NEEDS_CLARIFICATION = "needsClarification";
    private static final String FIELD_EMBEDDING = "embedding";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_TTL_SECONDS = "ttlSeconds";
    private static final String FIELD_ACCESS_COUNT = "accessCount";

    private final IntentCacheProperties properties;
    private final RedisTemplate<String, String> redisTemplate;
    private final EmbeddingService embeddingService;

    /**
     * 查询 L3 缓存（向量相似度搜索）
     *
     * @param userId      用户ID
     * @param fingerprint 查询指纹
     * @param queryText   原始查询文本（用于生成向量）
     * @return 最相似的缓存条目
     */
    public Optional<IntentCacheEntry> search(Long userId, String fingerprint, String queryText) {
        if (!isEnabled()) {
            return Optional.empty();
        }

        try {
            // 生成查询向量
            float[] queryEmbedding = embeddingService.embed(queryText);
            double threshold = properties.getL3().getSimilarityThreshold();

            // 获取该用户的所有 L3 缓存 key
            Set<String> keys = getUserCacheKeys(userId);
            if (keys.isEmpty()) {
                return Optional.empty();
            }

            IntentCacheEntry bestMatch = null;
            double bestSimilarity = threshold;

            HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

            for (String key : keys) {
                try {
                    Map<String, String> fields = hashOps.entries(key);
                    if (fields.isEmpty()) {
                        continue;
                    }

                    // 检查是否过期
                    if (isExpired(fields)) {
                        redisTemplate.delete(key);
                        continue;
                    }

                    // 计算向量相似度
                    String embeddingStr = fields.get(FIELD_EMBEDDING);
                    if (embeddingStr == null) {
                        continue;
                    }

                    float[] storedEmbedding = deserializeEmbedding(embeddingStr);
                    double similarity = cosineSimilarity(queryEmbedding, storedEmbedding);

                    if (similarity > bestSimilarity) {
                        bestSimilarity = similarity;
                        bestMatch = buildCacheEntry(fields);
                    }
                } catch (Exception e) {
                    log.warn("Error processing L3 cache entry {}: {}", key, e.getMessage());
                }
            }

            if (bestMatch != null) {
                log.debug("L3 cache hit: userId={}, intentType={}, similarity={}",
                        userId, bestMatch.getIntentType(), bestSimilarity);
            }

            return Optional.ofNullable(bestMatch);

        } catch (Exception e) {
            log.warn("L3 cache search failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 存储缓存条目到 L3
     *
     * @param userId      用户ID
     * @param fingerprint 指纹
     * @param entry       缓存条目
     * @param queryText   原始查询文本（用于生成向量）
     */
    public void put(Long userId, String fingerprint, IntentCacheEntry entry, String queryText) {
        if (!isEnabled()) {
            return;
        }

        try {
            String key = buildKey(userId, fingerprint);
            HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

            // 生成向量嵌入
            float[] embedding = embeddingService.embed(queryText);

            Map<String, String> fields = Map.of(
                    FIELD_FINGERPRINT, fingerprint,
                    FIELD_INTENT_TYPE, entry.getIntentType(),
                    FIELD_CONFIDENCE, String.valueOf(entry.getConfidence()),
                    FIELD_NEEDS_CLARIFICATION, String.valueOf(entry.isNeedsClarification()),
                    FIELD_EMBEDDING, serializeEmbedding(embedding),
                    FIELD_CREATED_AT, String.valueOf(Instant.now().getEpochSecond()),
                    FIELD_TTL_SECONDS, String.valueOf(entry.getTtlSeconds()),
                    FIELD_ACCESS_COUNT, String.valueOf(entry.getAccessCount())
            );

            hashOps.putAll(key, fields);

            // 设置 Redis TTL
            Duration ttl = properties.getL3().getTtl();
            redisTemplate.expire(key, ttl);

            log.debug("L3 cache stored: userId={}, fingerprint={}", userId, fingerprint);

        } catch (Exception e) {
            log.warn("L3 cache store failed: {}", e.getMessage());
        }
    }

    /**
     * 删除指定条目
     */
    public void invalidate(Long userId, String fingerprint) {
        String key = buildKey(userId, fingerprint);
        redisTemplate.delete(key);
        log.debug("L3 cache invalidated: userId={}, fingerprint={}", userId, fingerprint);
    }

    /**
     * 删除用户的所有 L3 缓存
     */
    public void invalidateByUserId(Long userId) {
        Set<String> keys = getUserCacheKeys(userId);
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("L3 cache invalidated for user: userId={}, count={}", userId, keys.size());
        }
    }

    /**
     * 是否启用 L3
     */
    public boolean isEnabled() {
        return properties.isEnabled() && properties.getL3().isEnabled();
    }

    // ========== 私有辅助方法 ==========

    private String buildKey(Long userId, String fingerprint) {
        return KEY_PREFIX + userId + ":" + fingerprint;
    }

    private Set<String> getUserCacheKeys(Long userId) {
        String pattern = KEY_PREFIX + userId + ":*";
        return redisTemplate.keys(pattern);
    }

    private boolean isExpired(Map<String, String> fields) {
        String createdAtStr = fields.get(FIELD_CREATED_AT);
        String ttlSecondsStr = fields.get(FIELD_TTL_SECONDS);

        if (createdAtStr == null || ttlSecondsStr == null) {
            return true;
        }

        long createdAt = Long.parseLong(createdAtStr);
        int ttlSeconds = Integer.parseInt(ttlSecondsStr);
        long now = Instant.now().getEpochSecond();

        return now - createdAt > ttlSeconds;
    }

    private IntentCacheEntry buildCacheEntry(Map<String, String> fields) {
        return IntentCacheEntry.builder()
                .intentType(fields.get(FIELD_INTENT_TYPE))
                .confidence(Double.parseDouble(fields.get(FIELD_CONFIDENCE)))
                .needsClarification(Boolean.parseBoolean(fields.get(FIELD_NEEDS_CLARIFICATION)))
                .createdAt(java.time.LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(Long.parseLong(fields.get(FIELD_CREATED_AT))),
                        java.time.ZoneId.systemDefault()))
                .ttlSeconds(Integer.parseInt(fields.get(FIELD_TTL_SECONDS)))
                .accessCount(Integer.parseInt(fields.get(FIELD_ACCESS_COUNT)))
                .build();
    }

    private String serializeEmbedding(float[] embedding) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        return sb.toString();
    }

    private float[] deserializeEmbedding(String str) {
        String[] parts = str.split(",");
        float[] embedding = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            embedding[i] = Float.parseFloat(parts[i]);
        }
        return embedding;
    }

    /**
     * 计算余弦相似度
     */
    private double cosineSimilarity(float[] v1, float[] v2) {
        if (v1 == null || v2 == null || v1.length != v2.length) {
            return 0.0;
        }

        double dot = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < v1.length; i++) {
            dot += v1[i] * v2[i];
            norm1 += v1[i] * v1[i];
            norm2 += v2[i] * v2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
