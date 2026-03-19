package com.mrshudson.optim.correction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具结果校验器单元测试
 */
class ToolResultValidatorTest {

    private ToolResultValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ToolResultValidator();
    }

    @Nested
    @DisplayName("validate - 基本验证测试")
    class ValidateTests {

        @Test
        @DisplayName("空结果应返回失败")
        void emptyResultShouldReturnFailure() {
            ValidationResult result = validator.validate("test_tool", "", null);

            assertFalse(result.isValid());
            assertEquals(ValidationResult.ErrorType.DATA_ERROR, result.getErrorType());
        }

        @Test
        @DisplayName("null结果应返回失败")
        void nullResultShouldReturnFailure() {
            ValidationResult result = validator.validate("test_tool", null, null);

            assertFalse(result.isValid());
            assertEquals(ValidationResult.ErrorType.DATA_ERROR, result.getErrorType());
        }

        @Test
        @DisplayName("包含error关键词应返回失败")
        void resultWithErrorKeywordShouldReturnFailure() {
            ValidationResult result = validator.validate("test_tool", "Something went error", null);

            assertFalse(result.isValid());
            assertEquals(ValidationResult.ErrorType.DATA_ERROR, result.getErrorType());
        }

        @Test
        @DisplayName("包含中文失败应返回失败")
        void resultWithChineseFailShouldReturnFailure() {
            ValidationResult result = validator.validate("test_tool", "操作失败", null);

            assertFalse(result.isValid());
        }

        @Test
        @DisplayName("正常结果应返回成功")
        void normalResultShouldReturnSuccess() {
            ValidationResult result = validator.validate("test_tool", "这是一个正常的返回结果", null);

            assertTrue(result.isValid());
        }
    }

    @Nested
    @DisplayName("validate - 天气工具验证测试")
    class WeatherValidationTests {

        @Test
        @DisplayName("包含温度应返回成功")
        void resultWithTemperatureShouldReturnSuccess() {
            ValidationResult result = validator.validate("weather", "北京今天25°C，晴", null);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("包含°C应返回成功")
        void resultWithCelsiusShouldReturnSuccess() {
            ValidationResult result = validator.validate("weather", "温度30°C", null);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("包含天气关键词应返回成功")
        void resultWithWeatherKeywordShouldReturnSuccess() {
            ValidationResult result = validator.validate("weather", "今天多云转晴", null);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("不包含温度和天气信息应返回部分结果")
        void resultWithoutTemperatureOrWeatherShouldReturnPartial() {
            ValidationResult result = validator.validate("weather", "今天是个好日子", null);

            assertFalse(result.isValid());
            assertEquals(ValidationResult.ErrorType.PARTIAL_RESULT, result.getErrorType());
        }
    }

    @Nested
    @DisplayName("validate - 日历工具验证测试")
    class CalendarValidationTests {

        @Test
        @DisplayName("包含日期信息应返回成功")
        void resultWithDateInfoShouldReturnSuccess() {
            ValidationResult result = validator.validate("calendar", "今天下午3点有个会议", null);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("包含时间关键词应返回成功")
        void resultWithTimeKeywordShouldReturnSuccess() {
            ValidationResult result = validator.validate("calendar", "14号安排", null);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("短文本无时间信息应返回部分结果")
        void shortTextWithoutTimeInfoShouldReturnPartial() {
            ValidationResult result = validator.validate("calendar", "会议", null);

            assertFalse(result.isValid());
            assertEquals(ValidationResult.ErrorType.PARTIAL_RESULT, result.getErrorType());
        }
    }

    @Nested
    @DisplayName("validate - 待办工具验证测试")
    class TodoValidationTests {

        @Test
        @DisplayName("包含列表标记应返回成功")
        void resultWithListMarkersShouldReturnSuccess() {
            ValidationResult result = validator.validate("todo", "1. 完成报告\n2. 发送邮件", null);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("包含圆点标记应返回成功")
        void resultWithBulletMarkersShouldReturnSuccess() {
            ValidationResult result = validator.validate("todo", "• 任务一\n• 任务二", null);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("短文本无列表标记应返回部分结果")
        void shortTextWithoutListMarkersShouldReturnPartial() {
            ValidationResult result = validator.validate("todo", "待办", null);

            assertFalse(result.isValid());
            assertEquals(ValidationResult.ErrorType.PARTIAL_RESULT, result.getErrorType());
        }
    }

    @Nested
    @DisplayName("validate - 路线工具验证测试")
    class RouteValidationTests {

        @Test
        @DisplayName("包含公里数应返回成功")
        void resultWithDistanceShouldReturnSuccess() {
            ValidationResult result = validator.validate("route", "全程10公里", null);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("包含分钟数应返回成功")
        void resultWithMinutesShouldReturnSuccess() {
            ValidationResult result = validator.validate("route", "预计30分钟", null);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("不包含距离和时间应返回部分结果")
        void resultWithoutDistanceOrTimeShouldReturnPartial() {
            ValidationResult result = validator.validate("route", "路线规划中", null);

            assertFalse(result.isValid());
            assertEquals(ValidationResult.ErrorType.PARTIAL_RESULT, result.getErrorType());
        }
    }

    @Nested
    @DisplayName("simpleValidate - 简单验证测试")
    class SimpleValidateTests {

        @Test
        @DisplayName("空值应返回失败")
        void emptyValueShouldReturnFailure() {
            ValidationResult result = validator.simpleValidate("");

            assertFalse(result.isValid());
        }

        @Test
        @DisplayName("包含error应返回失败")
        void valueWithErrorShouldReturnFailure() {
            ValidationResult result = validator.simpleValidate("null result");

            assertFalse(result.isValid());
        }

        @Test
        @DisplayName("正常文本应返回成功")
        void normalTextShouldReturnSuccess() {
            ValidationResult result = validator.simpleValidate("这是一个正常的返回结果");

            assertTrue(result.isValid());
        }
    }
}
