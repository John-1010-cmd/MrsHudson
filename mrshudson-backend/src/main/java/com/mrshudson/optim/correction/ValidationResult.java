package com.mrshudson.optim.correction;

import lombok.Builder;
import lombok.Data;

/**
 * 验证结果
 * 验证工具执行结果是否有效
 */
@Data
@Builder
public class ValidationResult {
    
    /**
     * 是否有效
     */
    private boolean isValid;
    
    /**
     * 错误类型
     */
    private ErrorType errorType;
    
    /**
     * 错误消息
     */
    private String errorMessage;
    
    /**
     * 结果置信度
     */
    private double confidence;

    /**
     * 重试次数
     */
    private int retryCount;

    /**
     * 错误类型枚举
     */
    public enum ErrorType {
        /**
         * 格式错误
         */
        FORMAT_ERROR,
        
        /**
         * 数据不合理
         */
        DATA_ERROR,
        
        /**
         * 执行超时
         */
        TIMEOUT,
        
        /**
         * 部分结果
         */
        PARTIAL_RESULT,
        
        /**
         * 无错误
         */
        NONE
    }
    
    /**
     * 创建成功结果
     */
    public static ValidationResult success() {
        return ValidationResult.builder()
            .isValid(true)
            .errorType(ErrorType.NONE)
            .confidence(1.0)
            .retryCount(0)
            .build();
    }
    
    /**
     * 创建失败结果
     */
    public static ValidationResult failure(ErrorType type, String message) {
        return ValidationResult.builder()
            .isValid(false)
            .errorType(type)
            .errorMessage(message)
            .confidence(0.0)
            .retryCount(0)
            .build();
    }
    
    /**
     * 创建格式错误结果
     */
    public static ValidationResult formatError(String message) {
        return failure(ErrorType.FORMAT_ERROR, message);
    }
    
    /**
     * 创建数据错误结果
     */
    public static ValidationResult dataError(String message) {
        return failure(ErrorType.DATA_ERROR, message);
    }
    
    /**
     * 创建超时结果
     */
    public static ValidationResult timeout() {
        return failure(ErrorType.TIMEOUT, "请求超时");
    }
    
    /**
     * 创建部分结果
     */
    public static ValidationResult partialResult(String message) {
        return ValidationResult.builder()
            .isValid(false)
            .errorType(ErrorType.PARTIAL_RESULT)
            .errorMessage(message)
            .confidence(0.5)
            .retryCount(0)
            .build();
    }
}
