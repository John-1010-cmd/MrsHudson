package com.mrshudson.controller;

import com.mrshudson.domain.dto.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 质量模式控制层（旧路径兼容）
 *
 * @deprecated 请使用新路径 /api/admin/quality/*
 * 保留此控制器是为了向后兼容，将在未来版本中移除
 */
@Deprecated(since = "2026-04-04", forRemoval = true)
@Slf4j
@RestController
@RequestMapping("/api/quality")
@RequiredArgsConstructor
public class QualityControllerDeprecated {

    private final QualityController qualityController;

    /**
     * 获取当前质量模式
     *
     * @deprecated 请使用 GET /api/admin/quality/mode
     */
    @Deprecated(since = "2026-04-04", forRemoval = true)
    @GetMapping("/mode")
    public Result<QualityController.QualityModeResponse> getCurrentMode() {
        log.warn("使用已废弃的 API 路径: /api/quality/mode，请迁移到 /api/admin/quality/mode");
        return qualityController.getCurrentMode();
    }

    /**
     * 设置质量模式
     *
     * @deprecated 请使用 PUT /api/admin/quality/mode
     */
    @Deprecated(since = "2026-04-04", forRemoval = true)
    @PutMapping("/mode")
    public Result<QualityController.QualityModeResponse> setMode(@RequestBody QualityController.SetQualityModeRequest request) {
        log.warn("使用已废弃的 API 路径: /api/quality/mode，请迁移到 /api/admin/quality/mode");
        return qualityController.setMode(request);
    }

    /**
     * 获取质量指标统计
     *
     * @deprecated 请使用 GET /api/admin/quality/metrics
     */
    @Deprecated(since = "2026-04-04", forRemoval = true)
    @GetMapping("/metrics")
    public Result<?> getMetrics() {
        log.warn("使用已废弃的 API 路径: /api/quality/metrics，请迁移到 /api/admin/quality/metrics");
        return qualityController.getMetrics();
    }

    /**
     * 重置质量指标统计
     *
     * @deprecated 请使用 POST /api/admin/quality/metrics/reset
     */
    @Deprecated(since = "2026-04-04", forRemoval = true)
    @PostMapping("/metrics/reset")
    public Result<String> resetMetrics() {
        log.warn("使用已废弃的 API 路径: /api/quality/metrics/reset，请迁移到 /api/admin/quality/metrics/reset");
        return qualityController.resetMetrics();
    }
}
