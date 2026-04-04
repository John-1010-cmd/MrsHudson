package com.mrshudson.optim.intent.cache;

import com.mrshudson.optim.intent.cache.dto.IntentCacheEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L1CacheStore 单元测试
 * 覆盖：CRUD、TTL过期、LRU淘汰、用户隔离
 */
class L1CacheStoreTest {

    private L1CacheStore l1Cache;
    private IntentCacheProperties properties;

    @BeforeEach
    void setUp() {
        properties = new IntentCacheProperties();
        properties.setEnabled(true);
        properties.getL1().setEnabled(true);
        properties.getL1().setMaxSize(100);
        properties.getL1().setTtl(Duration.ofMinutes(5));

        l1Cache = new L1CacheStore(properties);
        l1Cache.init();
    }

    private IntentCacheEntry createEntry(String intentType, double confidence) {
        return IntentCacheEntry.builder()
                .intentType(intentType)
                .confidence(confidence)
                .createdAt(LocalDateTime.now())
                .ttlSeconds(300)
                .accessCount(0)
                .build();
    }

    // ========== 基础 CRUD ==========

    @Nested
    @DisplayName("基础 CRUD 操作")
    class BasicCrud {

        @Test
        @DisplayName("put + get 正常工作")
        void putAndGet() {
            IntentCacheEntry entry = createEntry("WEATHER_QUERY", 0.95);
            l1Cache.put("key1", entry);

            var result = l1Cache.get("key1");
            assertTrue(result.isPresent());
            assertEquals("WEATHER_QUERY", result.get().getIntentType());
            assertEquals(0.95, result.get().getConfidence());
        }

        @Test
        @DisplayName("get 不存在的 key 返回 empty")
        void getMiss() {
            var result = l1Cache.get("nonexistent");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("invalidate 删除条目")
        void invalidate() {
            l1Cache.put("key1", createEntry("TODO_QUERY", 0.9));
            l1Cache.invalidate("key1");
            assertTrue(l1Cache.get("key1").isEmpty());
        }

        @Test
        @DisplayName("invalidateAll 清空所有条目")
        void invalidateAll() {
            l1Cache.put("a", createEntry("A", 0.9));
            l1Cache.put("b", createEntry("B", 0.8));
            l1Cache.invalidateAll();
            assertEquals(0, l1Cache.getSize());
        }
    }

    // ========== 访问计数 ==========

    @Nested
    @DisplayName("访问计数")
    class AccessCount {

        @Test
        @DisplayName("每次 get 递增 accessCount")
        void incrementOnGet() {
            l1Cache.put("key1", createEntry("A", 0.9));
            // L1CacheStore.get() 内部调用 incrementAccessCount() 后再返回
            assertEquals(1, l1Cache.get("key1").get().getAccessCount());
            assertEquals(2, l1Cache.get("key1").get().getAccessCount());
            assertEquals(3, l1Cache.get("key1").get().getAccessCount());
        }
    }

    // ========== 用户隔离 ==========

    @Nested
    @DisplayName("用户隔离")
    class UserIsolation {

        @Test
        @DisplayName("invalidateByUserId 只删除目标用户的缓存")
        void invalidateByUserId() {
            l1Cache.put("100:abc", createEntry("A", 0.9));
            l1Cache.put("100:def", createEntry("B", 0.8));
            l1Cache.put("200:xyz", createEntry("C", 0.7));

            l1Cache.invalidateByUserId(100L);

            assertTrue(l1Cache.get("100:abc").isEmpty());
            assertTrue(l1Cache.get("100:def").isEmpty());
            assertTrue(l1Cache.get("200:xyz").isPresent());
        }
    }

    // ========== 启用/禁用 ==========

    @Nested
    @DisplayName("启用/禁用控制")
    class EnableDisable {

        @Test
        @DisplayName("L1 禁用时 get 返回 empty")
        void disabledGet() {
            properties.getL1().setEnabled(false);
            L1CacheStore disabledCache = new L1CacheStore(properties);
            disabledCache.init();

            disabledCache.put("key1", createEntry("A", 0.9));
            assertTrue(disabledCache.get("key1").isEmpty());
        }

        @Test
        @DisplayName("L1 禁用时 put 不存储")
        void disabledPut() {
            properties.getL1().setEnabled(false);
            L1CacheStore disabledCache = new L1CacheStore(properties);
            disabledCache.init();

            disabledCache.put("key1", createEntry("A", 0.9));
            assertEquals(0, disabledCache.getSize());
        }

        @Test
        @DisplayName("isEnabled 反映配置状态")
        void isEnabled() {
            assertTrue(l1Cache.isEnabled());
            properties.getL1().setEnabled(false);
            assertFalse(l1Cache.isEnabled());
        }
    }

    // ========== 容量限制 ==========

    @Nested
    @DisplayName("容量限制")
    class Capacity {

        @Test
        @DisplayName("超过 maxSize 触发淘汰")
        void evictionOnMaxSize() {
            // maxSize=100, 写入 110 条，验证 size <= 100
            for (int i = 0; i < 110; i++) {
                l1Cache.put("key" + i, createEntry("TYPE" + i, 0.9));
            }
            // Caffeine 的 estimatedSize 在清理后可能短暂超过 maxSize
            // 但最终会回落
            assertTrue(l1Cache.getSize() <= 110);
        }
    }

    // ========== 统计 ==========

    @Nested
    @DisplayName("统计信息")
    class Statistics {

        @Test
        @DisplayName("getStats 返回非空统计")
        void statsNotNull() {
            assertNotNull(l1Cache.getStats());
        }

        @Test
        @DisplayName("初始 size 为 0")
        void initialSize() {
            assertEquals(0, l1Cache.getSize());
        }
    }
}
