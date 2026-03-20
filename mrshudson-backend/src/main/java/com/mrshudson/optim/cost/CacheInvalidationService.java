package com.mrshudson.optim.cost;

import com.mrshudson.optim.cache.ToolCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 缓存失效服务
 * 提供手动和自动的缓存清除功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheInvalidationService {

    private final ToolCacheManager toolCacheManager;
    private final RedisTemplate<String, String> redisTemplate;

    // 缓存前缀常量
    private static final String TOOL_CACHE_PREFIX = "tool:";
    private static final String SEMANTIC_CACHE_PREFIX = "ai:semantic_cache:";
    private static final String USER_CACHE_PREFIX = "user:cache:";

    /**
     * 清除指定用户的所有缓存
     *
     * @param userId 用户ID
     */
    public void invalidateUserCache(Long userId) {
        if (userId == null) {
            log.warn("用户ID为空，跳过缓存清除");
            return;
        }

        log.info("开始清除用户{}的所有缓存", userId);

        // 1. 清除用户相关的工具缓存
        toolCacheManager.invalidate(null, userId);

        // 2. 清除用户语义缓存
        invalidateSemanticCacheByUser(userId);

        // 3. 清除用户通用缓存
        invalidateUserGeneralCache(userId);

        log.info("用户{}的缓存清除完成", userId);
    }

    /**
     * 清除指定工具的缓存
     *
     * @param toolName 工具名称
     */
    public void invalidateToolCache(String toolName) {
        if (toolName == null || toolName.trim().isEmpty()) {
            log.warn("工具名称为空，跳过缓存清除");
            return;
        }

        log.info("开始清除工具{}的缓存", toolName);

        // 清除工具缓存
        toolCacheManager.invalidate(toolName, null);

        // 清除工具相关的语义缓存
        invalidateSemanticCacheByTool(toolName);

        log.info("工具{}的缓存清除完成", toolName);
    }

    /**
     * 清除指定用户的工具缓存
     *
     * @param userId   用户ID
     * @param toolName 工具名称
     */
    public void invalidateUserToolCache(Long userId, String toolName) {
        if (userId == null || toolName == null) {
            log.warn("用户ID或工具名称为空，跳过缓存清除");
            return;
        }

        log.info("清除用户{}的工具{}缓存", userId, toolName);
        toolCacheManager.invalidate(toolName, userId);
    }

    /**
     * 清除指定用户的语义缓存
     */
    private void invalidateSemanticCacheByUser(Long userId) {
        try {
            String pattern = SEMANTIC_CACHE_PREFIX + userId + ":*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("已清除用户{}的语义缓存: {}条", userId, keys.size());
            }
        } catch (Exception e) {
            log.error("清除用户{}语义缓存失败: {}", userId, e.getMessage());
        }
    }

    /**
     * 清除指定工具的语义缓存
     */
    private void invalidateSemanticCacheByTool(String toolName) {
        // 语义缓存是基于消息内容的，无法直接按工具名称清除
        // 这里可以清除包含工具名称关键词的缓存
        try {
            // 工具缓存是按tool:name:hash格式存储的
            // 语义缓存需要按具体消息清除，这里记录日志提示
            log.debug("工具{}的语义缓存需要通过消息内容清除", toolName);
        } catch (Exception e) {
            log.error("清除工具{}语义缓存失败: {}", toolName, e.getMessage());
        }
    }

    /**
     * 清除用户通用缓存
     */
    private void invalidateUserGeneralCache(Long userId) {
        try {
            String pattern = USER_CACHE_PREFIX + userId + ":*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("已清除用户{}的通用缓存: {}条", userId, keys.size());
            }
        } catch (Exception e) {
            log.error("清除用户{}通用缓存失败: {}", userId, e.getMessage());
        }
    }

    /**
     * 清除所有工具缓存
     */
    public void invalidateAllToolCache() {
        log.info("开始清除所有工具缓存");
        try {
            String pattern = TOOL_CACHE_PREFIX + "*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("已清除所有工具缓存: {}条", keys.size());
            } else {
                log.info("没有需要清除的工具缓存");
            }
        } catch (Exception e) {
            log.error("清除所有工具缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 清除所有语义缓存
     */
    public void invalidateAllSemanticCache() {
        log.info("开始清除所有语义缓存");
        try {
            String pattern = SEMANTIC_CACHE_PREFIX + "*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("已清除所有语义缓存: {}条", keys.size());
            } else {
                log.info("没有需要清除的语义缓存");
            }
        } catch (Exception e) {
            log.error("清除所有语义缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 清除所有缓存
     */
    public void invalidateAllCache() {
        log.info("开始清除所有缓存");
        invalidateAllToolCache();
        invalidateAllSemanticCache();
        log.info("所有缓存清除完成");
    }

    /**
     * 获取缓存统计信息
     */
    public CacheStats getCacheStats() {
        CacheStats stats = new CacheStats();

        try {
            // 工具缓存数量
            Set<String> toolKeys = redisTemplate.keys(TOOL_CACHE_PREFIX + "*");
            stats.setToolCacheCount(toolKeys != null ? toolKeys.size() : 0);

            // 语义缓存数量
            Set<String> semanticKeys = redisTemplate.keys(SEMANTIC_CACHE_PREFIX + "*");
            stats.setSemanticCacheCount(semanticKeys != null ? semanticKeys.size() : 0);

            // 用户缓存数量
            Set<String> userKeys = redisTemplate.keys(USER_CACHE_PREFIX + "*");
            stats.setUserCacheCount(userKeys != null ? userKeys.size() : 0);

            // 总缓存数量
            stats.setTotalCacheCount(stats.getToolCacheCount() + stats.getSemanticCacheCount() + stats.getUserCacheCount());

        } catch (Exception e) {
            log.error("获取缓存统计失败: {}", e.getMessage());
        }

        return stats;
    }

    /**
     * 缓存统计
     */
    public static class CacheStats {
        private long toolCacheCount;
        private long semanticCacheCount;
        private long userCacheCount;
        private long totalCacheCount;

        public long getToolCacheCount() { return toolCacheCount; }
        public void setToolCacheCount(long toolCacheCount) { this.toolCacheCount = toolCacheCount; }
        public long getSemanticCacheCount() { return semanticCacheCount; }
        public void setSemanticCacheCount(long semanticCacheCount) { this.semanticCacheCount = semanticCacheCount; }
        public long getUserCacheCount() { return userCacheCount; }
        public void setUserCacheCount(long userCacheCount) { this.userCacheCount = userCacheCount; }
        public long getTotalCacheCount() { return totalCacheCount; }
        public void setTotalCacheCount(long totalCacheCount) { this.totalCacheCount = totalCacheCount; }
    }
}
