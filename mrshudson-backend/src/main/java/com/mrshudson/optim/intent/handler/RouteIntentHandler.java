package com.mrshudson.optim.intent.handler;

import com.mrshudson.optim.intent.IntentType;
import com.mrshudson.optim.intent.RouteResult;
import com.mrshudson.service.RouteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 路线规划意图处理器
 * 识别路线规划相关查询，提取起点和终点参数，返回路线信息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RouteIntentHandler extends AbstractIntentHandler {

    private final RouteService routeService;

    // 路线规划关键词
    private static final String[] ROUTE_KEYWORDS = {
            "路线", "怎么去", "怎么走", "导航", "地图", "从", "到", "去"
    };

    // 起点终点提取正则模式
    // 支持 "从X到Y"、"从X去Y"、"X到Y怎么走"、"怎么去X" 等格式
    private static final Pattern FROM_TO_PATTERN = Pattern.compile(
            "(?:从)?([^到去走哪]+)(?:到|去|走|往)([^哪]+)(?:怎么|怎么走|怎么去|怎么走|吗)?"
    );

    // 简单目的地提取: "怎么去天安门"
    private static final Pattern TO_ONLY_PATTERN = Pattern.compile(
            "(?:怎么|如何)(?:去|到|走)([^哪]+)"
    );

    @Override
    public IntentType getIntentType() {
        return IntentType.ROUTE_QUERY;
    }

    @Override
    protected RouteResult doHandle(Long userId, String query, Map<String, Object> parameters) {
        // 提取起点和终点
        RouteParams routeParams = extractRouteParams(query);

        if (routeParams.destination == null || routeParams.destination.isEmpty()) {
            return createErrorResult("请提供目的地，例如：\"从北京到上海怎么走\"");
        }

        // 如果未提供起点，使用默认位置（如"当前位置"或"这里"）
        if (routeParams.origin == null || routeParams.origin.isEmpty()) {
            routeParams.origin = "当前位置";
        }

        log.debug("提取路线参数: 起点={}, 终点={}, 方式={}, 原始查询: {}",
                routeParams.origin, routeParams.destination, routeParams.mode, query);

        // 调用路线规划服务
        String routeResult = routeService.planRoute(
                routeParams.origin,
                routeParams.destination,
                routeParams.mode
        );

        // 构建参数Map
        Map<String, Object> resultParams = new HashMap<>();
        resultParams.put("origin", routeParams.origin);
        resultParams.put("destination", routeParams.destination);
        resultParams.put("mode", routeParams.mode);
        resultParams.put("query", query);

        // 返回结果，固定置信度0.95
        return RouteResult.builder()
                .handled(true)
                .response(routeResult)
                .intentType(IntentType.ROUTE_QUERY)
                .confidence(0.95)
                .parameters(resultParams)
                .routerLayer("rule")
                .build();
    }

    /**
     * 从查询中提取路线参数
     */
    private RouteParams extractRouteParams(String query) {
        RouteParams params = new RouteParams();
        params.mode = extractTravelMode(query);

        // 尝试匹配 "从X到Y" 格式
        Matcher fromToMatcher = FROM_TO_PATTERN.matcher(query);
        if (fromToMatcher.find()) {
            params.origin = cleanLocation(fromToMatcher.group(1));
            params.destination = cleanLocation(fromToMatcher.group(2));
            return params;
        }

        // 尝试匹配 "怎么去X" 格式（只有终点）
        Matcher toOnlyMatcher = TO_ONLY_PATTERN.matcher(query);
        if (toOnlyMatcher.find()) {
            params.destination = cleanLocation(toOnlyMatcher.group(1));
            return params;
        }

        // 简单解析：如果包含"到"，尝试分割
        if (query.contains("到")) {
            String[] parts = query.split("到", 2);
            if (parts.length == 2) {
                params.origin = cleanLocation(parts[0]);
                params.destination = cleanLocation(parts[1]);
            }
        }

        return params;
    }

    /**
     * 提取出行方式
     */
    private String extractTravelMode(String query) {
        String lowerQuery = query.toLowerCase();

        if (lowerQuery.contains("步行") || lowerQuery.contains("走路") || lowerQuery.contains("walk")) {
            return "walking";
        }
        if (lowerQuery.contains("公交") || lowerQuery.contains("地铁") || lowerQuery.contains("bus") ||
                lowerQuery.contains("transit") || lowerQuery.contains("公共交通")) {
            return "transit";
        }
        if (lowerQuery.contains("开车") || lowerQuery.contains("驾车") || lowerQuery.contains("自驾") ||
                lowerQuery.contains("drive") || lowerQuery.contains("driving")) {
            return "driving";
        }

        // 默认驾车
        return "driving";
    }

    /**
     * 清理地点字符串，去除无关词汇
     */
    private String cleanLocation(String location) {
        if (location == null) {
            return null;
        }

        String cleaned = location.trim()
                .replaceAll("^(从|在|往|向)", "")  // 去除前缀
                .replaceAll("(怎么走|怎么去|怎么|走|去|吗)$", "")  // 去除后缀
                .trim();

        return cleaned.isEmpty() ? null : cleaned;
    }

    @Override
    public double canHandle(String query) {
        if (query == null || query.isEmpty()) {
            return 0.0;
        }

        String lowerQuery = query.toLowerCase();

        // 检查是否包含路线规划关键词
        for (String keyword : ROUTE_KEYWORDS) {
            if (lowerQuery.contains(keyword)) {
                // 额外检查是否包含地点相关词汇或模式
                if (containsLocationPattern(lowerQuery)) {
                    return 0.95;
                }
            }
        }

        return 0.0;
    }

    /**
     * 检查是否包含地点模式
     */
    private boolean containsLocationPattern(String query) {
        // 必须匹配明确的路线模式，而不是简单地检查是否包含"到"
        // "今天是几号"包含"到"但不是路线查询
        return FROM_TO_PATTERN.matcher(query).find() ||
                TO_ONLY_PATTERN.matcher(query).find();
    }

    /**
     * 路线参数内部类
     */
    private static class RouteParams {
        String origin;
        String destination;
        String mode = "driving";
    }
}
