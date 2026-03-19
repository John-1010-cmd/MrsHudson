package com.mrshudson.optim.correction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证结果单元测试
 */
class ValidationResultTest {

    @Nested
    @DisplayName("静态工厂方法测试")
    class FactoryMethodTests {

        @Test
        @DisplayName("success应创建有效结果")
        void successShouldCreateValidResult() {
            ValidationResult result = ValidationResult.success();

            assertTrue(result.isValid());
            assertEquals(ValidationResult.ErrorType.NONE, result.getErrorType());
            assertEquals(1.0, result.getConfidence());
            assertEquals(0, result.getRetryCount());
            assertNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("failure应创建无效结果")
        void failureShouldCreateInvalidResult() {
            ValidationResult result = ValidationResult.failure(
                ValidationResult.ErrorType.DATA_ERROR, "数据为空");

            assertFalse(result.isValid());
            assertEquals(ValidationResult.ErrorType.DATA_ERROR, result.getErrorType());
            assertEquals("数据为空", result.getErrorMessage());
            assertEquals(0.0, result.getConfidence());
            assertEquals(0, result.getRetryCount());
        }

        @Test
        @DisplayName("formatError应创建格式错误结果")
        void formatErrorShouldCreateFormatErrorResult() {
            ValidationResult result = ValidationResult.formatError("JSON解析失败");

            assertFalse(result.isValid());
            assertEquals(ValidationResult.ErrorType.FORMAT_ERROR, result.getErrorType());
            assertEquals("JSON解析失败", result.getErrorMessage());
        }

        @Test
        @DisplayName("dataError应创建数据错误结果")
        void dataErrorShouldCreateDataErrorResult() {
            ValidationResult result = ValidationResult.dataError("温度超出范围");

            assertFalse(result.isValid());
            assertEquals(ValidationResult.ErrorType.DATA_ERROR, result.getErrorType());
        }

        @Test
        @DisplayName("timeout应创建超时结果")
        void timeoutShouldCreateTimeoutResult() {
            ValidationResult result = ValidationResult.timeout();

            assertFalse(result.isValid());
            assertEquals(ValidationResult.ErrorType.TIMEOUT, result.getErrorType());
            assertEquals("请求超时", result.getErrorMessage());
        }

        @Test
        @DisplayName("partialResult应创建部分结果")
        void partialResultShouldCreatePartialResult() {
            ValidationResult result = ValidationResult.partialResult("只返回了部分数据");

            assertFalse(result.isValid());
            assertEquals(ValidationResult.ErrorType.PARTIAL_RESULT, result.getErrorType());
            assertEquals(0.5, result.getConfidence());
        }
    }

    @Nested
    @DisplayName("getRetryCount - 重试次数测试")
    class RetryCountTests {

        @Test
        @DisplayName("默认retryCount应为0")
        void defaultRetryCountShouldBeZero() {
            ValidationResult result = ValidationResult.success();
            assertEquals(0, result.getRetryCount());
        }

        @Test
        @DisplayName("failure创建的result应有默认retryCount")
        void failureResultShouldHaveDefaultRetryCount() {
            ValidationResult result = ValidationResult.failure(
                ValidationResult.ErrorType.DATA_ERROR, "error");
            assertEquals(0, result.getRetryCount());
        }
    }
}
