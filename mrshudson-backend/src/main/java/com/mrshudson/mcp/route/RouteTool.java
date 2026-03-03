package com.mrshudson.mcp.route;

import com.mrshudson.mcp.BaseTool;
import com.mrshudson.mcp.ToolRegistry;
import com.mrshudson.mcp.kimi.dto.Tool;
import com.mrshudson.service.RouteService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 路线规划MCP工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RouteTool implements BaseTool {

    private final RouteService routeService;
    private final ToolRegistry toolRegistry;

    /**
     * 工具定义
     */
    private static final Tool TOOL_DEFINITION = Tool.builder()
            .type("function")
            .function(Tool.Function.builder()
                    .name("plan_route")
                    .description("规划从起点到终点的出行路线，支持步行、驾车和公交三种方式")
                    .parameters(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "origin", Map.of(
                                            "type", "string",
                                            "description", "起点地址，如\"北京市朝阳区国贸\"、\"天安门\"、\"我家\"等"
                                    ),
                                    "destination", Map.of(
                                            "type", "string",
                                            "description", "终点地址，如\"北京市海淀区中关村\"、\"机场\"等"
                                    ),
                                    "mode", Map.of(
                                            "type", "string",
                                            "description", "出行方式：walking(步行)、driving(驾车)、transit(公交)。不传则默认驾车",
                                            "enum", new String[]{"walking", "driving", "transit"}
                                    )
                            ),
                            "required", new String[]{"origin", "destination"}
                    ))
                    .build())
            .build();

    @PostConstruct
    public void register() {
        toolRegistry.registerTool(TOOL_DEFINITION, this::execute);
        log.info("路线规划工具已注册");
    }

    @Override
    public Tool getToolDefinition() {
        return TOOL_DEFINITION;
    }

    @Override
    public String execute(String arguments) {
        try {
            // 解析参数
            com.alibaba.fastjson2.JSONObject params = com.alibaba.fastjson2.JSON.parseObject(arguments);
            String origin = params.getString("origin");
            String destination = params.getString("destination");
            String mode = params.getString("mode");

            if (origin == null || origin.isEmpty()) {
                return "错误：起点地址不能为空";
            }
            if (destination == null || destination.isEmpty()) {
                return "错误：终点地址不能为空";
            }

            log.info("规划路线：{} -> {}，方式：{}", origin, destination, mode);

            // 调用路线规划服务
            return routeService.planRoute(origin, destination, mode);

        } catch (Exception e) {
            log.error("路线规划工具执行失败", e);
            return "路线规划失败: " + e.getMessage();
        }
    }
}
