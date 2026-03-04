package com.mrshudson.optim.intent;

import com.mrshudson.optim.intent.handler.AbstractIntentHandler;

/**
 * 意图路由器接口
 * 负责将用户查询路由到对应的意图处理器
 */
public interface IntentRouter {

    /**
     * 对用户查询进行意图分类
     *
     * @param query 用户原始查询
     * @return 意图分类结果，包含意图类型和置信度
     */
    IntentClassification classify(String query);

    /**
     * 将查询路由到对应的处理器并执行处理
     *
     * @param userId 用户ID
     * @param query  用户原始查询
     * @return 路由结果，包含处理结果或需要AI处理的标记
     */
    RouteResult route(Long userId, String query);

    /**
     * 注册意图处理器
     *
     * @param handler 意图处理器
     */
    void registerHandler(AbstractIntentHandler handler);

    /**
     * 获取路由器名称
     *
     * @return 路由器标识名称
     */
    String getName();

    /**
     * 获取路由器模式
     *
     * @return 路由模式: rule-only, hybrid, ai-only
     */
    String getMode();

    /**
     * 意图分类结果内部类
     */
    class IntentClassification {
        private final IntentType intentType;
        private final double confidence;
        private final String routerLayer;

        public IntentClassification(IntentType intentType, double confidence, String routerLayer) {
            this.intentType = intentType;
            this.confidence = confidence;
            this.routerLayer = routerLayer;
        }

        public IntentType getIntentType() {
            return intentType;
        }

        public double getConfidence() {
            return confidence;
        }

        public String getRouterLayer() {
            return routerLayer;
        }

        /**
         * 判断置信度是否达到阈值
         *
         * @param threshold 阈值 (0-1)
         * @return 是否达到阈值
         */
        public boolean meetsThreshold(double threshold) {
            return confidence >= threshold;
        }

        /**
         * 创建未知意图分类结果
         *
         * @return 未知意图分类
         */
        public static IntentClassification unknown() {
            return new IntentClassification(IntentType.UNKNOWN, 0.0, "unknown");
        }

        /**
         * 创建规则层分类结果
         *
         * @param intentType 意图类型
         * @param confidence 置信度
         * @return 规则层分类结果
         */
        public static IntentClassification rule(IntentType intentType, double confidence) {
            return new IntentClassification(intentType, confidence, "rule");
        }

        /**
         * 创建轻量AI层分类结果
         *
         * @param intentType 意图类型
         * @param confidence 置信度
         * @return 轻量AI层分类结果
         */
        public static IntentClassification lightweightAi(IntentType intentType, double confidence) {
            return new IntentClassification(intentType, confidence, "lightweight_ai");
        }

        /**
         * 创建完整AI层分类结果
         *
         * @param intentType 意图类型
         * @param confidence 置信度
         * @return 完整AI层分类结果
         */
        public static IntentClassification fullAi(IntentType intentType, double confidence) {
            return new IntentClassification(intentType, confidence, "full_ai");
        }
    }
}
