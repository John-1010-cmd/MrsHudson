package com.mrshudson.optim.cost;

import lombok.Builder;
import lombok.Data;

/**
 * 缓存结果模型
 */
@Data
@Builder
public class CachedResult {
    
    /**
     * 是否命中缓存
     */
    private boolean hit;
    
    /**
     * 缓存响应内容
     */
    private String response;
    
    /**
     * 匹配类型
     */
    private MatchType matchType;
    
    /**
     * 匹配类型枚举
     */
    public enum MatchType {
        /**
         * 精确匹配
         */
        EXACT,
        /**
         * 语义相似匹配
         */
        SEMANTIC
    }
    
    /**
     * 创建缓存未命中结果
     */
    public static CachedResult miss() {
        return CachedResult.builder()
            .hit(false)
            .build();
    }
    
    /**
     * 创建缓存命中结果
     *
     * @param response 缓存的响应内容
     * @param type 匹配类型
     */
    public static CachedResult hit(String response, MatchType type) {
        return CachedResult.builder()
            .hit(true)
            .response(response)
            .matchType(type)
            .build();
    }
}
