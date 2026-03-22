package com.mrshudson.optim.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI调用成本优化配置属性
 * 控制各优化层的开关和参数
 */
@Data
@Component
@ConfigurationProperties(prefix = "optim")
public class OptimProperties {

    /**
     * 语义缓存配置
     */
    private SemanticCacheConfig semanticCache = new SemanticCacheConfig();

    /**
     * 意图路由配置
     */
    private IntentRouterConfig intentRouter = new IntentRouterConfig();

    /**
     * 对话压缩配置
     */
    private CompressionConfig compression = new CompressionConfig();

    /**
     * 工具缓存配置
     */
    private ToolCacheConfig toolCache = new ToolCacheConfig();

    /**
     * 成本监控配置
     */
    private CostMonitorConfig costMonitor = new CostMonitorConfig();

    /**
     * 向量存储配置
     */
    private VectorStoreConfig vectorStore = new VectorStoreConfig();

    /**
     * Kimi API 参数配置
     */
    private KimiParamsConfig kimiParams = new KimiParamsConfig();

    @Data
    public static class KimiParamsConfig {
        /** 温度参数 (0-2)，默认0.3 */
        private double temperature = 0.3;
        /** 最大token数，默认800 */
        private int maxTokens = 800;
    }

    @Data
    public static class SemanticCacheConfig {
        /** 是否启用语义缓存 */
        private boolean enabled = true;
        /** 相似度阈值 (0-1) */
        private double similarityThreshold = 0.92;
        /** 缓存TTL（小时） */
        private int ttlHours = 168; // 7天
        /** 最大缓存条目数 */
        private int maxEntries = 10000;
    }

    @Data
    public static class IntentRouterConfig {
        /** 路由模式: rule-only, hybrid, ai-only */
        private String mode = "hybrid";
        /** 规则层配置 */
        private RuleLayerConfig rule = new RuleLayerConfig();
        /** 轻量AI层配置 */
        private LightweightAiConfig lightweightAi = new LightweightAiConfig();
        /** 完整AI层配置 */
        private FullAiConfig fullAi = new FullAiConfig();

        @Data
        public static class RuleLayerConfig {
            private boolean enabled = true;
        }

        @Data
        public static class LightweightAiConfig {
            private boolean enabled = true;
            /** 最大token数 */
            private int maxTokens = 100;
            /** 超时时间（毫秒） */
            private int timeoutMs = 2000;
        }

        @Data
        public static class FullAiConfig {
            private boolean enabled = true;
        }
    }

    @Data
    public static class CompressionConfig {
        /** 是否启用对话压缩 */
        private boolean enabled = true;
        /** 触发压缩的消息数阈值 */
        private int triggerThreshold = 10;
        /** 保留的最近消息数 */
        private int keepRecentMessages = 4;
        /** 摘要最大长度 */
        private int summaryMaxLength = 100;
    }

    @Data
    public static class ToolCacheConfig {
        /** 是否启用工具缓存 */
        private boolean enabled = true;
        /** 天气缓存TTL（分钟） */
        private int weatherTtlMinutes = 10;
        /** 日历缓存TTL（分钟） */
        private int calendarTtlMinutes = 5;
        /** 待办缓存TTL（分钟） */
        private int todoTtlMinutes = 5;
        /** 路线规划缓存TTL（分钟） */
        private int routeTtlMinutes = 5;
    }

    @Data
    public static class CostMonitorConfig {
        /** 是否启用成本监控 */
        private boolean enabled = true;
        /** 每日成本告警阈值（元） */
        private double dailyCostAlertThreshold = 50.0;
        /** 是否记录详细日志 */
        private boolean detailedLog = true;
        /** Token 价格（元/1000 tokens），默认0.003 */
        private double tokenPrice = 0.003;
    }

    @Data
    public static class VectorStoreConfig {
        /** 存储类型: redis, chroma, milvus, none */
        private String type = "redis";
        /** Chroma配置 */
        private ChromaConfig chroma = new ChromaConfig();

        @Data
        public static class ChromaConfig {
            private String host = "localhost";
            private int port = 8000;
            private String collectionName = "semantic_cache";
        }
    }
}
