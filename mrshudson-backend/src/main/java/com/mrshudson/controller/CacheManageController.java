package com.mrshudson.controller;

import com.mrshudson.domain.dto.Result;
import com.mrshudson.optim.cache.SemanticCacheService;
import com.mrshudson.optim.cache.ToolCacheManager;
import com.mrshudson.optim.cost.CacheInvalidationService;
import com.mrshudson.optim.cost.CacheMetrics;
import com.mrshudson.util.JwtContext;
import com.mrshudson.util.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 缓存管理控制器（管理员接口）
 * 提供手动清理缓存和获取缓存统计的API接口
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/cache")
@RequiredArgsConstructor
public class CacheManageController {

    private final SemanticCacheService semanticCacheService;
    private final ToolCacheManager toolCacheManager;
    private final RedisTemplate<String, String> redisTemplate;
    private final JwtTokenUtil jwtTokenUtil;
    private final CacheInvalidationService cacheInvalidationService;
    private final CacheMetrics cacheMetrics;

    private static final String TOKEN_PREFIX = "Bearer ";

    /**
     * 清除语义缓存
     *
     * @param request HTTP请求
     * @return 操作结果
     */
    @PostMapping("/clear/semantic")
    public Result<Void> clearSemanticCache(HttpServletRequest request) {
        Long userId = extractUserIdFromRequest(request);

        // 检查管理员权限
        if (!isAdmin(userId)) {
            log.warn("[CacheManage] 非管理员用户尝试清除语义缓存: userId={}", userId);
            return Result.error(403, "需要管理员权限");
        }

        log.info("[CacheManage] 管理员清除语义缓存: userId={}", userId);

        try {
            // 清除当前用户的语义缓存
            int clearedCount = semanticCacheService.clearAll(userId);
            log.info("[CacheManage] 语义缓存已清除: count={}", clearedCount);

            return Result.success();
        } catch (Exception e) {
            log.error("[CacheManage] 清除语义缓存失败: error={}", e.getMessage(), e);
            return Result.error("清除语义缓存失败: " + e.getMessage());
        }
    }

    /**
     * 清除工具缓存
     *
     * @param request HTTP请求
     * @return 操作结果
     */
    @PostMapping("/clear/tool")
    public Result<Void> clearToolCache(HttpServletRequest request) {
        Long userId = extractUserIdFromRequest(request);

        // 检查管理员权限
        if (!isAdmin(userId)) {
            log.warn("[CacheManage] 非管理员用户尝试清除工具缓存: userId={}", userId);
            return Result.error(403, "需要管理员权限");
        }

        log.info("[CacheManage] 管理员清除工具缓存: userId={}", userId);

        try {
            // 清除所有工具缓存
            toolCacheManager.invalidate(null, null);
            log.info("[CacheManage] 工具缓存已清除");

            return Result.success();
        } catch (Exception e) {
            log.error("[CacheManage] 清除工具缓存失败: error={}", e.getMessage(), e);
            return Result.error("清除工具缓存失败: " + e.getMessage());
        }
    }

    /**
     * 清除所有缓存（语义缓存 + 工具缓存）
     *
     * @param request HTTP请求
     * @return 操作结果
     */
    @PostMapping("/clear/all")
    public Result<Map<String, Object>> clearAllCache(HttpServletRequest request) {
        Long userId = extractUserIdFromRequest(request);

        // 检查管理员权限
        if (!isAdmin(userId)) {
            log.warn("[CacheManage] 非管理员用户尝试清除所有缓存: userId={}", userId);
            return Result.error(403, "需要管理员权限");
        }

        log.info("[CacheManage] 管理员清除所有缓存: userId={}", userId);

        Map<String, Object> result = new HashMap<>();

        try {
            // 1. 清除语义缓存
            int semanticCleared = semanticCacheService.clearAll(userId);
            result.put("semanticCleared", semanticCleared);
            log.info("[CacheManage] 语义缓存已清除: count={}", semanticCleared);

            // 2. 清除工具缓存
            toolCacheManager.invalidate(null, null);
            result.put("toolCleared", true);
            log.info("[CacheManage] 工具缓存已清除");

            // 3. 清除其他Redis缓存（以特定前缀的key）
            int otherCleared = clearOtherCaches();
            result.put("otherCleared", otherCleared);
            log.info("[CacheManage] 其他缓存已清除: count={}", otherCleared);

            result.put("success", true);
            return Result.success(result);
        } catch (Exception e) {
            log.error("[CacheManage] 清除所有缓存失败: error={}", e.getMessage(), e);
            return Result.error("清除所有缓存失败: " + e.getMessage());
        }
    }

    /**
     * 获取缓存统计信息
     *
     * @param request HTTP请求
     * @return 缓存统计数据
     */
    @GetMapping("/stats")
    public Result<CacheStatsDTO> getCacheStats(HttpServletRequest request) {
        Long userId = extractUserIdFromRequest(request);

        // 检查管理员权限
        if (!isAdmin(userId)) {
            log.warn("[CacheManage] 非管理员用户尝试获取缓存统计: userId={}", userId);
            return Result.error(403, "需要管理员权限");
        }

        log.info("[CacheManage] 管理员获取缓存统计: userId={}", userId);

        try {
            CacheStatsDTO stats = new CacheStatsDTO();

            // 1. 获取语义缓存统计
            SemanticCacheService.CacheStats semanticStats = semanticCacheService.getStats(userId);
            stats.setSemanticCacheEntries(semanticStats.getTotalEntries());
            stats.setSemanticCacheHitRate(semanticStats.getHitRate());
            stats.setSemanticCacheSize(semanticStats.getTotalSize());

            // 2. 获取工具缓存统计（通过Redis扫描）
            Map<String, Object> toolStats = getToolCacheStats();
            stats.setToolCacheEntries((Integer) toolStats.getOrDefault("totalEntries", 0));
            stats.setToolCacheSize((Long) toolStats.getOrDefault("totalSize", 0L));

            // 3. 获取Redis整体统计
            Map<String, Object> redisStats = getRedisStats();
            stats.setRedisKeyCount((Integer) redisStats.getOrDefault("keyCount", 0));
            stats.setRedisMemoryUsage((String) redisStats.getOrDefault("memoryUsage", "unknown"));

            return Result.success(stats);
        } catch (Exception e) {
            log.error("[CacheManage] 获取缓存统计失败: error={}", e.getMessage(), e);
            return Result.error("获取缓存统计失败: " + e.getMessage());
        }
    }

    /**
     * 手动清除缓存（通用接口）
     *
     * @param request HTTP请求
     * @param cacheType 缓存类型: user, tool, semantic, all
     * @param userId 用户ID（可选）
     * @param toolName 工具名称（可选）
     * @return 操作结果
     */
    @PostMapping("/clear")
    public Result<Map<String, Object>> clearCache(
            HttpServletRequest request,
            @RequestParam(required = false) String cacheType,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String toolName) {

        Long currentUserId = extractUserIdFromRequest(request);

        // 检查管理员权限
        if (!isAdmin(currentUserId)) {
            log.warn("[CacheManage] 非管理员用户尝试清除缓存: userId={}", currentUserId);
            return Result.error(403, "需要管理员权限");
        }

        log.info("[CacheManage] 管理员清除缓存: cacheType={}, userId={}, toolName={}", cacheType, userId, toolName);

        Map<String, Object> result = new HashMap<>();

        try {
            switch (cacheType != null ? cacheType.toLowerCase() : "all") {
                case "user":
                    if (userId != null) {
                        cacheInvalidationService.invalidateUserCache(userId);
                        result.put("message", "用户缓存已清除");
                        result.put("userId", userId);
                    } else {
                        return Result.error("清除用户缓存需要指定userId");
                    }
                    break;

                case "tool":
                    if (toolName != null) {
                        cacheInvalidationService.invalidateToolCache(toolName);
                        result.put("message", "工具缓存已清除");
                        result.put("toolName", toolName);
                    } else {
                        cacheInvalidationService.invalidateAllToolCache();
                        result.put("message", "所有工具缓存已清除");
                    }
                    break;

                case "semantic":
                    cacheInvalidationService.invalidateAllSemanticCache();
                    result.put("message", "所有语义缓存已清除");
                    break;

                case "all":
                default:
                    cacheInvalidationService.invalidateAllCache();
                    result.put("message", "所有缓存已清除");
                    break;
            }

            result.put("success", true);
            result.put("cacheType", cacheType);
            return Result.success(result);
        } catch (Exception e) {
            log.error("[CacheManage] 清除缓存失败: error={}", e.getMessage(), e);
            return Result.error("清除缓存失败: " + e.getMessage());
        }
    }

    /**
     * 获取缓存指标统计（包含命中率等）
     *
     * @param request HTTP请求
     * @return 缓存指标统计
     */
    @GetMapping("/metrics")
    public Result<CacheMetrics.CacheMetricsSnapshot> getCacheMetrics(HttpServletRequest request) {
        Long userId = extractUserIdFromRequest(request);

        // 检查管理员权限
        if (!isAdmin(userId)) {
            log.warn("[CacheManage] 非管理员用户尝试获取缓存指标: userId={}", userId);
            return Result.error(403, "需要管理员权限");
        }

        log.info("[CacheManage] 管理员获取缓存指标: userId={}", userId);

        try {
            CacheMetrics.CacheMetricsSnapshot snapshot = cacheMetrics.getSnapshot();
            return Result.success(snapshot);
        } catch (Exception e) {
            log.error("[CacheManage] 获取缓存指标失败: error={}", e.getMessage(), e);
            return Result.error("获取缓存指标失败: " + e.getMessage());
        }
    }

    /**
     * 重置缓存指标统计
     *
     * @param request HTTP请求
     * @return 操作结果
     */
    @PostMapping("/metrics/reset")
    public Result<Void> resetCacheMetrics(HttpServletRequest request) {
        Long userId = extractUserIdFromRequest(request);

        // 检查管理员权限
        if (!isAdmin(userId)) {
            log.warn("[CacheManage] 非管理员用户尝试重置缓存指标: userId={}", userId);
            return Result.error(403, "需要管理员权限");
        }

        log.info("[CacheManage] 管理员重置缓存指标: userId={}", userId);

        try {
            cacheMetrics.reset();
            return Result.success();
        } catch (Exception e) {
            log.error("[CacheManage] 重置缓存指标失败: error={}", e.getMessage(), e);
            return Result.error("重置缓存指标失败: " + e.getMessage());
        }
    }

    /**
     * 从HTTP请求中提取用户ID
     *
     * @param request HTTP请求
     * @return 用户ID
     */
    private Long extractUserIdFromRequest(HttpServletRequest request) {
        String token = extractTokenFromRequest(request);
        if (StringUtils.hasText(token) && jwtTokenUtil.validateToken(token)) {
            return jwtTokenUtil.extractUserId(token);
        }
        return null;
    }

    /**
     * 从HTTP请求中提取Token
     *
     * @param request HTTP请求
     * @return Token字符串
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(TOKEN_PREFIX)) {
            return bearerToken.substring(TOKEN_PREFIX.length());
        }
        return null;
    }

    /**
     * 检查用户是否为管理员
     * 目前简单实现：userId为1的是管理员
     *
     * @param userId 用户ID
     * @return 是否为管理员
     */
    private boolean isAdmin(Long userId) {
        // TODO: 后续可以从数据库或配置中读取管理员列表
        return userId != null && userId == 1L;
    }

    /**
     * 清除其他类型的缓存
     *
     * @return 清除的key数量
     */
    private int clearOtherCaches() {
        int count = 0;

        // 清除成本统计相关缓存
        try {
            Set<String> costKeys = redisTemplate.keys("cost:*");
            if (costKeys != null && !costKeys.isEmpty()) {
                redisTemplate.delete(costKeys);
                count += costKeys.size();
            }
        } catch (Exception e) {
            log.warn("[CacheManage] 清除成本统计缓存失败: {}", e.getMessage());
        }

        // 清除意图路由相关缓存
        try {
            Set<String> intentKeys = redisTemplate.keys("intent:*");
            if (intentKeys != null && !intentKeys.isEmpty()) {
                redisTemplate.delete(intentKeys);
                count += intentKeys.size();
            }
        } catch (Exception e) {
            log.warn("[CacheManage] 清除意图路由缓存失败: {}", e.getMessage());
        }

        return count;
    }

    /**
     * 获取工具缓存统计
     *
     * @return 统计信息Map
     */
    private Map<String, Object> getToolCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        int totalEntries = 0;
        long totalSize = 0L;

        try {
            Set<String> toolKeys = redisTemplate.keys("tool:*");
            if (toolKeys != null) {
                totalEntries = toolKeys.size();
                // 估算大小（每个key大概1KB）
                totalSize = totalEntries * 1024L;
            }
        } catch (Exception e) {
            log.warn("[CacheManage] 获取工具缓存统计失败: {}", e.getMessage());
        }

        stats.put("totalEntries", totalEntries);
        stats.put("totalSize", totalSize);
        return stats;
    }

    /**
     * 获取Redis整体统计
     *
     * @return 统计信息Map
     */
    private Map<String, Object> getRedisStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 获取key总数（只统计应用相关的key）
            Set<String> allKeys = redisTemplate.keys("*");
            stats.put("keyCount", allKeys != null ? allKeys.size() : 0);
        } catch (Exception e) {
            log.warn("[CacheManage] 获取Redis key统计失败: {}", e.getMessage());
            stats.put("keyCount", 0);
        }

        // 内存使用信息需要通过Redis Connection获取，这里简化处理
        stats.put("memoryUsage", "unknown");

        return stats;
    }

    /**
     * 缓存统计DTO
     */
    public static class CacheStatsDTO {
        // 语义缓存统计
        private int semanticCacheEntries;
        private double semanticCacheHitRate;
        private long semanticCacheSize;

        // 工具缓存统计
        private int toolCacheEntries;
        private long toolCacheSize;

        // Redis整体统计
        private int redisKeyCount;
        private String redisMemoryUsage;

        // Getters and Setters
        public int getSemanticCacheEntries() {
            return semanticCacheEntries;
        }

        public void setSemanticCacheEntries(int semanticCacheEntries) {
            this.semanticCacheEntries = semanticCacheEntries;
        }

        public double getSemanticCacheHitRate() {
            return semanticCacheHitRate;
        }

        public void setSemanticCacheHitRate(double semanticCacheHitRate) {
            this.semanticCacheHitRate = semanticCacheHitRate;
        }

        public long getSemanticCacheSize() {
            return semanticCacheSize;
        }

        public void setSemanticCacheSize(long semanticCacheSize) {
            this.semanticCacheSize = semanticCacheSize;
        }

        public int getToolCacheEntries() {
            return toolCacheEntries;
        }

        public void setToolCacheEntries(int toolCacheEntries) {
            this.toolCacheEntries = toolCacheEntries;
        }

        public long getToolCacheSize() {
            return toolCacheSize;
        }

        public void setToolCacheSize(long toolCacheSize) {
            this.toolCacheSize = toolCacheSize;
        }

        public int getRedisKeyCount() {
            return redisKeyCount;
        }

        public void setRedisKeyCount(int redisKeyCount) {
            this.redisKeyCount = redisKeyCount;
        }

        public String getRedisMemoryUsage() {
            return redisMemoryUsage;
        }

        public void setRedisMemoryUsage(String redisMemoryUsage) {
            this.redisMemoryUsage = redisMemoryUsage;
        }
    }
}
