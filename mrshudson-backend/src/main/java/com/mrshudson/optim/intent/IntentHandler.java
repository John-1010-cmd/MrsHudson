package com.mrshudson.optim.intent;

import java.util.Map;

/**
 * 意图处理器接口
 * 每种意图类型对应一个处理器实现
 */
public interface IntentHandler {

    /**
     * 获取处理的意图类型
     *
     * @return 意图类型
     */
    IntentType getIntentType();

    /**
     * 处理意图
     *
     * @param userId     用户ID
     * @param query      用户查询
     * @param parameters 提取的参数
     * @return 路由结果
     */
    RouteResult handle(Long userId, String query, Map<String, Object> parameters);

    /**
     * 检查是否可以处理该查询
     *
     * @param query 用户查询
     * @return 置信度 (0-1)
     */
    default double canHandle(String query) {
        if (query == null || query.isEmpty()) {
            return 0.0;
        }
        String lowerQuery = query.toLowerCase();
        int matchCount = 0;
        for (String keyword : getIntentType().getKeywords()) {
            if (lowerQuery.contains(keyword.toLowerCase())) {
                matchCount++;
            }
        }
        if (matchCount == 0) {
            return 0.0;
        }
        // 根据匹配关键词数量和查询长度计算置信度
        double baseConfidence = getIntentType().getBaseConfidenceThreshold();
        return Math.min(baseConfidence + (matchCount * 0.1), 0.95);
    }

    /**
     * 是否需要提取参数
     *
     * @return 是否需要参数提取
     */
    default boolean needParameterExtraction() {
        return true;
    }
}
