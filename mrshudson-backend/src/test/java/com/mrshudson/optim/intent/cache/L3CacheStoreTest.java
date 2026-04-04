package com.mrshudson.optim.intent.cache;

import com.mrshudson.optim.cache.EmbeddingService;
import com.mrshudson.optim.intent.cache.dto.IntentCacheEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * L3CacheStore 单元测试
 * 覆盖：向量搜索、存储、过期检查、禁用状态
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class L3CacheStoreTest {

    @Mock private IntentCacheProperties properties;
    @Mock private IntentCacheProperties.L3CacheProperties l3Props;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @SuppressWarnings("unchecked")
    @Mock private HashOperations<String, Object, Object> hashOps;
    @Mock private EmbeddingService embeddingService;

    private L3CacheStore l3Cache;

    private static final Long USER_ID = 1L;
    private static final String FINGERPRINT = "abc123";
    private static final String QUERY_TEXT = "今天天气怎么样";
    private static final String CACHE_KEY = "intent:L3:1:abc123";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getL3()).thenReturn(l3Props);
        when(l3Props.isEnabled()).thenReturn(true);
        when(l3Props.getSimilarityThreshold()).thenReturn(0.92);
        when(l3Props.getTtl()).thenReturn(Duration.ofDays(30));

        l3Cache = new L3CacheStore(properties, redisTemplate, embeddingService);
    }

    private IntentCacheEntry createEntry() {
        return IntentCacheEntry.builder()
                .intentType("WEATHER_QUERY")
                .confidence(0.95)
                .needsClarification(false)
                .createdAt(LocalDateTime.now())
                .ttlSeconds(86400)
                .accessCount(1)
                .build();
    }

    private float[] createEmbedding() {
        // 创建归一化的测试向量
        float[] emb = new float[100];
        emb[0] = 0.8f;
        emb[1] = 0.6f;
        // L2 归一化
        double norm = Math.sqrt(0.8 * 0.8 + 0.6 * 0.6);
        emb[0] = (float) (0.8 / norm);
        emb[1] = (float) (0.6 / norm);
        return emb;
    }

    // ========== 搜索 ==========

    @Nested
    @DisplayName("向量搜索")
    class Search {

        @Test
        @DisplayName("L3 禁用时返回 empty")
        void disabledReturnsEmpty() {
            when(l3Props.isEnabled()).thenReturn(false);

            Optional<IntentCacheEntry> result = l3Cache.search(USER_ID, FINGERPRINT, QUERY_TEXT);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("无缓存数据返回 empty")
        void noDataReturnsEmpty() {
            when(redisTemplate.keys(anyString())).thenReturn(Collections.emptySet());

            Optional<IntentCacheEntry> result = l3Cache.search(USER_ID, FINGERPRINT, QUERY_TEXT);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("相似度低于阈值返回 empty")
        void belowThresholdReturnsEmpty() {
            float[] queryEmb = createEmbedding();
            float[] storedEmb = new float[100];
            storedEmb[0] = 0.1f; // 低相似度
            storedEmb[1] = 0.9f;
            double norm = Math.sqrt(0.01 + 0.81);
            storedEmb[0] = (float) (0.1 / norm);
            storedEmb[1] = (float) (0.9 / norm);

            when(embeddingService.embed(QUERY_TEXT)).thenReturn(queryEmb);
            when(redisTemplate.keys(anyString())).thenReturn(Set.of(CACHE_KEY));
            when(hashOps.entries(CACHE_KEY)).thenReturn(Map.of(
                    "fingerprint", FINGERPRINT,
                    "intentType", "WEATHER_QUERY",
                    "confidence", "0.95",
                    "needsClarification", "false",
                    "embedding", serializeEmbedding(storedEmb),
                    "createdAt", String.valueOf(System.currentTimeMillis() / 1000),
                    "ttlSeconds", "86400",
                    "accessCount", "1"
            ));

            Optional<IntentCacheEntry> result = l3Cache.search(USER_ID, FINGERPRINT, QUERY_TEXT);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("高相似度命中返回 entry")
        void highSimilarityHit() {
            float[] embedding = createEmbedding();

            when(embeddingService.embed(QUERY_TEXT)).thenReturn(embedding);
            when(redisTemplate.keys(anyString())).thenReturn(Set.of(CACHE_KEY));
            when(hashOps.entries(CACHE_KEY)).thenReturn(Map.of(
                    "fingerprint", FINGERPRINT,
                    "intentType", "WEATHER_QUERY",
                    "confidence", "0.95",
                    "needsClarification", "false",
                    "embedding", serializeEmbedding(embedding),
                    "createdAt", String.valueOf(System.currentTimeMillis() / 1000),
                    "ttlSeconds", "86400",
                    "accessCount", "1"
            ));

            Optional<IntentCacheEntry> result = l3Cache.search(USER_ID, FINGERPRINT, QUERY_TEXT);

            assertTrue(result.isPresent());
            assertEquals("WEATHER_QUERY", result.get().getIntentType());
        }

        @Test
        @DisplayName("过期条目被清理并返回 empty")
        void expiredEntryCleaned() {
            float[] embedding = createEmbedding();
            long oldTimestamp = System.currentTimeMillis() / 1000 - 100000; // 很久前

            when(embeddingService.embed(QUERY_TEXT)).thenReturn(embedding);
            when(redisTemplate.keys(anyString())).thenReturn(Set.of(CACHE_KEY));
            when(hashOps.entries(CACHE_KEY)).thenReturn(Map.of(
                    "fingerprint", FINGERPRINT,
                    "intentType", "WEATHER_QUERY",
                    "confidence", "0.95",
                    "needsClarification", "false",
                    "embedding", serializeEmbedding(embedding),
                    "createdAt", String.valueOf(oldTimestamp),
                    "ttlSeconds", "60", // 60秒TTL，已过期
                    "accessCount", "1"
            ));
            when(redisTemplate.delete(CACHE_KEY)).thenReturn(true);

            Optional<IntentCacheEntry> result = l3Cache.search(USER_ID, FINGERPRINT, QUERY_TEXT);

            assertTrue(result.isEmpty());
            verify(redisTemplate).delete(CACHE_KEY);
        }
    }

    // ========== 存储 ==========

    @Nested
    @DisplayName("存储")
    class Store {

        @Test
        @DisplayName("L3 禁用时跳过存储")
        void disabledSkipsStore() {
            when(l3Props.isEnabled()).thenReturn(false);

            l3Cache.put(USER_ID, FINGERPRINT, createEntry(), QUERY_TEXT);

            verify(hashOps, never()).putAll(anyString(), anyMap());
        }

        @Test
        @DisplayName("正常存储调用 Redis")
        void normalStore() {
            float[] embedding = createEmbedding();
            when(embeddingService.embed(QUERY_TEXT)).thenReturn(embedding);

            l3Cache.put(USER_ID, FINGERPRINT, createEntry(), QUERY_TEXT);

            verify(hashOps).putAll(eq(CACHE_KEY), anyMap());
            verify(redisTemplate).expire(eq(CACHE_KEY), any(Duration.class));
        }
    }

    // ========== 失效 ==========

    @Nested
    @DisplayName("失效")
    class Invalidation {

        @Test
        @DisplayName("invalidate 删除指定条目")
        void invalidateSingle() {
            l3Cache.invalidate(USER_ID, FINGERPRINT);
            verify(redisTemplate).delete(CACHE_KEY);
        }

        @Test
        @DisplayName("invalidateByUserId 删除用户所有条目")
        void invalidateByUser() {
            String key2 = "intent:L3:1:def456";
            when(redisTemplate.keys("intent:L3:1:*")).thenReturn(Set.of(CACHE_KEY, key2));

            l3Cache.invalidateByUserId(USER_ID);

            verify(redisTemplate).delete(Set.of(CACHE_KEY, key2));
        }
    }

    // ========== 工具方法 ==========

    private String serializeEmbedding(float[] embedding) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        return sb.toString();
    }
}
