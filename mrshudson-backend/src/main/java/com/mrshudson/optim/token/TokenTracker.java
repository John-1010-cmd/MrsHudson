package com.mrshudson.optim.token;

import java.math.BigDecimal;

/**
 * Token 追踪器接口
 * 追踪和统计每次 AI 调用的 token 消耗
 */
public interface TokenTracker {

    /**
     * 开始追踪一次请求
     *
     * @return 追踪 ID
     */
    String startTracking();

    /**
     * 记录输入 token
     *
     * @param trackingId 追踪 ID
     * @param tokens 输入 token 数量
     */
    void recordInputTokens(String trackingId, int tokens);

    /**
     * 记录输出 token
     *
     * @param trackingId 追踪 ID
     * @param tokens 输出 token 数量
     */
    void recordOutputTokens(String trackingId, int tokens);

    /**
     * 记录响应耗时
     *
     * @param trackingId 追踪 ID
     * @param durationMs 耗时（毫秒）
     */
    void recordDuration(String trackingId, long durationMs);

    /**
     * 获取统计结果
     *
     * @param trackingId 追踪 ID
     * @return Token 使用统计
     */
    TokenUsage getUsage(String trackingId);

    /**
     * 生成统计消息
     *
     * @param usage Token 使用统计
     * @return 格式化的统计字符串
     */
    String formatStatistics(TokenUsage usage);

    /**
     * 计算预估成本
     *
     * @param usage Token 使用统计
     * @return 预估成本（元）
     */
    BigDecimal calculateCost(TokenUsage usage);

    /**
     * 结束追踪并获取结果
     *
     * @param trackingId 追踪 ID
     * @return Token 使用统计
     */
    default TokenUsage endTracking(String trackingId) {
        return getUsage(trackingId);
    }
}
