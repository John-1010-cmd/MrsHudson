package com.mrshudson.optim.cost;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 缓存指标收集器
 * 记录缓存命中/未命中统计，支持按缓存类型分组统计
 */
@Slf4j
@Component
public class CacheMetrics {

    // 总命中/未命中计数
    private final AtomicLong totalHits = new AtomicLong(0);
    private final AtomicLong totalMisses = new AtomicLong(0);

    // 按缓存类型统计
    private final Map<String, AtomicLong> hitsByType = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> missesByType = new ConcurrentHashMap<>();

    // 用户级统计
    private final Map<Long, AtomicLong> hitsByUser = new ConcurrentHashMap<>();
    private final Map<Long, AtomicLong> missesByUser = new ConcurrentHashMap<>();

    /**
     * 记录缓存命中
     */
    public void recordCacheHit() {
        totalHits.incrementAndGet();
    }

    /**
     * 记录缓存命中（带类型）
     */
    public void recordCacheHit(String cacheType) {
        totalHits.incrementAndGet();
        hitsByType.computeIfAbsent(cacheType, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * 记录缓存命中（带类型和用户）
     */
    public void recordCacheHit(String cacheType, Long userId) {
        recordCacheHit(cacheType);
        if (userId != null) {
            hitsByUser.computeIfAbsent(userId, k -> new AtomicLong(0)).incrementAndGet();
        }
    }

    /**
     * 记录缓存未命中
     */
    public void recordCacheMiss() {
        totalMisses.incrementAndGet();
    }

    /**
     * 记录缓存未命中（带类型）
     */
    public void recordCacheMiss(String cacheType) {
        totalMisses.incrementAndGet();
        missesByType.computeIfAbsent(cacheType, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * 记录缓存未命中（带类型和用户）
     */
    public void recordCacheMiss(String cacheType, Long userId) {
        recordCacheMiss(cacheType);
        if (userId != null) {
            missesByUser.computeIfAbsent(userId, k -> new AtomicLong(0)).incrementAndGet();
        }
    }

    /**
     * 获取总体命中率
     */
    public double getHitRate() {
        long hits = totalHits.get();
        long misses = totalMisses.get();
        long total = hits + misses;
        if (total == 0) {
            return 0.0;
        }
        return (hits * 100.0) / total;
    }

    /**
     * 获取总体命中率百分比（保留2位小数）
     */
    public String getHitRatePercent() {
        return String.format("%.2f%%", getHitRate());
    }

    /**
     * 获取命中数
     */
    public long getTotalHits() {
        return totalHits.get();
    }

    /**
     * 获取未命中数
     */
    public long getTotalMisses() {
        return totalMisses.get();
    }

    /**
     * 获取总请求数
     */
    public long getTotalRequests() {
        return totalHits.get() + totalMisses.get();
    }

    /**
     * 获取指定类型的命中率
     */
    public double getHitRateByType(String cacheType) {
        long hits = hitsByType.getOrDefault(cacheType, new AtomicLong(0)).get();
        long misses = missesByType.getOrDefault(cacheType, new AtomicLong(0)).get();
        long total = hits + misses;
        if (total == 0) {
            return 0.0;
        }
        return (hits * 100.0) / total;
    }

    /**
     * 获取指定类型的命中数
     */
    public long getHitsByType(String cacheType) {
        return hitsByType.getOrDefault(cacheType, new AtomicLong(0)).get();
    }

    /**
     * 获取指定类型的未命中数
     */
    public long getMissesByType(String cacheType) {
        return missesByType.getOrDefault(cacheType, new AtomicLong(0)).get();
    }

    /**
     * 获取所有缓存类型统计
     */
    public Map<String, CacheTypeStats> getStatsByType() {
        Map<String, CacheTypeStats> result = new ConcurrentHashMap<>();
        hitsByType.keySet().forEach(type -> {
            CacheTypeStats stats = new CacheTypeStats();
            stats.setCacheType(type);
            stats.setHits(getHitsByType(type));
            stats.setMisses(getMissesByType(type));
            stats.setHitRate(getHitRateByType(type));
            result.put(type, stats);
        });
        return result;
    }

    /**
     * 获取指定用户的命中率
     */
    public double getHitRateByUser(Long userId) {
        if (userId == null) {
            return 0.0;
        }
        long hits = hitsByUser.getOrDefault(userId, new AtomicLong(0)).get();
        long misses = missesByUser.getOrDefault(userId, new AtomicLong(0)).get();
        long total = hits + misses;
        if (total == 0) {
            return 0.0;
        }
        return (hits * 100.0) / total;
    }

    /**
     * 获取所有用户统计（按命中率排序）
     */
    public Map<Long, UserCacheStats> getStatsByUser() {
        Map<Long, UserCacheStats> result = new ConcurrentHashMap<>();
        hitsByUser.keySet().forEach(userId -> {
            UserCacheStats stats = new UserCacheStats();
            stats.setUserId(userId);
            stats.setHits(hitsByUser.get(userId).get());
            stats.setMisses(missesByUser.getOrDefault(userId, new AtomicLong(0)).get());
            stats.setHitRate(getHitRateByUser(userId));
            result.put(userId, stats);
        });
        return result;
    }

    /**
     * 获取完整统计快照
     */
    public CacheMetricsSnapshot getSnapshot() {
        CacheMetricsSnapshot snapshot = new CacheMetricsSnapshot();
        snapshot.setTotalHits(getTotalHits());
        snapshot.setTotalMisses(getTotalMisses());
        snapshot.setTotalRequests(getTotalRequests());
        snapshot.setHitRate(getHitRate());
        snapshot.setHitRatePercent(getHitRatePercent());
        snapshot.setStatsByType(getStatsByType());
        snapshot.setStatsByUser(getStatsByUser());
        return snapshot;
    }

    /**
     * 重置所有统计
     */
    public void reset() {
        totalHits.set(0);
        totalMisses.set(0);
        hitsByType.clear();
        missesByType.clear();
        hitsByUser.clear();
        missesByUser.clear();
        log.info("Cache metrics reset");
    }

    /**
     * 缓存类型统计
     */
    public static class CacheTypeStats {
        private String cacheType;
        private long hits;
        private long misses;
        private double hitRate;

        public String getCacheType() { return cacheType; }
        public void setCacheType(String cacheType) { this.cacheType = cacheType; }
        public long getHits() { return hits; }
        public void setHits(long hits) { this.hits = hits; }
        public long getMisses() { return misses; }
        public void setMisses(long misses) { this.misses = misses; }
        public double getHitRate() { return hitRate; }
        public void setHitRate(double hitRate) { this.hitRate = hitRate; }
    }

    /**
     * 用户级统计
     */
    public static class UserCacheStats {
        private Long userId;
        private long hits;
        private long misses;
        private double hitRate;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public long getHits() { return hits; }
        public void setHits(long hits) { this.hits = hits; }
        public long getMisses() { return misses; }
        public void setMisses(long misses) { this.misses = misses; }
        public double getHitRate() { return hitRate; }
        public void setHitRate(double hitRate) { this.hitRate = hitRate; }
    }

    /**
     * 完整统计快照
     */
    public static class CacheMetricsSnapshot {
        private long totalHits;
        private long totalMisses;
        private long totalRequests;
        private double hitRate;
        private String hitRatePercent;
        private Map<String, CacheTypeStats> statsByType;
        private Map<Long, UserCacheStats> statsByUser;

        public long getTotalHits() { return totalHits; }
        public void setTotalHits(long totalHits) { this.totalHits = totalHits; }
        public long getTotalMisses() { return totalMisses; }
        public void setTotalMisses(long totalMisses) { this.totalMisses = totalMisses; }
        public long getTotalRequests() { return totalRequests; }
        public void setTotalRequests(long totalRequests) { this.totalRequests = totalRequests; }
        public double getHitRate() { return hitRate; }
        public void setHitRate(double hitRate) { this.hitRate = hitRate; }
        public String getHitRatePercent() { return hitRatePercent; }
        public void setHitRatePercent(String hitRatePercent) { this.hitRatePercent = hitRatePercent; }
        public Map<String, CacheTypeStats> getStatsByType() { return statsByType; }
        public void setStatsByType(Map<String, CacheTypeStats> statsByType) { this.statsByType = statsByType; }
        public Map<Long, UserCacheStats> getStatsByUser() { return statsByUser; }
        public void setStatsByUser(Map<Long, UserCacheStats> statsByUser) { this.statsByUser = statsByUser; }
    }
}
