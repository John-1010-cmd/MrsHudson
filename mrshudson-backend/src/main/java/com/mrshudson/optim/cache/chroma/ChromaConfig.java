package com.mrshudson.optim.cache.chroma;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Chroma向量数据库配置
 */
@Data
@Component
@ConditionalOnProperty(name = "optim.vector-store.type", havingValue = "chroma")
@ConfigurationProperties(prefix = "chroma")
public class ChromaConfig {

    /**
     * Chroma服务地址
     */
    private String host = "http://localhost:8000";

    /**
     * 连接超时（毫秒）
     */
    private int connectTimeout = 5000;

    /**
     * 读取超时（毫秒）
     */
    private int readTimeout = 10000;

    /**
     * 集合名称
     */
    private String collectionName = "semantic_cache";

    /**
     * 向量维度
     */
    private int embeddingDimension = 384;

    /**
     * 是否启用SSL
     */
    private boolean sslEnabled = false;

    /**
     * API密钥（如果需要）
     */
    private String apiKey = "";

    /**
     * 租户ID
     */
    private String tenant = "default_tenant";

    /**
     * 数据库名称
     */
    private String database = "default_database";
}
