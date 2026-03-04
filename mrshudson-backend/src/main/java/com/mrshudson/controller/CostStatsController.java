package com.mrshudson.controller;

import com.mrshudson.domain.dto.Result;
import com.mrshudson.optim.monitor.CostMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 成本统计控制器（管理员权限）
 * 提供系统成本统计相关的API接口
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/cost")
@RequiredArgsConstructor
public class CostStatsController {

    private final CostMonitorService costMonitorService;

    /**
     * 获取系统成本统计
     *
     * @param startDate 开始日期（可选，默认7天前）
     * @param endDate   结束日期（可选，默认今天）
     * @return 成本统计数据
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getCostStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        // 默认查询最近7天
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.minusDays(6);

        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.atTime(LocalTime.MAX);

        log.info("[CostStats] 查询系统成本统计，时间范围: {} 至 {}", start, end);

        Map<String, Object> stats = costMonitorService.getStatistics(startTime, endTime);

        // 添加查询时间范围信息
        stats.put("startDate", start.toString());
        stats.put("endDate", end.toString());

        return Result.success(stats);
    }

    /**
     * 获取指定用户成本统计
     *
     * @param userId    用户ID
     * @param startDate 开始日期（可选，默认7天前）
     * @param endDate   结束日期（可选，默认今天）
     * @return 用户成本统计数据
     */
    @GetMapping("/user/{userId}")
    public Result<Map<String, Object>> getUserCostStats(
            @PathVariable Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        // 默认查询最近7天
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.minusDays(6);

        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.atTime(LocalTime.MAX);

        log.info("[CostStats] 查询用户 {} 成本统计，时间范围: {} 至 {}", userId, start, end);

        Map<String, Object> stats = costMonitorService.getUserStatistics(userId, startTime, endTime);

        // 添加查询信息
        stats.put("userId", userId);
        stats.put("startDate", start.toString());
        stats.put("endDate", end.toString());

        return Result.success(stats);
    }

    /**
     * 获取每日成本趋势
     *
     * @param days 查询天数（可选，默认30天）
     * @return 每日成本趋势数据
     */
    @GetMapping("/daily")
    public Result<Map<String, Object>> getDailyCostTrend(
            @RequestParam(defaultValue = "30") int days) {

        // 限制查询范围，避免数据量过大
        if (days < 1) {
            days = 1;
        } else if (days > 90) {
            days = 90;
        }

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        log.info("[CostStats] 查询每日成本趋势，最近 {} 天", days);

        // 生成每日统计数据
        List<Map<String, Object>> dailyStats = new ArrayList<>();
        BigDecimal totalCost = BigDecimal.ZERO;

        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.atTime(LocalTime.MAX);

            Map<String, Object> dayStats = costMonitorService.getStatistics(dayStart, dayEnd);
            BigDecimal dayCost = (BigDecimal) dayStats.getOrDefault("totalCost", BigDecimal.ZERO);

            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", date.toString());
            dayData.put("cost", dayCost);
            dayData.put("callTypeStats", dayStats.get("callTypeStats"));

            dailyStats.add(dayData);
            totalCost = totalCost.add(dayCost);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("days", days);
        result.put("startDate", startDate.toString());
        result.put("endDate", endDate.toString());
        result.put("totalCost", totalCost);
        result.put("dailyStats", dailyStats);

        // 计算平均值
        if (days > 0) {
            BigDecimal avgCost = totalCost.divide(BigDecimal.valueOf(days), 4, BigDecimal.ROUND_HALF_UP);
            result.put("averageDailyCost", avgCost);
        }

        return Result.success(result);
    }

    /**
     * 获取今日成本（快速查询）
     *
     * @return 今日成本
     */
    @GetMapping("/today")
    public Result<Map<String, Object>> getTodayCost() {
        BigDecimal todayCost = costMonitorService.getTodayCost(null);

        Map<String, Object> result = new HashMap<>();
        result.put("date", LocalDate.now().toString());
        result.put("cost", todayCost);

        return Result.success(result);
    }

    /**
     * 获取指定用户今日成本
     *
     * @param userId 用户ID
     * @return 用户今日成本
     */
    @GetMapping("/today/{userId}")
    public Result<Map<String, Object>> getUserTodayCost(@PathVariable Long userId) {
        BigDecimal todayCost = costMonitorService.getTodayCost(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("date", LocalDate.now().toString());
        result.put("cost", todayCost);

        return Result.success(result);
    }
}
