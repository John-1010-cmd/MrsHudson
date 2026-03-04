package com.mrshudson.optim.config;

import com.mrshudson.optim.cache.EmbeddingService;
import com.mrshudson.optim.cache.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量存储配置类
 * 根据配置创建对应的VectorStore实现Bean
 */
@Slf4j
@Configuration
public class VectorStoreSpringConfig {

    /**
     * Redis向量存储实现
     * 当 optim.vector-store.type=redis 时启用
     */
    @Bean
    @ConditionalOnProperty(prefix = "optim.vector-store", name = "type", havingValue = "redis", matchIfMissing = true)
    public VectorStore redisVectorStore(OptimProperties optimProperties) {
        log.info("Initializing Redis VectorStore");
        // 返回Redis实现，后续在RedisVectorStore类中实现
        return new VectorStore() {
            @Override
            public String store(String userId, String query, String response, float[] embedding) {
                throw new UnsupportedOperationException("RedisVectorStore not yet implemented");
            }

            @Override
            public java.util.Optional<CacheEntry> search(String userId, float[] queryEmbedding, double threshold) {
                throw new UnsupportedOperationException("RedisVectorStore not yet implemented");
            }

            @Override
            public boolean delete(String userId, String id) {
                throw new UnsupportedOperationException("RedisVectorStore not yet implemented");
            }

            @Override
            public int cleanup(String userId) {
                throw new UnsupportedOperationException("RedisVectorStore not yet implemented");
            }

            @Override
            public CacheStats getStats(String userId) {
                throw new UnsupportedOperationException("RedisVectorStore not yet implemented");
            }
        };
    }

    /**
     * Chroma向量存储实现
     * 当 optim.vector-store.type=chroma 时启用
     */
    @Bean
    @ConditionalOnProperty(prefix = "optim.vector-store", name = "type", havingValue = "chroma")
    public VectorStore chromaVectorStore(OptimProperties optimProperties) {
        log.info("Initializing Chroma VectorStore with config: {}:{}",
                optimProperties.getVectorStore().getChroma().getHost(),
                optimProperties.getVectorStore().getChroma().getPort());
        // 返回Chroma实现，后续在ChromaVectorStore类中实现
        return new VectorStore() {
            @Override
            public String store(String userId, String query, String response, float[] embedding) {
                throw new UnsupportedOperationException("ChromaVectorStore not yet implemented");
            }

            @Override
            public java.util.Optional<CacheEntry> search(String userId, float[] queryEmbedding, double threshold) {
                throw new UnsupportedOperationException("ChromaVectorStore not yet implemented");
            }

            @Override
            public boolean delete(String userId, String id) {
                throw new UnsupportedOperationException("ChromaVectorStore not yet implemented");
            }

            @Override
            public int cleanup(String userId) {
                throw new UnsupportedOperationException("ChromaVectorStore not yet implemented");
            }

            @Override
            public CacheStats getStats(String userId) {
                throw new UnsupportedOperationException("ChromaVectorStore not yet implemented");
            }
        };
    }

    /**
     * Milvus向量存储实现
     * 当 optim.vector-store.type=milvus 时启用
     */
    @Bean
    @ConditionalOnProperty(prefix = "optim.vector-store", name = "type", havingValue = "milvus")
    public VectorStore milvusVectorStore(OptimProperties optimProperties) {
        log.info("Initializing Milvus VectorStore");
        // 返回Milvus实现，后续在MilvusVectorStore类中实现
        return new VectorStore() {
            @Override
            public String store(String userId, String query, String response, float[] embedding) {
                throw new UnsupportedOperationException("MilvusVectorStore not yet implemented");
            }

            @Override
            public java.util.Optional<CacheEntry> search(String userId, float[] queryEmbedding, double threshold) {
                throw new UnsupportedOperationException("MilvusVectorStore not yet implemented");
            }

            @Override
            public boolean delete(String userId, String id) {
                throw new UnsupportedOperationException("MilvusVectorStore not yet implemented");
            }

            @Override
            public int cleanup(String userId) {
                throw new UnsupportedOperationException("MilvusVectorStore not yet implemented");
            }

            @Override
            public CacheStats getStats(String userId) {
                throw new UnsupportedOperationException("MilvusVectorStore not yet implemented");
            }
        };
    }

    /**
     * 空向量存储实现（禁用语义缓存）
     * 当 optim.vector-store.type=none 时启用
     */
    @Bean
    @ConditionalOnProperty(prefix = "optim.vector-store", name = "type", havingValue = "none")
    public VectorStore noopVectorStore(OptimProperties optimProperties) {
        log.info("Initializing No-op VectorStore (semantic cache disabled)");
        return new NoopVectorStore();
    }

    /**
     * 空实现类 - 用于禁用语义缓存的场景
     */
    public static class NoopVectorStore implements VectorStore {
        @Override
        public String store(String userId, String query, String response, float[] embedding) {
            return null;
        }

        @Override
        public java.util.Optional<CacheEntry> search(String userId, float[] queryEmbedding, double threshold) {
            return java.util.Optional.empty();
        }

        @Override
        public boolean delete(String userId, String id) {
            return false;
        }

        @Override
        public int cleanup(String userId) {
            return 0;
        }

        @Override
        public CacheStats getStats(String userId) {
            return new CacheStats(userId, 0, 0, 0, 0, 0);
        }
    }

    /**
     * 关键词嵌入服务实现
     * 简化版文本嵌入，基于预定义关键词的one-hot编码
     */
    @Bean
    @ConditionalOnProperty(prefix = "optim.semantic-cache", name = "enabled", havingValue = "true", matchIfMissing = true)
    public EmbeddingService keywordEmbeddingService() {
        log.info("Initializing Keyword EmbeddingService");
        return new KeywordEmbeddingService();
    }

    /**
     * 关键词嵌入服务实现类
     * 基于预定义关键词列表的简化版嵌入服务
     */
    public static class KeywordEmbeddingService implements EmbeddingService {

        // 预定义关键词列表（100个常见中文关键词）
        private static final String[] KEYWORDS = {
            "天气", "温度", "下雨", "晴天", "阴天", "多云", "雪", "风",
            "日历", "日程", "会议", "约会", "今天", "明天", "后天", "星期",
            "待办", "任务", "提醒", "完成", "待处理", "紧急", "重要",
            "你好", "谢谢", "再见", "帮助", "请问", "什么", "怎么", "为什么",
            "北京", "上海", "广州", "深圳", "杭州", "成都", "武汉", "西安",
            "时间", "日期", "几点", "什么时候", "多久", "多长",
            "查询", "搜索", "查找", "获取", "显示", "列出",
            "创建", "添加", "新建", "插入", "增加",
            "删除", "移除", "清除", "取消",
            "修改", "更新", "编辑", "更改",
            "是", "否", "有", "没有", "可以", "不可以",
            "早上", "中午", "下午", "晚上", "上午", "凌晨",
            "周一", "周二", "周三", "周四", "周五", "周六", "周日",
            "一月", "二月", "三月", "四月", "五月", "六月",
            "七月", "八月", "九月", "十月", "十一月", "十二月",
            "现在", "当前", "最近", "刚才", "马上", "立即",
            "全部", "所有", "部分", "一些", "很多", "少量",
            "高", "低", "大", "小", "多", "少", "好", "坏",
            "需要", "必须", "应该", "可能", "也许", "肯定"
        };

        @Override
        public float[] embed(String text) {
            if (text == null || text.isEmpty()) {
                return new float[KEYWORDS.length];
            }

            float[] embedding = new float[KEYWORDS.length];
            String lowerText = text.toLowerCase();

            for (int i = 0; i < KEYWORDS.length; i++) {
                if (lowerText.contains(KEYWORDS[i])) {
                    embedding[i] = 1.0f;
                }
            }

            // 归一化
            float norm = 0.0f;
            for (float v : embedding) {
                norm += v * v;
            }
            norm = (float) Math.sqrt(norm);

            if (norm > 0) {
                for (int i = 0; i < embedding.length; i++) {
                    embedding[i] /= norm;
                }
            }

            return embedding;
        }

        @Override
        public java.util.List<float[]> embedBatch(java.util.List<String> texts) {
            if (texts == null || texts.isEmpty()) {
                return new java.util.ArrayList<>();
            }

            java.util.List<float[]> embeddings = new java.util.ArrayList<>(texts.size());
            for (String text : texts) {
                embeddings.add(embed(text));
            }
            return embeddings;
        }

        @Override
        public int getDimension() {
            return KEYWORDS.length;
        }

        @Override
        public String getName() {
            return "KeywordEmbeddingService";
        }
    }
}
