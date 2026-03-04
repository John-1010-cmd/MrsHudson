package com.mrshudson.optim.exception;

import lombok.Getter;

/**
 * 优化层基础异常类
 * 支持降级处理标记
 */
@Getter
public class OptimException extends RuntimeException {

    /** 错误码 */
    private final String errorCode;
    /** 是否可降级（异常时是否可使用 fallback） */
    private final boolean degradable;

    public OptimException(String message) {
        super(message);
        this.errorCode = "OPTIM_ERROR";
        this.degradable = true;
    }

    public OptimException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.degradable = true;
    }

    public OptimException(String errorCode, String message, boolean degradable) {
        super(message);
        this.errorCode = errorCode;
        this.degradable = degradable;
    }

    public OptimException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.degradable = true;
    }

    /**
     * 语义缓存异常
     */
    @Getter
    public static class CacheException extends OptimException {
        public CacheException(String message) {
            super("CACHE_ERROR", message, true);
        }
        public CacheException(String message, Throwable cause) {
            super("CACHE_ERROR", message, cause);
        }
    }

    /**
     * 意图路由异常
     */
    @Getter
    public static class IntentException extends OptimException {
        public IntentException(String message) {
            super("INTENT_ERROR", message, true);
        }
        public IntentException(String message, Throwable cause) {
            super("INTENT_ERROR", message, cause);
        }
    }

    /**
     * 向量存储异常
     */
    @Getter
    public static class VectorStoreException extends OptimException {
        public VectorStoreException(String message) {
            super("VECTOR_STORE_ERROR", message, true);
        }
        public VectorStoreException(String message, Throwable cause) {
            super("VECTOR_STORE_ERROR", message, cause);
        }
    }

    /**
     * 嵌入服务异常
     */
    @Getter
    public static class EmbeddingException extends OptimException {
        public EmbeddingException(String message) {
            super("EMBEDDING_ERROR", message, true);
        }
        public EmbeddingException(String message, Throwable cause) {
            super("EMBEDDING_ERROR", message, cause);
        }
    }

    /**
     * 对话压缩异常
     */
    @Getter
    public static class CompressionException extends OptimException {
        public CompressionException(String message) {
            super("COMPRESSION_ERROR", message, true);
        }
        public CompressionException(String message, Throwable cause) {
            super("COMPRESSION_ERROR", message, cause);
        }
    }
}
