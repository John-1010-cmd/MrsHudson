package com.mrshudson.controller;

import com.mrshudson.domain.dto.Result;
import com.mrshudson.optim.monitor.MetricsService;
import com.mrshudson.optim.monitor.MetricsService.DailyMetrics;
import com.mrshudson.optim.monitor.MetricsService.MetricsSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 指标数据控制器
 * 提供优化效果实际数据的API接口
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsService metricsService;

    /**
     * 获取当前指标快照
     */
    @GetMapping("/current")
    public Result<MetricsSnapshot> getCurrentMetrics() {
        MetricsSnapshot snapshot = metricsService.getCurrentMetrics();
        return Result.success(snapshot);
    }

    /**
     * 获取趋势数据
     */
    @GetMapping("/trend")
    public Result<List<DailyMetrics>> getTrend(
            @RequestParam(defaultValue = "7") int days) {
        List<DailyMetrics> trend = metricsService.getTrend(days);
        return Result.success(trend);
    }

    /**
     * 获取日统计
     */
    @GetMapping("/daily/{date}")
    public Result<DailyMetrics> getDailyMetrics(@PathVariable String date) {
        LocalDate localDate = LocalDate.parse(date);
        DailyMetrics metrics = metricsService.getDailyMetrics(localDate);
        return Result.success(metrics);
    }

    /**
     * 获取对比数据（优化前后）
     */
    @GetMapping("/comparison")
    public Result<Map<String, Object>> getComparison() {
        Map<String, Object> comparison = new HashMap<>();

        // 理论值
        Map<String, Object> before = new HashMap<>();
        before.put("aiCallCount", 10000);
        before.put("avgResponseTime", 3.0);
        before.put("monthlyCost", 3000.0);
        before.put("toolQueryZeroAiRate", 0.0);

        // 当前实际值（从服务获取）
        MetricsSnapshot snapshot = metricsService.getCurrentMetrics();
        Map<String, Object> after = new HashMap<>();
        after.put("semanticCacheHitRate", snapshot.getSemanticCacheHitRate());
        after.put("toolCacheHitRate", snapshot.getToolCacheHitRate());
        after.put("intentLayerStats", snapshot.getIntentLayerStats());
        after.put("intentLayerPercentages", snapshot.getIntentLayerPercentages());

        comparison.put("before", before);
        comparison.put("after", after);
        comparison.put("theoreticalSavings", 75.0); // 理论节省75%

        return Result.success(comparison);
    }

    /**
     * 重置统计
     */
    @PostMapping("/reset")
    public Result<Void> resetStats() {
        metricsService.resetStats();
        log.info("Metrics stats reset by admin");
        return Result.success();
    }
}
