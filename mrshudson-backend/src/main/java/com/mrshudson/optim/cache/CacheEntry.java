package com.mrshudson.optim.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 语义缓存条目
 * 存储查询、响应、向量嵌入及元数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheEntry {

    /**
     * 缓存条目唯一ID
     */
    private String id;

    /**
     * 原始查询文本
     */
    private String query;

    /**
     * AI响应内容
     */
    private String response;

    /**
     * 查询的向量嵌入表示
     */
    private float[] embedding;

    /**
     * 用户ID（用于用户隔离）
     */
    private Long userId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 最后访问时间
     */
    private LocalDateTime lastAccessedAt;

    /**
     * 访问次数
     */
    @Builder.Default
    private Integer accessCount = 1;

    /**
     * 缓存元数据（tokens、cost等）
     */
    private CacheMetadata metadata;

    /**
     * 增加访问计数
     */
    public void incrementAccessCount() {
        this.accessCount = this.accessCount + 1;
    }

    /**
     * 更新最后访问时间
     */
    public void updateLastAccessedAt() {
        this.lastAccessedAt = LocalDateTime.now();
    }

    /**
     * 创建新的缓存条目
     *
     * @param id        唯一ID
     * @param query     查询文本
     * @param response  响应内容
     * @param embedding 向量嵌入
     * @param userId    用户ID
     * @param metadata  元数据
     * @return 新的CacheEntry实例
     */
    public static CacheEntry create(String id, String query, String response,
                                    float[] embedding, Long userId, CacheMetadata metadata) {
        LocalDateTime now = LocalDateTime.now();
        return CacheEntry.builder()
                .id(id)
                .query(query)
                .response(response)
                .embedding(embedding)
                .userId(userId)
                .createdAt(now)
                .lastAccessedAt(now)
                .accessCount(1)
                .metadata(metadata)
                .build();
    }

    /**
     * 创建简化版缓存条目（无元数据）
     *
     * @param id        唯一ID
     * @param query     查询文本
     * @param response  响应内容
     * @param embedding 向量嵌入
     * @param userId    用户ID
     * @return 新的CacheEntry实例
     */
    public static CacheEntry create(String id, String query, String response,
                                    float[] embedding, Long userId) {
        return create(id, query, response, embedding, userId, null);
    }
}
