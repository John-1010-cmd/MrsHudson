package com.mrshudson.optim.wrapper;

import com.mrshudson.domain.dto.*;
import com.mrshudson.service.ChatService;

/**
 * 优化后的对话服务接口
 * 继承自 ChatService，保持与原接口兼容
 * 在实现中集成语义缓存、意图路由、对话压缩等优化层
 */
public interface OptimChatService extends ChatService {

    /**
     * 获取优化配置状态
     *
     * @return 优化层配置信息
     */
    OptimConfigStatus getOptimConfigStatus();

    /**
     * 更新优化层开关配置
     *
     * @param configStatus 配置状态
     */
    void updateOptimConfig(OptimConfigStatus configStatus);

    /**
     * 获取优化统计信息
     *
     * @return 统计信息
     */
    OptimStatistics getStatistics();

    /**
     * 重置统计信息
     */
    void resetStatistics();

    /**
     * 优化配置状态内部类
     */
    class OptimConfigStatus {
        private boolean semanticCacheEnabled;
        private boolean intentRouterEnabled;
        private boolean compressionEnabled;
        private boolean costMonitorEnabled;

        public OptimConfigStatus() {
            this.semanticCacheEnabled = true;
            this.intentRouterEnabled = true;
            this.compressionEnabled = true;
            this.costMonitorEnabled = true;
        }

        public OptimConfigStatus(boolean semanticCacheEnabled, boolean intentRouterEnabled,
                                 boolean compressionEnabled, boolean costMonitorEnabled) {
            this.semanticCacheEnabled = semanticCacheEnabled;
            this.intentRouterEnabled = intentRouterEnabled;
            this.compressionEnabled = compressionEnabled;
            this.costMonitorEnabled = costMonitorEnabled;
        }

        public boolean isSemanticCacheEnabled() {
            return semanticCacheEnabled;
        }

        public void setSemanticCacheEnabled(boolean semanticCacheEnabled) {
            this.semanticCacheEnabled = semanticCacheEnabled;
        }

        public boolean isIntentRouterEnabled() {
            return intentRouterEnabled;
        }

        public void setIntentRouterEnabled(boolean intentRouterEnabled) {
            this.intentRouterEnabled = intentRouterEnabled;
        }

        public boolean isCompressionEnabled() {
            return compressionEnabled;
        }

        public void setCompressionEnabled(boolean compressionEnabled) {
            this.compressionEnabled = compressionEnabled;
        }

        public boolean isCostMonitorEnabled() {
            return costMonitorEnabled;
        }

        public void setCostMonitorEnabled(boolean costMonitorEnabled) {
            this.costMonitorEnabled = costMonitorEnabled;
        }
    }

    /**
     * 优化统计信息内部类
     */
    class OptimStatistics {
        private long cacheHits;
        private long cacheMisses;
        private long intentRouterHandled;
        private long compressionCount;
        private long totalAiCalls;
        private long totalCostSaved;

        public OptimStatistics() {
            this.cacheHits = 0;
            this.cacheMisses = 0;
            this.intentRouterHandled = 0;
            this.compressionCount = 0;
            this.totalAiCalls = 0;
            this.totalCostSaved = 0;
        }

        public long getCacheHits() {
            return cacheHits;
        }

        public void setCacheHits(long cacheHits) {
            this.cacheHits = cacheHits;
        }

        public long getCacheMisses() {
            return cacheMisses;
        }

        public void setCacheMisses(long cacheMisses) {
            this.cacheMisses = cacheMisses;
        }

        public long getIntentRouterHandled() {
            return intentRouterHandled;
        }

        public void setIntentRouterHandled(long intentRouterHandled) {
            this.intentRouterHandled = intentRouterHandled;
        }

        public long getCompressionCount() {
            return compressionCount;
        }

        public void setCompressionCount(long compressionCount) {
            this.compressionCount = compressionCount;
        }

        public long getTotalAiCalls() {
            return totalAiCalls;
        }

        public void setTotalAiCalls(long totalAiCalls) {
            this.totalAiCalls = totalAiCalls;
        }

        public long getTotalCostSaved() {
            return totalCostSaved;
        }

        public void setTotalCostSaved(long totalCostSaved) {
            this.totalCostSaved = totalCostSaved;
        }

        public double getCacheHitRate() {
            long total = cacheHits + cacheMisses;
            return total > 0 ? (double) cacheHits / total : 0.0;
        }
    }
}
