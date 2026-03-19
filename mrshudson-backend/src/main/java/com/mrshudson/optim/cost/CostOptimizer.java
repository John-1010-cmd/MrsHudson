package com.mrshudson.optim.cost;

/**
 * 成本优化器接口
 * 通过缓存和智能路由降低 AI 调用成本
 */
public interface CostOptimizer {

    /**
     * 检查是否有缓存命中
     *
     * @param userId 用户ID
     * @param message 用户消息
     * @return 缓存结果（可能为 null 表示未命中）
     */
    CachedResult checkCache(Long userId, String message);

    /**
     * 判断是否使用小模型
     *
     * @param message 用户消息
     * @return boolean 是否使用小模型
     */
    boolean shouldUseSmallModel(String message);

    /**
     * 保存缓存
     *
     * @param userId 用户ID
     * @param message 用户消息
     * @param response 响应内容
     */
    void saveCache(Long userId, String message, String response);

    /**
     * 清除用户的所有缓存
     *
     * @param userId 用户ID
     */
    void clearUserCache(Long userId);

    /**
     * 获取缓存命中率统计
     *
     * @return 命中率 (0.0 - 1.0)
     */
    double getCacheHitRate();
}
