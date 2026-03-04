package com.mrshudson.optim.monitor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

/**
 * 成本监控定时任务
 * 定期执行成本统计和告警检查
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CostMonitorScheduler {

    private final CostMonitorService costMonitorService;

    /**
     * 每小时执行成本统计
     * 记录当前小时成本，用于趋势分析
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void hourlyCostStatistics() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime hourStart = now.withMinute(0).withSecond(0).withNano(0);
            LocalDateTime hourEnd = now.withMinute(59).withSecond(59).withNano(999999999);

            Map<String, Object> stats = costMonitorService.getStatistics(hourStart, hourEnd);
            BigDecimal hourCost = (BigDecimal) stats.getOrDefault("totalCost", BigDecimal.ZERO);

            log.info("[CostScheduler] 小时成本统计 - 时间: {}, 成本: {} 元", hourStart, hourCost);

            // 记录缓存命中率
            Object cacheHitRate = stats.get("cacheHitRate");
            if (cacheHitRate != null) {
                log.info("[CostScheduler] 缓存命中率: {}%", cacheHitRate);
            }

        } catch (Exception e) {
            log.error("[CostScheduler] 小时成本统计执行失败", e);
            // 异常不影响主流程
        }
    }

    /**
     * 每日0点检查昨日成本是否超过阈值
     * 如果超过阈值则触发告警
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void dailyCostAlertCheck() {
        try {
            // 获取昨天的日期
            LocalDate yesterday = LocalDate.now().minusDays(1);
            LocalDateTime dayStart = yesterday.atStartOfDay();
            LocalDateTime dayEnd = yesterday.atTime(LocalTime.MAX);

            log.info("[CostScheduler] 开始执行每日成本告警检查，日期: {}", yesterday);

            // 检查系统总成本
            Map<String, Object> stats = costMonitorService.getStatistics(dayStart, dayEnd);
            BigDecimal totalCost = (BigDecimal) stats.getOrDefault("totalCost", BigDecimal.ZERO);

            log.info("[CostScheduler] 昨日系统总成本: {} 元", totalCost);

            // 触发告警检查（userId为null表示检查系统总成本）
            costMonitorService.checkAndAlert(null);

            // 记录统计摘要
            Object callTypeStats = stats.get("callTypeStats");
            if (callTypeStats != null) {
                log.info("[CostScheduler] 昨日调用类型统计: {}", callTypeStats);
            }

            Object routerLayerStats = stats.get("routerLayerStats");
            if (routerLayerStats != null) {
                log.info("[CostScheduler] 昨日路由层统计: {}", routerLayerStats);
            }

            Object cacheHitRate = stats.get("cacheHitRate");
            if (cacheHitRate != null) {
                log.info("[CostScheduler] 昨日缓存命中率: {}%", cacheHitRate);
            }

            log.info("[CostScheduler] 每日成本告警检查完成");

        } catch (Exception e) {
            log.error("[CostScheduler] 每日成本告警检查执行失败", e);
            // 异常不影响主流程
        }
    }

    /**
     * 每日1点生成成本日报
     * 汇总前一天的详细成本数据
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void generateDailyReport() {
        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            LocalDateTime dayStart = yesterday.atStartOfDay();
            LocalDateTime dayEnd = yesterday.atTime(LocalTime.MAX);

            log.info("[CostScheduler] 生成成本日报，日期: {}", yesterday);

            Map<String, Object> stats = costMonitorService.getStatistics(dayStart, dayEnd);

            BigDecimal totalCost = (BigDecimal) stats.getOrDefault("totalCost", BigDecimal.ZERO);
            Object callTypeStats = stats.get("callTypeStats");
            Object routerLayerStats = stats.get("routerLayerStats");
            Object cacheHitRate = stats.get("cacheHitRate");

            log.info("[CostScheduler] 成本日报 [{}] - 总成本: {} 元, 缓存命中率: {}%",
                    yesterday, totalCost, cacheHitRate);

            if (callTypeStats != null) {
                log.info("[CostScheduler] 成本日报 [{}] - 调用类型分布: {}",
                        yesterday, callTypeStats);
            }

            if (routerLayerStats != null) {
                log.info("[CostScheduler] 成本日报 [{}] - 路由层分布: {}",
                        yesterday, routerLayerStats);
            }

        } catch (Exception e) {
            log.error("[CostScheduler] 生成成本日报失败", e);
            // 异常不影响主流程
        }
    }
}
