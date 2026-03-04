package com.mrshudson.controller;

import com.mrshudson.domain.dto.Result;
import com.mrshudson.domain.dto.RouteRequest;
import com.mrshudson.domain.dto.RouteResponse;
import com.mrshudson.service.RouteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 路线规划控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/route")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    /**
     * 规划出行路线
     *
     * @param request 路线规划请求，包含起点、终点和出行方式
     * @return 路线规划结果
     */
    @PostMapping("/plan")
    public Result<RouteResponse> planRoute(@RequestBody RouteRequest request) {
        log.info("路线规划请求: 从 {} 到 {}，方式: {}",
                request.getOrigin(),
                request.getDestination(),
                request.getMode());

        // 参数校验
        if (request.getOrigin() == null || request.getOrigin().trim().isEmpty()) {
            return Result.error("起点地址不能为空");
        }
        if (request.getDestination() == null || request.getDestination().trim().isEmpty()) {
            return Result.error("终点地址不能为空");
        }

        // 调用路线规划服务
        String routeResult = routeService.planRoute(
                request.getOrigin().trim(),
                request.getDestination().trim(),
                request.getMode()
        );

        RouteResponse response = new RouteResponse(routeResult);
        return Result.success(response);
    }
}
