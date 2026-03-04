package com.mrshudson.optim.intent.handler;

import com.mrshudson.optim.intent.IntentType;
import com.mrshudson.optim.intent.RouteResult;
import com.mrshudson.service.WeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 天气查询意图处理器
 * 识别天气相关查询，提取城市参数，返回天气信息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherIntentHandler extends AbstractIntentHandler {

    private final WeatherService weatherService;

    // 城市名提取正则模式
    private static final Pattern CITY_PATTERN = Pattern.compile(
            "(北京|上海|广州|深圳|杭州|南京|成都|武汉|西安|重庆|天津|苏州|郑州|长沙|沈阳|青岛|宁波|东莞|厦门|福州|昆明|合肥|济南|哈尔滨|长春|大连|石家庄|太原|南昌|南宁|贵阳|兰州|海口|乌鲁木齐|拉萨|银川|呼和浩特|西宁|[\u4e00-\u9fa5]{2,10}(?:市|县|区)?)"
    );

    // 天气关键词
    private static final String[] WEATHER_KEYWORDS = {
            "天气", "温度", "下雨", "雪", "风", "晴", "阴", "多云", "雾", "霾"
    };

    @Override
    public IntentType getIntentType() {
        return IntentType.WEATHER_QUERY;
    }

    @Override
    protected RouteResult doHandle(Long userId, String query, Map<String, Object> parameters) {
        // 提取城市名，默认北京
        String city = extractCity(query);
        if (city == null || city.isEmpty()) {
            city = "北京";
        }

        log.debug("提取城市参数: {}, 原始查询: {}", city, query);

        // 调用天气服务查询
        String weatherResult = weatherService.getCurrentWeather(city);

        // 构建参数Map
        Map<String, Object> resultParams = new HashMap<>();
        resultParams.put("city", city);
        resultParams.put("query", query);

        // 返回结果，固定置信度0.95
        return RouteResult.builder()
                .handled(true)
                .response(weatherResult)
                .intentType(IntentType.WEATHER_QUERY)
                .confidence(0.95)
                .parameters(resultParams)
                .routerLayer("rule")
                .build();
    }

    /**
     * 从查询中提取城市名
     */
    private String extractCity(String query) {
        if (query == null || query.isEmpty()) {
            return null;
        }

        // 使用正则匹配城市名
        Matcher matcher = CITY_PATTERN.matcher(query);
        if (matcher.find()) {
            String city = matcher.group(1);
            // 去除"市"、"县"、"区"后缀，保留核心名称
            if (city.endsWith("市") || city.endsWith("县") || city.endsWith("区")) {
                city = city.substring(0, city.length() - 1);
            }
            return city;
        }

        return null;
    }

    @Override
    public double canHandle(String query) {
        if (query == null || query.isEmpty()) {
            return 0.0;
        }

        String lowerQuery = query.toLowerCase();
        int matchCount = 0;

        // 检查天气关键词
        for (String keyword : WEATHER_KEYWORDS) {
            if (lowerQuery.contains(keyword)) {
                matchCount++;
            }
        }

        if (matchCount == 0) {
            return 0.0;
        }

        // 天气查询固定返回高置信度0.95
        return 0.95;
    }
}
