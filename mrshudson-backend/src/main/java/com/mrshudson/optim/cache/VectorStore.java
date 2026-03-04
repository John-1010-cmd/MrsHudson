package com.mrshudson.optim.cache;

import java.util.List;
import java.util.Optional;

/**
 * 向量存储通用接口
 * 定义向量存储的抽象接口，支持多种后端实现（Redis、Chroma、Milvus等）
 */
public interface VectorStore {

    /**
     * 存储向量数据
     *
     * @param userId    用户ID（用于用户隔离）
     * @param query     查询文本
     * @param response  响应文本
     * @param embedding 向量嵌入
     * @return 存储的条目ID
     */
    String store(String userId, String query, String response, float[] embedding);

    /**
     * 搜索相似向量
     *
     * @param userId         用户ID
     * @param queryEmbedding 查询向量
     * @param threshold      相似度阈值（0-1之间，如0.92）
     * @return 最匹配的缓存条目
     */
    Optional<CacheEntry> search(String userId, float[] queryEmbedding, double threshold);

    /**
     * 删除指定缓存条目
     *
     * @param userId 用户ID
     * @param id     条目ID
     * @return 是否删除成功
     */
    boolean delete(String userId, String id);

    /**
     * 清理用户的过期缓存数据
     *
     * @param userId 用户ID
     * @return 清理的条目数量
     */
    int cleanup(String userId);

    /**
     * 获取用户缓存统计信息
     *
     * @param userId 用户ID
     * @return 统计信息对象
     */
    CacheStats getStats(String userId);

    /**
     * 缓存条目类
     */
    class CacheEntry {
        private String id;
        private String query;
        private String response;
        private float[] embedding;
        private String userId;
        private long createdAt;
        private long lastAccessedAt;
        private int accessCount;

        public CacheEntry() {
        }

        public CacheEntry(String id, String query, String response, float[] embedding,
                         String userId, long createdAt) {
            this.id = id;
            this.query = query;
            this.response = response;
            this.embedding = embedding;
            this.userId = userId;
            this.createdAt = createdAt;
            this.lastAccessedAt = createdAt;
            this.accessCount = 0;
        }

        // Getters and Setters
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public String getResponse() {
            return response;
        }

        public void setResponse(String response) {
            this.response = response;
        }

        public float[] getEmbedding() {
            return embedding;
        }

        public void setEmbedding(float[] embedding) {
            this.embedding = embedding;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(long createdAt) {
            this.createdAt = createdAt;
        }

        public long getLastAccessedAt() {
            return lastAccessedAt;
        }

        public void setLastAccessedAt(long lastAccessedAt) {
            this.lastAccessedAt = lastAccessedAt;
        }

        public int getAccessCount() {
            return accessCount;
        }

        public void setAccessCount(int accessCount) {
            this.accessCount = accessCount;
        }

        /**
         * 记录访问
         */
        public void recordAccess() {
            this.lastAccessedAt = System.currentTimeMillis();
            this.accessCount++;
        }
    }

    /**
     * 缓存统计信息类
     */
    class CacheStats {
        private String userId;
        private int totalEntries;
        private long totalSize;
        private long oldestEntryTime;
        private long newestEntryTime;
        private double avgAccessCount;

        public CacheStats() {
        }

        public CacheStats(String userId, int totalEntries, long totalSize,
                         long oldestEntryTime, long newestEntryTime, double avgAccessCount) {
            this.userId = userId;
            this.totalEntries = totalEntries;
            this.totalSize = totalSize;
            this.oldestEntryTime = oldestEntryTime;
            this.newestEntryTime = newestEntryTime;
            this.avgAccessCount = avgAccessCount;
        }

        // Getters and Setters
        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public int getTotalEntries() {
            return totalEntries;
        }

        public void setTotalEntries(int totalEntries) {
            this.totalEntries = totalEntries;
        }

        public long getTotalSize() {
            return totalSize;
        }

        public void setTotalSize(long totalSize) {
            this.totalSize = totalSize;
        }

        public long getOldestEntryTime() {
            return oldestEntryTime;
        }

        public void setOldestEntryTime(long oldestEntryTime) {
            this.oldestEntryTime = oldestEntryTime;
        }

        public long getNewestEntryTime() {
            return newestEntryTime;
        }

        public void setNewestEntryTime(long newestEntryTime) {
            this.newestEntryTime = newestEntryTime;
        }

        public double getAvgAccessCount() {
            return avgAccessCount;
        }

        public void setAvgAccessCount(double avgAccessCount) {
            this.avgAccessCount = avgAccessCount;
        }
    }
}
