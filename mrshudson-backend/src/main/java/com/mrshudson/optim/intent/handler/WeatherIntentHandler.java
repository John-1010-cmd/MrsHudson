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
    // 优先匹配"XX天气"或"XX的天气"格式，要求以"天气"相关词结尾
    private static final Pattern CITY_PATTERN = Pattern.compile(
            "([\\u4e00-\\u9fa5]{2,6})(?:的)?天气|" +  // XX天气 或 XX的天气
            "(北京|上海|广州|深圳|杭州|南京|苏州|成都|武汉|西安|重庆|天津|青岛|大连|厦门|宁波|无锡|佛山|东莞|郑州|长沙|沈阳|济南|哈尔滨|长春|石家庄|太原|合肥|南昌|昆明|贵阳|南宁|兰州|海口|银川|西宁|拉萨|乌鲁木齐|呼和浩特|汕头|珠海|中山|惠州|江门|湛江|茂名|肇庆|梅州|汕尾|河源|阳江|清远|潮州|揭阳|云浮)"
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
        // 优先使用ParameterExtractor已提取的城市参数，如果没有则尝试从query中提取
        String city = (String) parameters.get("city");
        if (city == null || city.isEmpty()) {
            // 如果参数中没有城市，尝试从query中提取
            city = extractCity(query);
        }
        if (city == null || city.isEmpty()) {
            city = "北京";
        }

        // 提取日期参数，判断是查询当天天气还是预报
        String dateStr = (String) parameters.get("date");
        String dateType = (String) parameters.get("dateType");
        String weatherResult;

        // 判断是否需要查询预报（如果用户问的是明天、后天等）
        if (dateStr != null && dateType != null && !"今天".equals(dateType)) {
            // 查询预报天气
            weatherResult = weatherService.getWeatherForecast(city, 7);
        } else {
            // 查询当前天气
            weatherResult = weatherService.getCurrentWeather(city);
        }

        log.debug("提取城市参数: {}, 日期: {} ({})，原始查询: {}", city, dateStr, dateType, query);

        // 构建参数Map
        Map<String, Object> resultParams = new HashMap<>();
        resultParams.put("city", city);
        resultParams.put("query", query);
        if (dateStr != null) {
            resultParams.put("date", dateStr);
            resultParams.put("dateType", dateType);
        }

        // 检查天气结果是否是错误
        boolean isError = weatherResult != null && (
            weatherResult.contains("未找到") ||
            weatherResult.contains("失败") ||
            weatherResult.contains("无法")
        );

        // 如果是错误，返回 handled(false) 以触发下一层处理（兜底）
        return RouteResult.builder()
                .handled(!isError)  // 错误时返回false，触发fallback
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
            // group(1) 是动态城市模式匹配结果，group(2) 是特定城市列表匹配结果
            String city = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (city == null || city.isEmpty()) {
                return null;
            }
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

        // 检查天气核心关键词 - 必须包含"天气"才算天气查询
        boolean hasWeatherCore = lowerQuery.contains("天气");

        // 检查其他天气相关词
        boolean hasWeatherRelated = false;
        for (String keyword : new String[]{"温度", "下雨", "下雪", "刮风", "晴天", "阴天", "多云", "雾", "霾", "气温"}) {
            if (lowerQuery.contains(keyword)) {
                hasWeatherRelated = true;
                break;
            }
        }

        // 必须有"天气"核心词或同时有天气相关词
        if (!hasWeatherCore && !hasWeatherRelated) {
            return 0.0;
        }

        // 有明确的"天气"关键词，置信度0.95
        if (hasWeatherCore) {
            return 0.95;
        }

        // 只有其他天气相关词，置信度较低
        return 0.7;
    }
}
