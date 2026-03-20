package com.mrshudson.optim.quality;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 质量优化指标收集器
 * 记录质量模式切换和优化触发情况
 */
@Slf4j
@Component
public class QualityMetrics {

    // 质量模式切换次数统计
    private final AtomicLong qualityUpgradeCount = new AtomicLong(0);
    private final AtomicLong speedModeCount = new AtomicLong(0);
    private final AtomicLong balancedModeCount = new AtomicLong(0);

    // 复杂问题检测统计
    private final AtomicLong complexQuestionCount = new AtomicLong(0);
    private final AtomicLong creativityRequestCount = new AtomicLong(0);

    // 自动优化触发统计（根据问题特征自动调整）
    private final AtomicLong autoQualityUpgradeCount = new AtomicLong(0);
    private final AtomicLong autoSpeedModeCount = new AtomicLong(0);

    /**
     * 记录质量提升触发
     * 当检测到复杂问题时触发
     */
    public void recordQualityUpgrade() {
        qualityUpgradeCount.incrementAndGet();
        autoQualityUpgradeCount.incrementAndGet();
        log.debug("记录质量提升触发，当前计数: {}", qualityUpgradeCount.get());
    }

    /**
     * 记录速度优先模式触发
     * 当检测到简单问题时触发
     */
    public void recordSpeedMode() {
        speedModeCount.incrementAndGet();
        autoSpeedModeCount.incrementAndGet();
        log.debug("记录速度模式触发，当前计数: {}", speedModeCount.get());
    }

    /**
     * 记录平衡模式触发
     */
    public void recordBalancedMode() {
        balancedModeCount.incrementAndGet();
        log.debug("记录平衡模式触发，当前计数: {}", balancedModeCount.get());
    }

    /**
     * 记录复杂问题检测
     */
    public void recordComplexQuestion() {
        complexQuestionCount.incrementAndGet();
    }

    /**
     * 记录创意请求检测
     */
    public void recordCreativityRequest() {
        creativityRequestCount.incrementAndGet();
    }

    /**
     * 手动设置质量模式时的记录
     */
    public void recordManualMode(QualityProperties.Mode mode) {
        switch (mode) {
            case QUALITY -> qualityUpgradeCount.incrementAndGet();
            case SPEED -> speedModeCount.incrementAndGet();
            case BALANCED -> balancedModeCount.incrementAndGet();
        }
        log.info("手动设置质量模式: {}, 当前各模式计数 - QUALITY: {}, SPEED: {}, BALANCED: {}",
            mode, qualityUpgradeCount.get(), speedModeCount.get(), balancedModeCount.get());
    }

    /**
     * 获取当前指标快照
     */
    public MetricsSnapshot getSnapshot() {
        MetricsSnapshot snapshot = new MetricsSnapshot();
        snapshot.setQualityUpgradeCount(qualityUpgradeCount.get());
        snapshot.setSpeedModeCount(speedModeCount.get());
        snapshot.setBalancedModeCount(balancedModeCount.get());
        snapshot.setComplexQuestionCount(complexQuestionCount.get());
        snapshot.setCreativityRequestCount(creativityRequestCount.get());
        snapshot.setAutoQualityUpgradeCount(autoQualityUpgradeCount.get());
        snapshot.setAutoSpeedModeCount(autoSpeedModeCount.get());

        // 计算各模式占比
        long total = snapshot.getQualityUpgradeCount() + snapshot.getSpeedModeCount() + snapshot.getBalancedModeCount();
        if (total > 0) {
            snapshot.setQualityPercentage((snapshot.getQualityUpgradeCount() * 100.0) / total);
            snapshot.setSpeedPercentage((snapshot.getSpeedModeCount() * 100.0) / total);
            snapshot.setBalancedPercentage((snapshot.getBalancedModeCount() * 100.0) / total);
        }

        return snapshot;
    }

    /**
     * 重置统计
     */
    public void reset() {
        qualityUpgradeCount.set(0);
        speedModeCount.set(0);
        balancedModeCount.set(0);
        complexQuestionCount.set(0);
        creativityRequestCount.set(0);
        autoQualityUpgradeCount.set(0);
        autoSpeedModeCount.set(0);
        log.info("质量指标已重置");
    }

    /**
     * 指标快照
     */
    public static class MetricsSnapshot {
        private long qualityUpgradeCount;
        private long speedModeCount;
        private long balancedModeCount;
        private long complexQuestionCount;
        private long creativityRequestCount;
        private long autoQualityUpgradeCount;
        private long autoSpeedModeCount;
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
        public long getAutoQualityUpgradeCount() { return autoQualityUpgradeCount; }
        public void setAutoQualityUpgradeCount(long autoQualityUpgradeCount) { this.autoQualityUpgradeCount = autoQualityUpgradeCount; }
        public long getAutoSpeedModeCount() { return autoSpeedModeCount; }
        public void setAutoSpeedModeCount(long autoSpeedModeCount) { this.autoSpeedModeCount = autoSpeedModeCount; }
        public double getQualityPercentage() { return qualityPercentage; }
        public void setQualityPercentage(double qualityPercentage) { this.qualityPercentage = qualityPercentage; }
        public double getSpeedPercentage() { return speedPercentage; }
        public void setSpeedPercentage(double speedPercentage) { this.speedPercentage = speedPercentage; }
        public double getBalancedPercentage() { return balancedPercentage; }
        public void setBalancedPercentage(double balancedPercentage) { this.balancedPercentage = balancedPercentage; }
    }
}
