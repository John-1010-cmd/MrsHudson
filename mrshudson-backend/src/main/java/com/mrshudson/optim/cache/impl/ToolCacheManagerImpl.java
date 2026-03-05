package com.mrshudson.optim.cache.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrshudson.optim.cache.ToolCacheManager;
import com.mrshudson.optim.config.OptimProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 工具结果缓存管理器实现类
 * 使用RedisTemplate进行缓存操作，支持序列化异常降级处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolCacheManagerImpl implements ToolCacheManager {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final OptimProperties optimProperties;

    private static final String CACHE_KEY_PREFIX = "tool:";

    @Override
    public Object get(String toolName, String paramsHash) {
        if (!optimProperties.getToolCache().isEnabled()) {
            log.debug("工具缓存已禁用");
            return null;
        }

        if (toolName == null || paramsHash == null) {
            log.warn("工具名称或参数哈希为空，无法获取缓存");
            return null;
        }

        String cacheKey = buildCacheKey(toolName, paramsHash);

        try {
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null) {
                log.debug("缓存命中: tool={}, paramsHash={}", toolName, paramsHash);
                // 将JSON字符串反序列化为对象
                return objectMapper.readValue(cachedJson, Object.class);
            } else {
                log.debug("缓存未命中: tool={}, paramsHash={}", toolName, paramsHash);
                return null;
            }
        } catch (Exception e) {
            log.error("获取缓存时发生异常: key={}, error={}", cacheKey, e.getMessage());
            // 降级处理：返回null，让调用方重新查询
            return null;
        }
    }

    @Override
    public void put(String toolName, String paramsHash, Object result, int ttlMinutes) {
        if (!optimProperties.getToolCache().isEnabled()) {
            log.debug("工具缓存已禁用，跳过缓存存储");
            return;
        }

        if (toolName == null || paramsHash == null || result == null) {
            log.warn("工具名称、参数哈希或结果为空，无法存储缓存");
            return;
        }

        String cacheKey = buildCacheKey(toolName, paramsHash);

        try {
            // 将对象序列化为JSON字符串
            String jsonResult = objectMapper.writeValueAsString(result);

            redisTemplate.opsForValue().set(cacheKey, jsonResult, ttlMinutes, TimeUnit.MINUTES);
            log.debug("缓存已存储: tool={}, paramsHash={}, ttl={}分钟", toolName, paramsHash, ttlMinutes);
        } catch (Exception e) {
            log.error("存储缓存时发生异常: key={}, error={}", cacheKey, e.getMessage());
            // 降级处理：不抛出异常，让流程继续
        }
    }

    @Override
    public void invalidate(String toolName, Long userId) {
        if (!optimProperties.getToolCache().isEnabled()) {
            log.debug("工具缓存已禁用，跳过缓存清除");
            return;
        }

        try {
            String pattern;
            if (toolName != null && userId != null) {
                // 清除指定工具和用户的缓存（需要匹配包含userId的key）
                // 注意：当前key格式不包含userId，这里预留扩展
                pattern = CACHE_KEY_PREFIX + toolName + ":*";
            } else if (toolName != null) {
                // 清除指定工具的所有缓存
                pattern = CACHE_KEY_PREFIX + toolName + ":*";
            } else if (userId != null) {
                // 清除指定用户的所有工具缓存（需要key包含userId）
                // 当前实现：清除所有工具缓存
                pattern = CACHE_KEY_PREFIX + "*";
            } else {
                // 清除所有工具缓存
                pattern = CACHE_KEY_PREFIX + "*";
            }

            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("已清除缓存: pattern={}, count={}", pattern, keys.size());
            } else {
                log.debug("没有匹配的缓存需要清除: pattern={}", pattern);
            }
        } catch (Exception e) {
            log.error("清除缓存时发生异常: toolName={}, userId={}, error={}",
                    toolName, userId, e.getMessage());
            // 降级处理：不抛出异常
        }
    }

    @Override
    public void invalidateEntry(String toolName, String paramsHash) {
        if (!optimProperties.getToolCache().isEnabled()) {
            log.debug("工具缓存已禁用，跳过缓存清除");
            return;
        }

        if (toolName == null || paramsHash == null) {
            log.warn("工具名称或参数哈希为空，无法清除缓存");
            return;
        }

        String cacheKey = buildCacheKey(toolName, paramsHash);

        try {
            Boolean deleted = redisTemplate.delete(cacheKey);
            if (Boolean.TRUE.equals(deleted)) {
                log.debug("缓存已清除: tool={}, paramsHash={}", toolName, paramsHash);
            } else {
                log.debug("缓存不存在或已过期: tool={}, paramsHash={}", toolName, paramsHash);
            }
        } catch (Exception e) {
            log.error("清除缓存条目时发生异常: key={}, error={}", cacheKey, e.getMessage());
            // 降级处理：不抛出异常
        }
    }

    @Override
    public boolean exists(String toolName, String paramsHash) {
        if (!optimProperties.getToolCache().isEnabled()) {
            return false;
        }

        if (toolName == null || paramsHash == null) {
            return false;
        }

        String cacheKey = buildCacheKey(toolName, paramsHash);

        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey));
        } catch (Exception e) {
            log.error("检查缓存存在性时发生异常: key={}, error={}", cacheKey, e.getMessage());
            // 降级处理：假设缓存不存在
            return false;
        }
    }

    @Override
    public String buildCacheKey(String toolName, String paramsHash) {
        return CACHE_KEY_PREFIX + toolName + ":" + paramsHash;
    }

    /**
     * 检查对象是否可序列化
     *
     * @param obj 要检查的对象
     * @return true如果对象可序列化
     */
    private boolean isSerializable(Object obj) {
        try {
            // 尝试序列化和反序列化
            String json = objectMapper.writeValueAsString(obj);
            objectMapper.readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            log.warn("对象不可序列化: type={}, error={}", obj.getClass().getName(), e.getMessage());
            return false;
        }
    }

    /**
     * 根据工具名称获取默认TTL（分钟）
     * 用于当调用方未指定TTL时的默认配置
     *
     * @param toolName 工具名称
     * @return 默认TTL分钟数
     */
    public int getDefaultTtl(String toolName) {
        if (toolName == null) {
            return 5; // 默认5分钟
        }

        return switch (toolName.toLowerCase()) {
            case "weather", "weathertool" -> optimProperties.getToolCache().getWeatherTtlMinutes();
            case "calendar", "calendartool" -> optimProperties.getToolCache().getCalendarTtlMinutes();
            case "todo", "todotool" -> optimProperties.getToolCache().getTodoTtlMinutes();
            default -> 5; // 默认5分钟
        };
    }
}
