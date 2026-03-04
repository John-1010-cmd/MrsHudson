package com.mrshudson.optim.intent.extract;

import com.mrshudson.optim.intent.IntentType;

import java.util.Map;

/**
 * 参数提取器接口
 * 从用户查询中提取结构化参数，支持规则、AI等多种实现方式
 */
public interface ParameterExtractor {

    /**
     * 从查询中提取参数
     *
     * @param query      用户原始查询
     * @param intentType 识别到的意图类型
     * @return 参数提取结果，包含提取的参数Map或失败信息
     */
    ExtractionResult extract(String query, IntentType intentType);

    /**
     * 从查询中提取指定类型的参数
     *
     * @param query      用户原始查询
     * @param intentType 识别到的意图类型
     * @param context    上下文信息（如历史对话、用户偏好等）
     * @return 参数提取结果
     */
    default ExtractionResult extract(String query, IntentType intentType, Map<String, Object> context) {
        // 默认实现忽略上下文，直接调用基础extract方法
        return extract(query, intentType);
    }

    /**
     * 获取提取器名称
     *
     * @return 提取器标识名称
     */
    String getName();

    /**
     * 获取提取器描述
     *
     * @return 提取器功能描述
     */
    String getDescription();

    /**
     * 检查是否支持指定意图类型
     *
     * @param intentType 意图类型
     * @return 是否支持
     */
    boolean supports(IntentType intentType);
}
