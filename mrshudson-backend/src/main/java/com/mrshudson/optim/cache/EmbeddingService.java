package com.mrshudson.optim.cache;

import java.util.List;

/**
 * 文本嵌入服务接口
 * 定义文本向量化的抽象接口，支持多种实现方式（关键词、API、本地模型等）
 */
public interface EmbeddingService {

    /**
     * 将单个文本转换为向量嵌入
     *
     * @param text 输入文本
     * @return 向量嵌入（float数组）
     */
    float[] embed(String text);

    /**
     * 批量将文本转换为向量嵌入
     * 批量处理通常比多次单条调用更高效
     *
     * @param texts 输入文本列表
     * @return 向量嵌入列表（与输入顺序一致）
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * 获取嵌入向量的维度
     *
     * @return 向量维度
     */
    int getDimension();

    /**
     * 计算两个向量的余弦相似度
     *
     * @param vector1 第一个向量
     * @param vector2 第二个向量
     * @return 余弦相似度（-1到1之间）
     */
    default double cosineSimilarity(float[] vector1, float[] vector2) {
        if (vector1 == null || vector2 == null || vector1.length != vector2.length) {
            throw new IllegalArgumentException("Vectors must be non-null and have the same dimension");
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vector1.length; i++) {
            dotProduct += vector1[i] * vector2[i];
            norm1 += vector1[i] * vector1[i];
            norm2 += vector2[i] * vector2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * 获取嵌入服务名称
     *
     * @return 服务名称
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 检查服务是否可用
     *
     * @return true如果服务可用
     */
    default boolean isAvailable() {
        return true;
    }
}
