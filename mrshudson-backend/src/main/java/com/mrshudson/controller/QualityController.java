package com.mrshudson.controller;

import com.mrshudson.domain.dto.Result;
import com.mrshudson.optim.quality.QualityMetrics;
import com.mrshudson.optim.quality.QualityOptimizer;
import com.mrshudson.optim.quality.QualityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 质量模式控制层
 * 提供质量模式的查询和设置功能
 */
@Slf4j
@RestController
@RequestMapping("/api/quality")
@RequiredArgsConstructor
public class QualityController {

    private final QualityOptimizer qualityOptimizer;
    private final QualityProperties qualityProperties;
    private final QualityMetrics qualityMetrics;

    /**
     * 获取当前质量模式
     */
    @GetMapping("/mode")
    public Result<QualityModeResponse> getCurrentMode() {
        QualityProperties.Mode mode = qualityOptimizer.getCurrentMode();

        QualityModeResponse response = new QualityModeResponse();
        response.setMode(mode.name());
        response.setMaxTokens(qualityProperties.getMaxTokens());
        response.setTemperature(qualityProperties.getTemperature());
        response.setEnableFullContext(qualityProperties.isEnableFullContext());

        // 获取当前指标快照
        QualityMetrics.MetricsSnapshot metricsSnapshot = qualityMetrics.getSnapshot();
        QualityModeResponse.Metrics metrics = new QualityModeResponse.Metrics();
        metrics.setQualityUpgradeCount(metricsSnapshot.getQualityUpgradeCount());
        metrics.setSpeedModeCount(metricsSnapshot.getSpeedModeCount());
        metrics.setBalancedModeCount(metricsSnapshot.getBalancedModeCount());
        metrics.setComplexQuestionCount(metricsSnapshot.getComplexQuestionCount());
        metrics.setCreativityRequestCount(metricsSnapshot.getCreativityRequestCount());
        metrics.setQualityPercentage(metricsSnapshot.getQualityPercentage());
        metrics.setSpeedPercentage(metricsSnapshot.getSpeedPercentage());
        metrics.setBalancedPercentage(metricsSnapshot.getBalancedPercentage());
        response.setMetrics(metrics);

        log.info("获取当前质量模式: {}", mode);
        return Result.success(response);
    }

    /**
     * 设置质量模式
     */
    @PutMapping("/mode")
    public Result<QualityModeResponse> setMode(@RequestBody SetQualityModeRequest request) {
        try {
            QualityProperties.Mode targetMode = QualityProperties.Mode.valueOf(request.getMode().toUpperCase());

            // 设置模式
            qualityOptimizer.setMode(targetMode);

            // 记录手动模式切换
            qualityMetrics.recordManualMode(targetMode);

            // 返回更新后的信息
            QualityModeResponse response = new QualityModeResponse();
            response.setMode(targetMode.name());
            response.setMaxTokens(qualityProperties.getMaxTokens());
            response.setTemperature(qualityProperties.getTemperature());
            response.setEnableFullContext(qualityProperties.isEnableFullContext());

            log.info("设置质量模式为: {}", targetMode);
            return Result.success(response);
        } catch (IllegalArgumentException e) {
            log.warn("无效的质量模式: {}", request.getMode());
            return Result.error("无效的质量模式，可选值: SPEED, BALANCED, QUALITY");
        }
    }

    /**
     * 获取质量指标统计
     */
    @GetMapping("/metrics")
    public Result<QualityMetrics.MetricsSnapshot> getMetrics() {
        return Result.success(qualityMetrics.getSnapshot());
    }

    /**
     * 重置质量指标统计
     */
    @PostMapping("/metrics/reset")
    public Result<String> resetMetrics() {
        qualityMetrics.reset();
        log.info("质量指标已重置");
        return Result.success("指标已重置");
    }

    /**
     * 质量模式响应
     */
    public static class QualityModeResponse {
        private String mode;
        private int maxTokens;
        private double temperature;
        private boolean enableFullContext;
        private Metrics metrics;

        // Getters and Setters
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public boolean isEnableFullContext() { return enableFullContext; }
        public void setEnableFullContext(boolean enableFullContext) { this.enableFullContext = enableFullContext; }
        public Metrics getMetrics() { return metrics; }
        public void setMetrics(Metrics metrics) { this.metrics = metrics; }

        /**
         * 指标详情
         */
        public static class Metrics {
            private long qualityUpgradeCount;
            private long speedModeCount;
            private long balancedModeCount;
            private long complexQuestionCount;
            private long creativityRequestCount;
            private double qualityPercentage;
            private double speedPercentage;
            private double balancedPercentage;

            // Getters and Setters
            public long getQualityUpgradeCount() { return qualityUpgradeCount; }
            public void setQualityUpgradeCount(long qualityUpgradeCount) { this.qualityUpgradeCount = qualityUpgradeCount; }
            public long getSpeedModeCount() { return speedModeCount; }
            public void setSpeedModeCount(long speedModeCount) { this.speedModeCount = speedModeCount; }
            public long getBalancedModeCount() { return balancedModeCount; }
            public void setBalancedModeCount(long balancedModeCount) { this.balancedModeCount = balancedModeCount; }
            public long getComplexQuestionCount() { return complexQuestionCount; }
            public void setComplexQuestionCount(long complexQuestionCount) { this.complexQuestionCount = complexQuestionCount; }
            public long getCreativityRequestCount() { return creativityRequestCount; }
            public void setCreativityRequestCount(long creativityRequestCount) { this.creativityRequestCount = creativityRequestCount; }
            public double getQualityPercentage() { return qualityPercentage; }
            public void setQualityPercentage(double qualityPercentage) { this.qualityPercentage = qualityPercentage; }
            public double getSpeedPercentage() { return speedPercentage; }
            public void setSpeedPercentage(double speedPercentage) { this.speedPercentage = speedPercentage; }
            public double getBalancedPercentage() { return balancedPercentage; }
            public void setBalancedPercentage(double balancedPercentage) { this.balancedPercentage = balancedPercentage; }
        }
    }

    /**
     * 设置质量模式请求
     */
    public static class SetQualityModeRequest {
        private String mode;

        // Getters and Setters
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
    }
}
