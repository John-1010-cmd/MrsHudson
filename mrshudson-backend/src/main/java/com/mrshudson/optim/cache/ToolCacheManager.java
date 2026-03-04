package com.mrshudson.optim.cache;

/**
 * 工具结果缓存管理器接口
 * 提供工具调用结果的缓存存取、过期和清除功能
 */
public interface ToolCacheManager {

    /**
     * 从缓存获取工具调用结果
     *
     * @param toolName 工具名称
     * @param paramsHash 参数哈希值
     * @return 缓存的结果对象，如果不存在则返回null
     */
    Object get(String toolName, String paramsHash);

    /**
     * 将工具调用结果存入缓存
     *
     * @param toolName 工具名称
     * @param paramsHash 参数哈希值
     * @param result 要缓存的结果对象
     * @param ttlMinutes 缓存过期时间（分钟）
     */
    void put(String toolName, String paramsHash, Object result, int ttlMinutes);

    /**
     * 使指定工具和用户相关的缓存失效
     *
     * @param toolName 工具名称，如果为null则清除所有工具的缓存
     * @param userId 用户ID，如果为null则清除所有用户的缓存
     */
    void invalidate(String toolName, Long userId);

    /**
     * 使指定工具的特定参数缓存失效
     *
     * @param toolName 工具名称
     * @param paramsHash 参数哈希值
     */
    void invalidateEntry(String toolName, String paramsHash);

    /**
     * 检查缓存中是否存在指定key
     *
     * @param toolName 工具名称
     * @param paramsHash 参数哈希值
     * @return true如果缓存存在，否则false
     */
    boolean exists(String toolName, String paramsHash);

    /**
     * 获取缓存key
     * key格式: tool:{toolName}:{paramsHash}
     *
     * @param toolName 工具名称
     * @param paramsHash 参数哈希值
     * @return 完整的缓存key
     */
    default String buildCacheKey(String toolName, String paramsHash) {
        return String.format("tool:%s:%s", toolName, paramsHash);
    }
}
