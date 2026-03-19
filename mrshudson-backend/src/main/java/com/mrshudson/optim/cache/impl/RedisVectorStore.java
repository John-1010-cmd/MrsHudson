package com.mrshudson.optim.cache.impl;

import com.mrshudson.optim.cache.VectorStore;
import com.mrshudson.optim.util.OptimUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis向量存储实现（简化版）
 * 使用Redis Hash存储向量，通过遍历计算相似度
 * 适用于中小规模缓存场景，无需外部向量数据库依赖
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "optim.vector-store", name = "type", havingValue = "redis", matchIfMissing = true)
public class RedisVectorStore implements VectorStore {

    private static final String KEY_PREFIX = "semantic_cache:";
    private static final String FIELD_QUERY = "query";
    private static final String FIELD_RESPONSE = "response";
    private static final String FIELD_EMBEDDING = "embedding";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_LAST_ACCESSED_AT = "lastAccessedAt";
    private static final String FIELD_ACCESS_COUNT = "accessCount";

    private final RedisTemplate<String, String> redisTemplate;
    private final HashOperations<String, String, String> hashOps;

    public RedisVectorStore(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.hashOps = redisTemplate.opsForHash();
    }

    @Override
    public String store(String userId, String query, String response, float[] embedding) {
        String id = UUID.randomUUID().toString();
        String key = buildKey(userId, id);
        long now = System.currentTimeMillis();

        Map<String, String> fields = new HashMap<>();
        fields.put(FIELD_QUERY, query);
        fields.put(FIELD_RESPONSE, response);
        fields.put(FIELD_EMBEDDING, serializeEmbedding(embedding));
        fields.put(FIELD_USER_ID, userId);
        fields.put(FIELD_CREATED_AT, String.valueOf(now));
        fields.put(FIELD_LAST_ACCESSED_AT, String.valueOf(now));
        fields.put(FIELD_ACCESS_COUNT, "0");

        hashOps.putAll(key, fields);

        log.debug("Stored cache entry: userId={}, id={}", userId, id);
        return id;
    }

    @Override
    public Optional<VectorStore.CacheEntry> search(String userId, float[] queryEmbedding, double threshold) {
        // 获取该用户的所有缓存key
        Set<String> keys = getUserCacheKeys(userId);

        if (keys.isEmpty()) {
            return Optional.empty();
        }

        VectorStore.CacheEntry bestMatch = null;
        double bestSimilarity = threshold;

        for (String key : keys) {
            try {
                Map<String, String> fields = hashOps.entries(key);
                if (fields.isEmpty()) {
                    continue;
                }

                String embeddingStr = fields.get(FIELD_EMBEDDING);
                if (embeddingStr == null) {
                    continue;
                }

                float[] storedEmbedding = deserializeEmbedding(embeddingStr);
                double similarity = OptimUtils.cosineSimilarity(queryEmbedding, storedEmbedding);

                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    bestMatch = buildCacheEntry(key, fields);
                }
            } catch (Exception e) {
                log.warn("Error processing cache entry {}: {}", key, e.getMessage());
            }
        }

        if (bestMatch != null) {
            // 更新访问统计
            updateAccessStats(userId, bestMatch.getId());
            log.debug("Cache hit: userId={}, id={}, similarity={}", userId, bestMatch.getId(), bestSimilarity);
        }

        return Optional.ofNullable(bestMatch);
    }

    @Override
    public boolean delete(String userId, String id) {
        String key = buildKey(userId, id);
        Boolean deleted = redisTemplate.delete(key);
        log.debug("Deleted cache entry: userId={}, id={}, success={}", userId, id, deleted);
        return Boolean.TRUE.equals(deleted);
    }

    @Override
    public int cleanup(String userId) {
        Set<String> keys = getUserCacheKeys(userId);
        long now = System.currentTimeMillis();
        long maxAgeMs = 7 * 24 * 60 * 60 * 1000L; // 7天过期
        int cleanedCount = 0;

        for (String key : keys) {
            try {
                Map<String, String> fields = hashOps.entries(key);
                String createdAtStr = fields.get(FIELD_CREATED_AT);

                if (createdAtStr != null) {
                    long createdAt = Long.parseLong(createdAtStr);
                    if (now - createdAt > maxAgeMs) {
                        redisTemplate.delete(key);
                        cleanedCount++;
                    }
                }
            } catch (Exception e) {
                log.warn("Error cleaning up cache entry {}: {}", key, e.getMessage());
            }
        }

        log.info("Cleaned up {} expired cache entries for user {}", cleanedCount, userId);
        return cleanedCount;
    }

    @Override
    public int deleteAll(String userId) {
        Set<String> keys = getUserCacheKeys(userId);
        if (keys.isEmpty()) {
            return 0;
        }

        Long deleted = redisTemplate.delete(keys);
        int count = deleted != null ? deleted.intValue() : 0;
        log.info("Deleted {} cache entries for user {}", count, userId);
        return count;
    }

    @Override
    public CacheStats getStats(String userId) {
        Set<String> keys = getUserCacheKeys(userId);

        if (keys.isEmpty()) {
            return new CacheStats(userId, 0, 0, 0, 0, 0.0);
        }

        int totalEntries = keys.size();
        long totalSize = 0;
        long oldestEntryTime = Long.MAX_VALUE;
        long newestEntryTime = 0;
        long totalAccessCount = 0;

        for (String key : keys) {
            try {
                Map<String, String> fields = hashOps.entries(key);

                // 估算大小
                for (Map.Entry<String, String> entry : fields.entrySet()) {
                    totalSize += entry.getKey().getBytes(StandardCharsets.UTF_8).length;
                    if (entry.getValue() != null) {
                        totalSize += entry.getValue().getBytes(StandardCharsets.UTF_8).length;
                    }
                }

                String createdAtStr = fields.get(FIELD_CREATED_AT);
                if (createdAtStr != null) {
                    long createdAt = Long.parseLong(createdAtStr);
                    oldestEntryTime = Math.min(oldestEntryTime, createdAt);
                    newestEntryTime = Math.max(newestEntryTime, createdAt);
                }

                String accessCountStr = fields.get(FIELD_ACCESS_COUNT);
                if (accessCountStr != null) {
                    totalAccessCount += Long.parseLong(accessCountStr);
                }
            } catch (Exception e) {
                log.warn("Error getting stats for cache entry {}: {}", key, e.getMessage());
            }
        }

        double avgAccessCount = totalEntries > 0 ? (double) totalAccessCount / totalEntries : 0.0;

        return new CacheStats(
                userId,
                totalEntries,
                totalSize,
                oldestEntryTime == Long.MAX_VALUE ? 0 : oldestEntryTime,
                newestEntryTime,
                avgAccessCount
        );
    }

    /**
     * 构建Redis key
     * 格式: semantic_cache:{userId}:{id}
     */
    private String buildKey(String userId, String id) {
        return KEY_PREFIX + userId + ":" + id;
    }

    /**
     * 从key中提取id
     */
    private String extractIdFromKey(String key) {
        int lastColonIndex = key.lastIndexOf(':');
        return lastColonIndex > 0 ? key.substring(lastColonIndex + 1) : key;
    }

    /**
     * 获取用户的所有缓存key
     */
    private Set<String> getUserCacheKeys(String userId) {
        String pattern = KEY_PREFIX + userId + ":*";
        return redisTemplate.keys(pattern);
    }

    /**
     * 序列化向量（逗号分隔的字符串）
     */
    private String serializeEmbedding(float[] embedding) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        return sb.toString();
    }

    /**
     * 反序列化向量
     */
    private float[] deserializeEmbedding(String str) {
        String[] parts = str.split(",");
        float[] embedding = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            embedding[i] = Float.parseFloat(parts[i]);
        }
        return embedding;
    }

    /**
     * 从Redis字段构建CacheEntry
     */
    private VectorStore.CacheEntry buildCacheEntry(String key, Map<String, String> fields) {
        String id = extractIdFromKey(key);
        String query = fields.get(FIELD_QUERY);
        String response = fields.get(FIELD_RESPONSE);
        String embeddingStr = fields.get(FIELD_EMBEDDING);
        String userId = fields.get(FIELD_USER_ID);
        String createdAtStr = fields.get(FIELD_CREATED_AT);
        String lastAccessedAtStr = fields.get(FIELD_LAST_ACCESSED_AT);
        String accessCountStr = fields.get(FIELD_ACCESS_COUNT);

        float[] embedding = embeddingStr != null ? deserializeEmbedding(embeddingStr) : new float[0];
        long createdAt = createdAtStr != null ? Long.parseLong(createdAtStr) : 0;
        long lastAccessedAt = lastAccessedAtStr != null ? Long.parseLong(lastAccessedAtStr) : createdAt;
        int accessCount = accessCountStr != null ? Integer.parseInt(accessCountStr) : 0;

        VectorStore.CacheEntry entry = new VectorStore.CacheEntry(
                id, query, response, embedding, userId, createdAt
        );
        entry.setLastAccessedAt(lastAccessedAt);
        entry.setAccessCount(accessCount);

        return entry;
    }

    /**
     * 更新访问统计
     */
    private void updateAccessStats(String userId, String id) {
        String key = buildKey(userId, id);
        String accessCountStr = hashOps.get(key, FIELD_ACCESS_COUNT);
        int accessCount = accessCountStr != null ? Integer.parseInt(accessCountStr) : 0;

        hashOps.put(key, FIELD_ACCESS_COUNT, String.valueOf(accessCount + 1));
        hashOps.put(key, FIELD_LAST_ACCESSED_AT, String.valueOf(System.currentTimeMillis()));
    }
}
