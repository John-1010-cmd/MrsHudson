package com.mrshudson.optim.cost;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 语义缓存服务
 * 支持精确匹配和语义相似匹配
 */
@Slf4j
@Service
public class SemanticCacheService implements CostOptimizer {

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 缓存前缀
     */
    private static final String CACHE_PREFIX = "ai:semantic_cache:";

    /**
     * 相似度阈值（0.9 = 90% 相似）
     */
    private static final double SIMILARITY_THRESHOLD = 0.9;

    /**
     * 默认缓存 TTL
     */
    @Value("${ai.cache.ttl:24}")
    private int cacheTtlHours;

    /**
     * 缓存命中计数器
     */
    private final AtomicLong cacheHits = new AtomicLong(0);

    /**
     * 缓存查询计数器
     */
    private final AtomicLong cacheQueries = new AtomicLong(0);

    public SemanticCacheService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public CachedResult checkCache(Long userId, String message) {
        cacheQueries.incrementAndGet();

        if (message == null || message.isEmpty()) {
            return CachedResult.miss();
        }

        try {
            // 1. 精确匹配
            String exactKey = buildCacheKey(userId, message);
            String exactResult = redisTemplate.opsForValue().get(exactKey);

            if (exactResult != null) {
                cacheHits.incrementAndGet();
                log.debug("缓存精确命中，用户: {}, 消息: {}", userId, message.substring(0, Math.min(20, message.length())));
                return CachedResult.hit(exactResult, CachedResult.MatchType.EXACT);
            }

            // 2. 语义相似匹配（简化版：基于关键词匹配）
            String semanticResult = checkSemanticMatch(userId, message);
            if (semanticResult != null) {
                cacheHits.incrementAndGet();
                log.debug("缓存语义命中，用户: {}", userId);
                return CachedResult.hit(semanticResult, CachedResult.MatchType.SEMANTIC);
            }

            return CachedResult.miss();

        } catch (Exception e) {
            log.error("缓存查询失败: {}", e.getMessage(), e);
            return CachedResult.miss();
        }
    }

    /**
     * 简化版语义匹配：基于关键词重叠度
     */
    private String checkSemanticMatch(Long userId, String message) {
        String normalizedMessage = normalizeText(message);
        Set<String> keywords = extractKeywords(normalizedMessage);

        if (keywords.isEmpty()) {
            return null;
        }

        // 遍历用户的缓存，找相似的
        Set<String> cacheKeys = redisTemplate.keys(CACHE_PREFIX + userId + ":keyword:*");
        if (cacheKeys == null || cacheKeys.isEmpty()) {
            return null;
        }

        for (String key : cacheKeys) {
            String cachedKeywordsStr = redisTemplate.opsForValue().get(key);
            if (cachedKeywordsStr == null) {
                continue;
            }

            Set<String> cachedKeywords = Set.of(cachedKeywordsStr.split(","));
            double similarity = calculateSimilarity(keywords, cachedKeywords);

            if (similarity >= SIMILARITY_THRESHOLD) {
                // 从精确匹配key获取结果
                String exactKey = key.replace(":keyword:", ":");
                return redisTemplate.opsForValue().get(exactKey);
            }
        }

        return null;
    }

    /**
     * 提取关键词
     */
    private Set<String> extractKeywords(String text) {
        // 简单分词：去除停用词，保留有意义的词
        String[] words = text.split("[\\s，。、！？,.!?;:]+");
        return java.util.Arrays.stream(words)
            .filter(w -> w.length() > 1)
            .filter(w -> !isStopWord(w))
            .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 判断是否为停用词
     */
    private boolean isStopWord(String word) {
        Set<String> stopWords = Set.of("的", "了", "是", "在", "我", "有", "和", "就", "不", "人", "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好", "自己", "这", "那", "什么");
        return stopWords.contains(word);
    }

    /**
     * 计算相似度
     */
    private double calculateSimilarity(Set<String> keywords1, Set<String> keywords2) {
        if (keywords1.isEmpty() || keywords2.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new java.util.HashSet<>(keywords1);
        intersection.retainAll(keywords2);

        Set<String> union = new java.util.HashSet<>(keywords1);
        union.addAll(keywords2);

        return (double) intersection.size() / union.size();
    }

    /**
     * 标准化文本
     */
    private String normalizeText(String text) {
        return text.toLowerCase()
            .replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", " ")
            .trim();
    }

    /**
     * 构建缓存键
     */
    private String buildCacheKey(Long userId, String message) {
        String hash = Integer.toHexString(message.hashCode());
        return CACHE_PREFIX + userId + ":" + hash;
    }

    @Override
    public void saveCache(Long userId, String message, String response) {
        if (message == null || message.isEmpty() || response == null) {
            return;
        }

        try {
            String cacheKey = buildCacheKey(userId, message);
            redisTemplate.opsForValue().set(cacheKey, response, Duration.ofHours(cacheTtlHours));

            // 同时保存关键词用于语义匹配
            // key格式：ai:semantic_cache:{userId}:keyword:{hash}，与checkSemanticMatch查询模式一致
            String normalizedMessage = normalizeText(message);
            Set<String> keywords = extractKeywords(normalizedMessage);
            String keywordKey = CACHE_PREFIX + userId + ":keyword:" + Integer.toHexString(message.hashCode());
            redisTemplate.opsForValue().set(keywordKey, String.join(",", keywords), Duration.ofHours(cacheTtlHours));

            log.debug("缓存已保存，用户: {}, 关键词数: {}", userId, keywords.size());

        } catch (Exception e) {
            log.error("缓存保存失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void clearUserCache(Long userId) {
        try {
            Set<String> keys = redisTemplate.keys(CACHE_PREFIX + userId + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("已清除用户 {} 的缓存，共 {} 条", userId, keys.size());
            }
        } catch (Exception e) {
            log.error("清除缓存失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public double getCacheHitRate() {
        long queries = cacheQueries.get();
        if (queries == 0) {
            return 0.0;
        }
        return (double) cacheHits.get() / queries;
    }

    /**
     * 判断是否使用小模型（缓存服务不负责模型选择）
     * 此方法由 ModelRouter 实现
     */
    @Override
    public boolean shouldUseSmallModel(String message) {
        // 缓存服务不判断模型大小，默认返回true
        return true;
    }
}
