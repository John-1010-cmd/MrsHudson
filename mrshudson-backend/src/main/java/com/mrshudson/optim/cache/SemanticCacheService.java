package com.mrshudson.optim.cache;

import java.util.Optional;

/**
 * 语义缓存服务接口
 * 提供基于语义相似度的查询缓存功能
 */
public interface SemanticCacheService {

    /**
     * 从缓存中获取相似查询的响应
     * 如果找到相似度超过阈值的缓存，返回缓存的响应
     *
     * @param userId 用户ID（用于用户隔离）
     * @param query  查询文本
     * @return 缓存的响应，如果没有找到则返回empty
     */
    Optional<String> get(Long userId, String query);

    /**
     * 将查询和响应存入缓存
     *
     * @param userId   用户ID
     * @param query    查询文本
     * @param response AI响应内容
     * @return 缓存条目的ID
     */
    String put(Long userId, String query, String response);

    /**
     * 将查询和响应存入缓存（带元数据）
     *
     * @param userId   用户ID
     * @param query    查询文本
     * @param response AI响应内容
     * @param metadata 缓存元数据
     * @return 缓存条目的ID
     */
    String put(Long userId, String query, String response, CacheMetadata metadata);

    /**
     * 清理过期缓存数据
     *
     * @param userId 用户ID
     * @return 清理的条目数量
     */
    int cleanup(Long userId);

    /**
     * 获取缓存统计信息
     *
     * @param userId 用户ID
     * @return 缓存统计信息
     */
    CacheStats getStats(Long userId);

    /**
     * 删除指定缓存条目
     *
     * @param userId 用户ID
     * @param id     缓存条目ID
     * @return 是否删除成功
     */
    boolean delete(Long userId, String id);

    /**
     * 清空用户的所有缓存
     *
     * @param userId 用户ID
     * @return 清空的条目数量
     */
    int clearAll(Long userId);

    /**
     * 检查缓存是否包含相似查询
     *
     * @param userId 用户ID
     * @param query  查询文本
     * @return 如果存在相似缓存返回true
     */
    boolean contains(Long userId, String query);

    /**
     * 获取缓存统计信息（内部类）
     */
    class CacheStats {
        private final Long userId;
        private final int totalEntries;
        private final double hitRate;
        private final long totalSize;

        public CacheStats(Long userId, int totalEntries, double hitRate, long totalSize) {
            this.userId = userId;
            this.totalEntries = totalEntries;
            this.hitRate = hitRate;
            this.totalSize = totalSize;
        }

        public Long getUserId() {
            return userId;
        }

        public int getTotalEntries() {
            return totalEntries;
        }

        public double getHitRate() {
            return hitRate;
        }

        public long getTotalSize() {
            return totalSize;
        }
    }
}
