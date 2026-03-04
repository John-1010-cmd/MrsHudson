package com.mrshudson.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI调用成本记录实体
 * 记录每次AI调用的成本信息，用于成本监控和统计
 */
@Data
@TableName("ai_cost_record")
public class AiCostRecord {

    /**
     * 记录ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会话ID（可选）
     */
    private Long conversationId;

    /**
     * 输入token数
     */
    private Integer inputTokens;

    /**
     * 输出token数
     */
    private Integer outputTokens;

    /**
     * 总token数（input + output）
     */
    @TableField(exist = false)
    private Integer totalTokens;

    /**
     * 成本（元）
     */
    private BigDecimal cost;

    /**
     * 是否命中缓存（0=未命中，1=命中）
     */
    private Integer cacheHit;

    /**
     * 使用的AI模型
     */
    private String model;

    /**
     * 调用类型：chat, tool_call, intent_extract, summary等
     */
    private String callType;

    /**
     * 意图路由层：rule, lightweight_ai, full_ai
     */
    private String routerLayer;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 获取总token数
     */
    public Integer getTotalTokens() {
        if (inputTokens == null) inputTokens = 0;
        if (outputTokens == null) outputTokens = 0;
        return inputTokens + outputTokens;
    }

    /**
     * 是否命中缓存
     */
    public boolean isCacheHit() {
        return cacheHit != null && cacheHit == 1;
    }
}
