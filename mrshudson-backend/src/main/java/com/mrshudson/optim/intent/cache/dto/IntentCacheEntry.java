package com.mrshudson.optim.intent.cache.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Intent 缓存条目
 * 存储意图识别结果，用于 L1/L2/L3 三级缓存
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentCacheEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID (用于缓存隔离)
     */
    private Long userId;

    /**
     * 指纹 (MD5 of normalized input)
     */
    private String fingerprint;

    /**
     * 意图类型
     */
    private String intentType;

    /**
     * 置信度 (0.0-1.0)
     */
    private double confidence;

    /**
     * 是否需要澄清
     */
    private boolean needsClarification;

    /**
     * 备选意图列表
     */
    private List<IntentCandidate> candidates;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * TTL (秒)
     */
    private int ttlSeconds;

    /**
     * 访问次数 (用于 LRU)
     */
    private long accessCount;

    /**
     * 向量嵌入 (仅 L3 使用)
     */
    private float[] embedding;

    /**
     * 增加访问计数
     */
    public void incrementAccessCount() {
        this.accessCount++;
    }

    /**
     * 检查是否过期
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(createdAt.plusSeconds(ttlSeconds));
    }

    /**
     * 备选意图
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IntentCandidate implements Serializable {
        private static final long serialVersionUID = 1L;

        private String type;
        private double confidence;
    }
}
