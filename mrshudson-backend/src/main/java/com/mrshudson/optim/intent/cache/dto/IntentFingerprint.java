package com.mrshudson.optim.intent.cache.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 意图指纹
 * 归一化后的用户输入表示，用于缓存键生成
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentFingerprint implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 原始输入
     */
    private String rawInput;

    /**
     * 归一化后的输入 (具体日期替代相对时间)
     */
    private String normalizedInput;

    /**
     * 指纹哈希 (MD5)
     */
    private String fingerprintHash;

    /**
     * 用于时间归一化的具体日期
     */
    private LocalDate concreteDate;

    /**
     * 生成缓存键
     */
    public String generateCacheKey(Long userId) {
        return userId + ":" + fingerprintHash;
    }
}
