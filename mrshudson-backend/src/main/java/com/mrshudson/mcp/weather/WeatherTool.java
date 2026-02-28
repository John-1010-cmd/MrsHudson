package com.mrshudson.mcp.weather;

import com.mrshudson.mcp.BaseTool;
import com.mrshudson.mcp.kimi.dto.Tool;
import com.mrshudson.service.WeatherService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 天气MCP工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherTool implements BaseTool {

    private final WeatherService weatherService;
    private final com.mrshudson.mcp.ToolRegistry toolRegistry;

    /**
     * 工具定义
     */
    private static final Tool TOOL_DEFINITION = Tool.builder()
            .type("function")
            .function(Tool.Function.builder()
                    .name("get_weather")
                    .description("获取指定城市的天气信息，包括当前天气和天气预报")
                    .parameters(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "city", Map.of(
                                            "type", "string",
                                            "description", "城市名称，如\"北京\"
                                    ),
                                    "date", Map.of(
                                            "type", "string",
                                            "description", "日期，格式为YYYY-MM-DD，不传则返回当天天气"
                                    ),
                                    "forecast_days", Map.of(
                                            "type", "integer",
                                            "description", "预报天数（1-7），不传则返回当天天气"
                                    )
                            ),
                            "required", new String[]{"city"}
                    ))
                    .build())
            .build();

    @PostConstruct
    public void register() {
        toolRegistry.registerTool(TOOL_DEFINITION, this::execute);
        log.info("天气工具已注册");
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
            String city = params.getString("city");
            String date = params.getString("date");
            Integer forecastDays = params.getInteger("forecast_days");

            if (city == null || city.isEmpty()) {
                return "错误：城市名称不能为空";
            }

            // 根据参数决定查询当前天气还是预报
            if (forecastDays != null && forecastDays > 0) {
                return weatherService.getWeatherForecast(city, forecastDays);
            } else {
                return weatherService.getCurrentWeather(city);
            }

        } catch (Exception e) {
            log.error("天气工具执行失败", e);
            return "查询天气失败: " + e.getMessage();
        }
    }
}
