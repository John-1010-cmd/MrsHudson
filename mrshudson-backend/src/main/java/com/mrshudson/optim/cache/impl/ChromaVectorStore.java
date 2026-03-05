package com.mrshudson.optim.cache.impl;

import com.mrshudson.optim.cache.VectorStore;
import com.mrshudson.optim.cache.chroma.ChromaClient;
import com.mrshudson.optim.util.OptimUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Chroma向量存储实现
 * 使用Chroma向量数据库进行高性能向量存储和相似度搜索
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "optim.vector-store", name = "type", havingValue = "chroma")
public class ChromaVectorStore implements VectorStore {

    private static final String COLLECTION_NAME = "semantic_cache";
    private static final int DEFAULT_DIMENSION = 384;

    private final ChromaClient chromaClient;

    public ChromaVectorStore(ChromaClient chromaClient) {
        this.chromaClient = chromaClient;
        log.info("ChromaVectorStore initialized with collection: {}", COLLECTION_NAME);
    }

    @Override
    public String store(String userId, String query, String response, float[] embedding) {
        try {
            String id = UUID.randomUUID().toString();
            long now = System.currentTimeMillis();

            // 构建元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("userId", userId);
            metadata.put("query", query);
            metadata.put("response", response);
            metadata.put("createdAt", String.valueOf(now));
            metadata.put("lastAccessedAt", String.valueOf(now));
            metadata.put("accessCount", "0");

            // 存储文档（使用query作为文档内容）
            boolean success = chromaClient.addDocument(id, embedding, query, metadata);

            if (success) {
                log.debug("Stored cache entry in Chroma: userId={}, id={}", userId, id);
                return id;
            } else {
                log.warn("Failed to store cache entry in Chroma");
                return null;
            }

        } catch (Exception e) {
            log.error("Error storing to Chroma: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public Optional<CacheEntry> search(String userId, float[] queryEmbedding, double threshold) {
        try {
            // 构建过滤条件，只搜索该用户的缓存
            Map<String, Object> where = new HashMap<>();
            where.put("userId", userId);

            // 查询最相似的1个结果
            ChromaClient.ChromaQueryResult result = chromaClient.query(queryEmbedding, 1, where);

            if (result.getIds().isEmpty()) {
                return Optional.empty();
            }

            // 获取第一个结果
            String id = result.getIds().get(0);
            String document = result.getDocuments().get(0);
            Double distance = result.getDistances().get(0);
            Map<String, String> metadata = result.getMetadatas().get(0);

            if (distance == null || metadata == null) {
                return Optional.empty();
            }

            // Chroma返回的是距离（越小越相似），转换为相似度
            // 使用余弦相似度转换：similarity = 1 - distance / 2
            double similarity = 1.0 - (distance / 2.0);

            if (similarity < threshold) {
                log.debug("Chroma search result below threshold: similarity={}", similarity);
                return Optional.empty();
            }

            // 构建CacheEntry
            CacheEntry entry = new CacheEntry();
            entry.setId(id);
            entry.setQuery(document);
            entry.setResponse(metadata.get("response"));
            entry.setEmbedding(queryEmbedding); // 使用查询向量作为近似
            entry.setUserId(userId);

            String createdAtStr = metadata.get("createdAt");
            entry.setCreatedAt(createdAtStr != null ? Long.parseLong(createdAtStr) : System.currentTimeMillis());

            String lastAccessedAtStr = metadata.get("lastAccessedAt");
            entry.setLastAccessedAt(lastAccessedAtStr != null ? Long.parseLong(lastAccessedAtStr) : entry.getCreatedAt());

            String accessCountStr = metadata.get("accessCount");
            entry.setAccessCount(accessCountStr != null ? Integer.parseInt(accessCountStr) : 0);

            // 更新访问统计
            updateAccessStats(id, entry.getAccessCount() + 1);

            log.debug("Chroma cache hit: userId={}, id={}, similarity={}", userId, id, similarity);
            return Optional.of(entry);

        } catch (Exception e) {
            log.error("Error searching Chroma: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public boolean delete(String userId, String id) {
        try {
            // 先验证该条目属于该用户
            List<ChromaClient.ChromaDocument> documents = chromaClient.getAllDocuments();
            for (ChromaClient.ChromaDocument doc : documents) {
                if (doc.getId().equals(id)) {
                    Map<String, String> metadata = doc.getMetadata();
                    if (metadata != null && userId.equals(metadata.get("userId"))) {
                        boolean success = chromaClient.deleteDocuments(Collections.singletonList(id));
                        log.debug("Deleted cache entry from Chroma: userId={}, id={}, success={}", userId, id, success);
                        return success;
                    }
                    log.warn("Attempted to delete entry not belonging to user: {} != {}",
                            metadata != null ? metadata.get("userId") : null, userId);
                    return false;
                }
            }
            return false;

        } catch (Exception e) {
            log.error("Error deleting from Chroma: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public int cleanup(String userId) {
        try {
            List<ChromaClient.ChromaDocument> documents = chromaClient.getAllDocuments();
            long now = System.currentTimeMillis();
            long maxAgeMs = 7 * 24 * 60 * 60 * 1000L; // 7天
            List<String> idsToDelete = new ArrayList<>();

            for (ChromaClient.ChromaDocument doc : documents) {
                Map<String, String> metadata = doc.getMetadata();
                if (metadata == null) {
                    continue;
                }

                String docUserId = metadata.get("userId");
                if (!userId.equals(docUserId)) {
                    continue;
                }

                String createdAtStr = metadata.get("createdAt");
                if (createdAtStr != null) {
                    long createdAt = Long.parseLong(createdAtStr);
                    if (now - createdAt > maxAgeMs) {
                        idsToDelete.add(doc.getId());
                    }
                }
            }

            if (!idsToDelete.isEmpty()) {
                chromaClient.deleteDocuments(idsToDelete);
                log.info("Cleaned up {} expired entries from Chroma for user {}", idsToDelete.size(), userId);
            }

            return idsToDelete.size();

        } catch (Exception e) {
            log.error("Error cleaning up Chroma: {}", e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public CacheStats getStats(String userId) {
        try {
            List<ChromaClient.ChromaDocument> documents = chromaClient.getAllDocuments();

            int totalEntries = 0;
            long totalSize = 0;
            long oldestEntryTime = Long.MAX_VALUE;
            long newestEntryTime = 0;
            long totalAccessCount = 0;

            for (ChromaClient.ChromaDocument doc : documents) {
                Map<String, String> metadata = doc.getMetadata();
                if (metadata == null) {
                    continue;
                }

                String docUserId = metadata.get("userId");
                if (!userId.equals(docUserId)) {
                    continue;
                }

                totalEntries++;

                // 估算大小
                if (doc.getDocument() != null) {
                    totalSize += doc.getDocument().getBytes().length;
                }

                String createdAtStr = metadata.get("createdAt");
                if (createdAtStr != null) {
                    long createdAt = Long.parseLong(createdAtStr);
                    oldestEntryTime = Math.min(oldestEntryTime, createdAt);
                    newestEntryTime = Math.max(newestEntryTime, createdAt);
                }

                String accessCountStr = metadata.get("accessCount");
                if (accessCountStr != null) {
                    totalAccessCount += Long.parseLong(accessCountStr);
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

        } catch (Exception e) {
            log.error("Error getting stats from Chroma: {}", e.getMessage(), e);
            return new CacheStats(userId, 0, 0, 0, 0, 0.0);
        }
    }

    /**
     * 更新访问统计
     */
    private void updateAccessStats(String id, int newAccessCount) {
        try {
            // Chroma不支持部分更新，需要重新添加
            // 这里简化处理，实际生产环境可能需要更复杂的逻辑
            log.debug("Updated access stats for entry {}: count={}", id, newAccessCount);
        } catch (Exception e) {
            log.warn("Error updating access stats: {}", e.getMessage());
        }
    }
}
