package com.mrshudson.optim.correction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 错误模式学习器
 * 记录和学习错误模式，用于后续规避
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorPatternLearner {

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Redis Key 前缀
     */
    private static final String ERROR_PATTERN_KEY = "ai:error_patterns:";
    private static final String ERROR_FREQUENCY_KEY = "ai:error_frequency:";

    /**
     * 记录错误模式
     *
     * @param toolName 工具名称
     * @param errorType 错误类型
     * @param userQuery 用户查询
     */
    public void recordErrorPattern(String toolName, String errorType, String userQuery) {
        try {
            String key = ERROR_PATTERN_KEY + toolName + ":" + errorType;
            redisTemplate.opsForSet().add(key, userQuery);

            // 记录频率
            String frequencyKey = ERROR_FREQUENCY_KEY + toolName + ":" + errorType;
            redisTemplate.opsForValue().increment(frequencyKey);

            // 设置过期时间（7天）
            redisTemplate.expire(frequencyKey, 7, TimeUnit.DAYS);

            log.debug("记录错误模式: tool={}, type={}, query={}", toolName, errorType, userQuery);

        } catch (Exception e) {
            log.error("记录错误模式失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取常见错误模式
     *
     * @param toolName 工具名称
     * @return 错误模式列表
     */
    public List<String> getCommonErrorPatterns(String toolName) {
        try {
            Set<String> keys = redisTemplate.keys(ERROR_PATTERN_KEY + toolName + ":*");
            if (keys == null || keys.isEmpty()) {
                return Collections.emptyList();
            }

            List<String> patterns = new ArrayList<>();
            for (String key : keys) {
                Set<String> members = redisTemplate.opsForSet().members(key);
                if (members != null) {
                    patterns.addAll(members);
                }
            }

            return patterns;

        } catch (Exception e) {
            log.error("获取错误模式失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取错误规避指引
     *
     * @param toolName 工具名称
     * @return 规避指引字符串
     */
    public String getErrorAvoidanceGuidance(String toolName) {
        try {
            String frequencyKey = ERROR_FREQUENCY_KEY + toolName + ":*";
            Set<String> keys = redisTemplate.keys(frequencyKey);

            if (keys == null || keys.isEmpty()) {
                return "";
            }

            // 获取频率最高的错误类型
            Map<String, Long> frequencyMap = new HashMap<>();
            for (String key : keys) {
                String frequency = redisTemplate.opsForValue().get(key);
                if (frequency != null) {
                    String errorType = key.replace(ERROR_FREQUENCY_KEY + toolName + ":", "");
                    frequencyMap.put(errorType, Long.parseLong(frequency));
                }
            }

            if (frequencyMap.isEmpty()) {
                return "";
            }

            // 按频率排序，取前3
            List<Map.Entry<String, Long>> sorted = frequencyMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .toList();

            StringBuilder guidance = new StringBuilder("\n【历史错误规避】\n");
            for (Map.Entry<String, Long> entry : sorted) {
                guidance.append("- 避免：").append(entry.getKey())
                    .append("（出现").append(entry.getValue()).append("次）\n");
            }

            return guidance.toString();

        } catch (Exception e) {
            log.error("获取错误规避指引失败: {}", e.getMessage(), e);
            return "";
        }
    }

    /**
     * 获取高频错误统计
     *
     * @param limit 返回数量
     * @return 高频错误列表
     */
    public List<ErrorFrequency> getTopErrors(int limit) {
        try {
            Set<String> keys = redisTemplate.keys(ERROR_FREQUENCY_KEY + "*");
            if (keys == null || keys.isEmpty()) {
                return Collections.emptyList();
            }

            List<ErrorFrequency> frequencies = new ArrayList<>();
            for (String key : keys) {
                String value = redisTemplate.opsForValue().get(key);
                if (value != null) {
                    String[] parts = key.replace(ERROR_FREQUENCY_KEY, "").split(":");
                    if (parts.length >= 2) {
                        frequencies.add(new ErrorFrequency(parts[0], parts[1], Long.parseLong(value)));
                    }
                }
            }

            // 按频率排序
            frequencies.sort((a, b) -> Long.compare(b.count, a.count));

            return frequencies.stream().limit(limit).toList();

        } catch (Exception e) {
            log.error("获取高频错误失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 清除错误模式记录
     *
     * @param toolName 工具名称（null表示清除所有）
     */
    public void clearErrorPatterns(String toolName) {
        try {
            String pattern;
            if (toolName == null || toolName.isEmpty()) {
                pattern = ERROR_PATTERN_KEY + "*";
            } else {
                pattern = ERROR_PATTERN_KEY + toolName + ":*";
            }

            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("已清除错误模式: {}", pattern);
            }

        } catch (Exception e) {
            log.error("清除错误模式失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 错误频率记录
     */
    public record ErrorFrequency(String toolName, String errorType, long count) {}
}
