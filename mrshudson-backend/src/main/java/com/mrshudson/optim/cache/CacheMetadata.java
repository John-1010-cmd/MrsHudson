package com.mrshudson.optim.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 缓存条目元数据
 * 存储与缓存条目相关的统计和成本信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheMetadata {

    /**
     * 输入token数量
     */
    private Integer inputTokens;

    /**
     * 输出token数量
     */
    private Integer outputTokens;

    /**
     * 总token数量
     */
    private Integer totalTokens;

    /**
     * 预估成本（美元）
     */
    private Double cost;

    /**
     * 使用的AI模型
     */
    private String model;

    /**
     * 响应生成时间（毫秒）
     */
    private Long responseTimeMs;

    /**
     * 创建缓存条目的意图类型
     */
    private String intentType;

    /**
     * 计算总token数
     */
    public Integer calculateTotalTokens() {
        if (this.totalTokens != null) {
            return this.totalTokens;
        }
        int input = this.inputTokens != null ? this.inputTokens : 0;
        int output = this.outputTokens != null ? this.outputTokens : 0;
        return input + output;
    }

    /**
     * 创建元数据（简化版）
     *
     * @param inputTokens  输入token数
     * @param outputTokens 输出token数
     * @param model        模型名称
     * @return 新的CacheMetadata实例
     */
    public static CacheMetadata of(Integer inputTokens, Integer outputTokens, String model) {
        return CacheMetadata.builder()
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(inputTokens + outputTokens)
                .model(model)
                .build();
    }

    /**
     * 创建元数据（带成本）
     *
     * @param inputTokens  输入token数
     * @param outputTokens 输出token数
     * @param model        模型名称
     * @param cost         成本
     * @return 新的CacheMetadata实例
     */
    public static CacheMetadata of(Integer inputTokens, Integer outputTokens, String model, Double cost) {
        return CacheMetadata.builder()
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(inputTokens + outputTokens)
                .model(model)
                .cost(cost)
                .build();
    }

    /**
     * 创建空元数据
     *
     * @return 空的CacheMetadata实例
     */
    public static CacheMetadata empty() {
        return CacheMetadata.builder()
                .inputTokens(0)
                .outputTokens(0)
                .totalTokens(0)
                .cost(0.0)
                .build();
    }
}
