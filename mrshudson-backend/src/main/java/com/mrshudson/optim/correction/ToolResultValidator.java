package com.mrshudson.optim.correction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 工具结果校验器
 * 校验工具返回结果的格式和内容
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolResultValidator {

    /**
     * 错误关键词
     */
    private static final String[] ERROR_KEYWORDS = {
        "error", "失败", "无法", "exception", "null", "none",
        "not found", "不存在", "错误", "失败"
    };

    /**
     * 验证工具执行结果
     *
     * @param toolName 工具名称
     * @param result 执行结果
     * @param originalRequest 原始请求
     * @return 验证结果
     */
    public ValidationResult validate(String toolName, String result, Object originalRequest) {
        // 1. 检查结果是否为空
        if (result == null || result.isEmpty()) {
            log.warn("工具 {} 返回结果为空", toolName);
            return ValidationResult.failure(
                ValidationResult.ErrorType.DATA_ERROR,
                "返回结果为空"
            );
        }

        // 2. 检查是否包含错误标记
        String lowerResult = result.toLowerCase();
        for (String keyword : ERROR_KEYWORDS) {
            if (lowerResult.contains(keyword)) {
                log.warn("工具 {} 返回结果包含错误关键词: {}", toolName, keyword);
                return ValidationResult.failure(
                    ValidationResult.ErrorType.DATA_ERROR,
                    "返回结果包含错误信息: " + result.substring(0, Math.min(50, result.length()))
                );
            }
        }

        // 3. 根据工具类型进行特定验证
        return validateByToolType(toolName, result, originalRequest);
    }

    /**
     * 根据工具类型进行特定验证
     */
    private ValidationResult validateByToolType(String toolName, String result, Object originalRequest) {
        if (toolName == null) {
            return ValidationResult.success();
        }

        // 天气工具验证
        if (toolName.contains("weather")) {
            return validateWeatherResult(result);
        }

        // 日历工具验证
        if (toolName.contains("calendar")) {
            return validateCalendarResult(result);
        }

        // 待办工具验证
        if (toolName.contains("todo")) {
            return validateTodoResult(result);
        }

        // 路线工具验证
        if (toolName.contains("route") || toolName.contains("导航")) {
            return validateRouteResult(result);
        }

        // 默认验证通过
        return ValidationResult.success();
    }

    /**
     * 验证天气结果
     */
    private ValidationResult validateWeatherResult(String result) {
        // 检查是否包含温度、天气等关键词
        boolean hasTemperature = result.contains("温度") || result.contains("°") || result.contains("C");
        boolean hasWeather = result.contains("天气") || result.contains("晴") || result.contains("雨") 
            || result.contains("雪") || result.contains("云");

        if (!hasTemperature && !hasWeather) {
            return ValidationResult.partialResult("天气结果可能不完整");
        }

        return ValidationResult.success();
    }

    /**
     * 验证日历结果
     */
    private ValidationResult validateCalendarResult(String result) {
        // 检查是否包含时间信息
        boolean hasTimeInfo = result.contains("日") || result.contains("号") 
            || result.contains("点") || result.contains("时");

        if (!hasTimeInfo && result.length() < 10) {
            return ValidationResult.partialResult("日历结果可能不完整");
        }

        return ValidationResult.success();
    }

    /**
     * 验证待办结果
     */
    private ValidationResult validateTodoResult(String result) {
        // 检查是否有列表结构
        boolean hasList = result.contains("1.") || result.contains("- ") 
            || result.contains("•") || result.contains("·");

        if (!hasList && result.length() < 20) {
            return ValidationResult.partialResult("待办结果可能不完整");
        }

        return ValidationResult.success();
    }

    /**
     * 验证路线结果
     */
    private ValidationResult validateRouteResult(String result) {
        // 检查是否包含距离或时间
        boolean hasDistance = result.contains("公里") || result.contains("km") || result.contains("米");
        boolean hasTime = result.contains("分钟") || result.contains("小时") || result.contains("min") || result.contains("h");

        if (!hasDistance && !hasTime) {
            return ValidationResult.partialResult("路线结果可能不完整");
        }

        return ValidationResult.success();
    }

    /**
     * 简单验证（仅检查基本错误）
     */
    public ValidationResult simpleValidate(String result) {
        if (result == null || result.isEmpty()) {
            return ValidationResult.failure(
                ValidationResult.ErrorType.DATA_ERROR,
                "返回结果为空"
            );
        }

        String lowerResult = result.toLowerCase();
        for (String keyword : ERROR_KEYWORDS) {
            if (lowerResult.contains(keyword)) {
                return ValidationResult.failure(
                    ValidationResult.ErrorType.DATA_ERROR,
                    "返回结果包含错误信息"
                );
            }
        }

        return ValidationResult.success();
    }
}
