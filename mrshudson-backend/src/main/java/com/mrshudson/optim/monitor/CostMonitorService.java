package com.mrshudson.optim.monitor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 成本监控服务接口
 * 用于记录AI调用成本、缓存命中统计和告警检查
 */
public interface CostMonitorService {

    /**
     * 记录AI调用成本
     *
     * @param userId         用户ID
     * @param conversationId 会话ID（可选）
     * @param inputTokens    输入token数
     * @param outputTokens   输出token数
     * @param cost           成本（元）
     * @param model          使用的AI模型
     * @param callType       调用类型（chat, tool_call, intent_extract, summary等）
     * @param routerLayer    意图路由层（rule, lightweight_ai, full_ai）
     */
    void recordAiCall(Long userId, Long conversationId,
                      Integer inputTokens, Integer outputTokens,
                      BigDecimal cost, String model,
                      String callType, String routerLayer);

    /**
     * 记录缓存命中
     *
     * @param userId         用户ID
     * @param conversationId 会话ID（可选）
     * @param model          使用的AI模型
     * @param callType       调用类型
     */
    void recordCacheHit(Long userId, Long conversationId,
                        String model, String callType);

    /**
     * 获取指定时间范围内的统计信息
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 统计信息Map
     */
    Map<String, Object> getStatistics(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取用户指定时间范围内的统计信息
     *
     * @param userId    用户ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 统计信息Map
     */
    Map<String, Object> getUserStatistics(Long userId,
                                          LocalDateTime startTime,
                                          LocalDateTime endTime);

    /**
     * 检查并触发告警
     * 检查当日成本是否超过阈值，如果超过则记录告警
     *
     * @param userId 用户ID（为null时检查系统总成本）
     */
    void checkAndAlert(Long userId);

    /**
     * 获取今日成本
     *
     * @param userId 用户ID（为null时获取系统总成本）
     * @return 今日成本
     */
    BigDecimal getTodayCost(Long userId);
}
