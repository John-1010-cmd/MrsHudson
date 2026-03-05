package com.mrshudson.optim.cache.scheduler;

import com.mrshudson.optim.cache.SemanticCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 缓存清理定时任务
 * 定期清理过期缓存数据，释放存储空间
 */
@Slf4j
@Component
public class CacheCleanupScheduler {

    private static final String KEY_PREFIX = "semantic_cache:";
    private static final long MAX_IDLE_DAYS = 7;
    private static final long MAX_IDLE_MILLIS = MAX_IDLE_DAYS * 24 * 60 * 60 * 1000L;

    private final RedisTemplate<String, String> redisTemplate;
    private final SemanticCacheService semanticCacheService;

    public CacheCleanupScheduler(RedisTemplate<String, String> redisTemplate,
                                 SemanticCacheService semanticCacheService) {
        this.redisTemplate = redisTemplate;
        this.semanticCacheService = semanticCacheService;
    }

    /**
     * 每天凌晨3点执行缓存清理任务
     * 清理超过7天未访问的缓存数据
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Async
    public void cleanupExpiredCache() {
        log.info("Starting scheduled cache cleanup task at 03:00 AM");

        try {
            // 获取所有语义缓存的key
            Set<String> allCacheKeys = redisTemplate.keys(KEY_PREFIX + "*");

            if (allCacheKeys == null || allCacheKeys.isEmpty()) {
                log.info("No cache entries found for cleanup");
                return;
            }

            long now = System.currentTimeMillis();
            int cleanedCount = 0;
            int totalChecked = 0;

            for (String key : allCacheKeys) {
                try {
                    totalChecked++;

                    // 获取最后访问时间
                    String lastAccessedStr = (String) redisTemplate.opsForHash().get(key, "lastAccessedAt");

                    if (lastAccessedStr != null) {
                        long lastAccessedAt = Long.parseLong(lastAccessedStr);
                        long idleTime = now - lastAccessedAt;

                        // 如果超过7天未访问，删除该缓存条目
                        if (idleTime > MAX_IDLE_MILLIS) {
                            redisTemplate.delete(key);
                            cleanedCount++;

                            if (cleanedCount % 100 == 0) {
                                log.debug("Cleaned {} expired cache entries so far", cleanedCount);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error processing cache entry {} during cleanup: {}", key, e.getMessage());
                    // 继续处理其他条目，不抛出异常
                }
            }

            log.info("Cache cleanup completed. Checked: {}, Cleaned: {}", totalChecked, cleanedCount);

        } catch (Exception e) {
            log.error("Error during scheduled cache cleanup: {}", e.getMessage(), e);
            // 异步执行，异常不抛出
        }
    }

    /**
     * 每周日凌晨4点执行深度清理任务
     * 清理所有用户的过期缓存并生成统计报告
     */
    @Scheduled(cron = "0 0 4 ? * SUN")
    @Async
    public void weeklyDeepCleanup() {
        log.info("Starting weekly deep cache cleanup");

        try {
            // 获取所有语义缓存的key
            Set<String> allCacheKeys = redisTemplate.keys(KEY_PREFIX + "*");

            if (allCacheKeys == null || allCacheKeys.isEmpty()) {
                log.info("No cache entries found for weekly cleanup");
                return;
            }

            long now = System.currentTimeMillis();
            int totalEntries = allCacheKeys.size();
            int expiredEntries = 0;
            int oldEntries = 0;
            long totalSize = 0;

            for (String key : allCacheKeys) {
                try {
                    // 获取缓存条目信息
                    String lastAccessedStr = (String) redisTemplate.opsForHash().get(key, "lastAccessedAt");
                    String createdAtStr = (String) redisTemplate.opsForHash().get(key, "createdAt");
                    String query = (String) redisTemplate.opsForHash().get(key, "query");

                    if (lastAccessedStr != null) {
                        long lastAccessedAt = Long.parseLong(lastAccessedStr);
                        long idleTime = now - lastAccessedAt;

                        if (idleTime > MAX_IDLE_MILLIS) {
                            redisTemplate.delete(key);
                            expiredEntries++;
                        } else if (createdAtStr != null) {
                            long createdAt = Long.parseLong(createdAtStr);
                            // 超过30天的旧条目
                            if (now - createdAt > 30L * 24 * 60 * 60 * 1000L) {
                                oldEntries++;
                            }
                        }
                    }

                    // 估算大小
                    if (query != null) {
                        totalSize += query.getBytes().length;
                    }

                } catch (Exception e) {
                    log.warn("Error processing cache entry {} during weekly cleanup: {}", key, e.getMessage());
                }
            }

            log.info("Weekly cleanup report - Total entries: {}, Expired removed: {}, Old entries (30d+): {}, Estimated size: {} bytes",
                    totalEntries, expiredEntries, oldEntries, totalSize);

        } catch (Exception e) {
            log.error("Error during weekly deep cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * 手动触发缓存清理（供管理接口调用）
     *
     * @param maxIdleDays 最大空闲天数
     * @return 清理的条目数量
     */
    public int cleanupManually(int maxIdleDays) {
        log.info("Manual cache cleanup triggered with maxIdleDays={}", maxIdleDays);

        try {
            Set<String> allCacheKeys = redisTemplate.keys(KEY_PREFIX + "*");

            if (allCacheKeys == null || allCacheKeys.isEmpty()) {
                return 0;
            }

            long now = System.currentTimeMillis();
            long maxIdleMillis = maxIdleDays * 24L * 60 * 60 * 1000L;
            int cleanedCount = 0;

            for (String key : allCacheKeys) {
                try {
                    String lastAccessedStr = (String) redisTemplate.opsForHash().get(key, "lastAccessedAt");

                    if (lastAccessedStr != null) {
                        long lastAccessedAt = Long.parseLong(lastAccessedStr);
                        long idleTime = now - lastAccessedAt;

                        if (idleTime > maxIdleMillis) {
                            redisTemplate.delete(key);
                            cleanedCount++;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error processing cache entry {} during manual cleanup: {}", key, e.getMessage());
                }
            }

            log.info("Manual cleanup completed. Cleaned {} entries", cleanedCount);
            return cleanedCount;

        } catch (Exception e) {
            log.error("Error during manual cache cleanup: {}", e.getMessage(), e);
            return 0;
        }
    }
}
