package com.mrshudson.optim.token;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Token 消耗统计模型
 */
@Data
@Builder
public class TokenUsage {
    /**
     * 输入 token 数量
     */
    private Integer inputTokens;
    
    /**
     * 输出 token 数量
     */
    private Integer outputTokens;
    
    /**
     * 响应耗时（毫秒）
     */
    private Long duration;
    
    /**
     * 使用的模型
     */
    private String model;
    
    /**
     * 时间戳
     */
    private LocalDateTime timestamp;
    
    /**
     * 获取总 token 数量
     */
    public int getTotalTokens() {
        return (inputTokens != null ? inputTokens : 0) + (outputTokens != null ? outputTokens : 0);
    }
    
    /**
     * 计算预估成本
     * @param inputPrice 输入价格（元/1M tokens）
     * @param outputPrice 输出价格（元/1M tokens）
     */
    public BigDecimal calculateCost(BigDecimal inputPrice, BigDecimal outputPrice) {
        BigDecimal inputCost = BigDecimal.ZERO;
        BigDecimal outputCost = BigDecimal.ZERO;
        
        if (inputTokens != null && inputTokens > 0) {
            inputCost = BigDecimal.valueOf(inputTokens)
                .multiply(inputPrice)
                .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
        }
        
        if (outputTokens != null && outputTokens > 0) {
            outputCost = BigDecimal.valueOf(outputTokens)
                .multiply(outputPrice)
                .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
        }
        
        return inputCost.add(outputCost);
    }
}
