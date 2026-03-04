package com.mrshudson.optim.intent.extract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 参数提取结果
 * 封装参数提取的成功/失败状态、提取的参数Map和失败原因
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionResult {

    /**
     * 提取是否成功
     */
    private boolean success;

    /**
     * 提取的参数Map
     * key: 参数名，value: 参数值
     */
    private Map<String, Object> parameters;

    /**
     * 失败原因（当success为false时有效）
     */
    private String failureReason;

    /**
     * 使用的提取器名称
     */
    private String extractorName;

    /**
     * 置信度 (0-1)，表示参数提取的可信程度
     */
    private double confidence;

    /**
     * 创建成功的提取结果
     *
     * @param parameters 提取的参数
     * @return 成功的提取结果
     */
    public static ExtractionResult success(Map<String, Object> parameters) {
        return ExtractionResult.builder()
                .success(true)
                .parameters(parameters != null ? parameters : new HashMap<>())
                .confidence(1.0)
                .build();
    }

    /**
     * 创建成功的提取结果（带提取器名称）
     *
     * @param parameters     提取的参数
     * @param extractorName  提取器名称
     * @return 成功的提取结果
     */
    public static ExtractionResult success(Map<String, Object> parameters, String extractorName) {
        return ExtractionResult.builder()
                .success(true)
                .parameters(parameters != null ? parameters : new HashMap<>())
                .extractorName(extractorName)
                .confidence(1.0)
                .build();
    }

    /**
     * 创建失败的提取结果
     *
     * @param failureReason 失败原因
     * @return 失败的提取结果
     */
    public static ExtractionResult failure(String failureReason) {
        return ExtractionResult.builder()
                .success(false)
                .failureReason(failureReason)
                .parameters(Collections.emptyMap())
                .confidence(0.0)
                .build();
    }

    /**
     * 创建失败的提取结果（带提取器名称）
     *
     * @param failureReason 失败原因
     * @param extractorName 提取器名称
     * @return 失败的提取结果
     */
    public static ExtractionResult failure(String failureReason, String extractorName) {
        return ExtractionResult.builder()
                .success(false)
                .failureReason(failureReason)
                .extractorName(extractorName)
                .parameters(Collections.emptyMap())
                .confidence(0.0)
                .build();
    }

    /**
     * 创建部分成功的提取结果（部分参数提取成功）
     *
     * @param parameters 提取的参数（可能不完整）
     * @param missingParams 缺失的参数说明
     * @param confidence 置信度
     * @return 部分成功的提取结果
     */
    public static ExtractionResult partial(Map<String, Object> parameters, String missingParams, double confidence) {
        return ExtractionResult.builder()
                .success(true)
                .parameters(parameters != null ? parameters : new HashMap<>())
                .failureReason(missingParams)
                .confidence(confidence)
                .build();
    }

    /**
     * 获取指定参数值
     *
     * @param key 参数名
     * @return 参数值，不存在时返回null
     */
    public Object getParameter(String key) {
        return parameters != null ? parameters.get(key) : null;
    }

    /**
     * 获取指定参数值（字符串类型）
     *
     * @param key 参数名
     * @return 参数值字符串，不存在时返回null
     */
    public String getStringParameter(String key) {
        Object value = getParameter(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 检查是否包含指定参数
     *
     * @param key 参数名
     * @return 是否包含
     */
    public boolean hasParameter(String key) {
        return parameters != null && parameters.containsKey(key);
    }

    /**
     * 添加参数
     *
     * @param key   参数名
     * @param value 参数值
     */
    public void addParameter(String key, Object value) {
        if (parameters == null) {
            parameters = new HashMap<>();
        }
        parameters.put(key, value);
    }
}
