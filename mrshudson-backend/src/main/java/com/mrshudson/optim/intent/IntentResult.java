package com.mrshudson.optim.intent;

import lombok.Builder;
import lombok.Data;

/**
 * 意图识别结果
 * 包含意图类型、置信度和候选列表
 */
@Data
@Builder
public class IntentResult {
    
    /**
     * 意图类型
     */
    private IntentType type;
    
    /**
     * 置信度 (0.0 - 1.0)
     */
    private double confidence;
    
    /**
     * 提取的参数
     */
    private java.util.Map<String, Object> extractedParams;
    
    /**
     * 候选意图列表（当置信度相近时）
     */
    private java.util.List<IntentType> candidates;
    
    /**
     * 判断是否是高置信度
     */
    public boolean isHighConfidence() {
        return confidence >= 0.8;
    }
    
    /**
     * 判断是否是模糊意图
     */
    public boolean isAmbiguous() {
        return candidates != null && !candidates.isEmpty();
    }
    
    /**
     * 判断是否需要澄清
     */
    public boolean needsClarification() {
        return !isHighConfidence() || isAmbiguous();
    }
    
    /**
     * 创建成功结果
     */
    public static IntentResult success(IntentType type, double confidence) {
        return IntentResult.builder()
            .type(type)
            .confidence(confidence)
            .build();
    }
    
    /**
     * 创建需要澄清的结果
     */
    public static IntentResult needsClarify(IntentType type, double confidence, java.util.List<IntentType> candidates) {
        return IntentResult.builder()
            .type(type)
            .confidence(confidence)
            .candidates(candidates)
            .build();
    }
    
    /**
     * 创建未知结果
     */
    public static IntentResult unknown() {
        return IntentResult.builder()
            .type(IntentType.UNKNOWN)
            .confidence(0.0)
            .build();
    }
}
