package com.mrshudson.optim.intent.handler;

import com.mrshudson.domain.entity.CalendarEvent;
import com.mrshudson.optim.intent.IntentType;
import com.mrshudson.optim.intent.RouteResult;
import com.mrshudson.service.CalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 日历创建意图处理器
 * 识别创建日程的请求，提取参数并创建日历事件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalendarCreateIntentHandler extends AbstractIntentHandler {

    private final CalendarService calendarService;

    // 创建类关键词
    private static final String[] CREATE_KEYWORDS = {
        "创建", "添加", "新建", "增加", "记"
    };

    @Override
    public IntentType getIntentType() {
        return IntentType.CALENDAR_CREATE;
    }

    @Override
    protected RouteResult doHandle(Long userId, String query, Map<String, Object> parameters) {
        // 提取参数
        String title = extractTitle(parameters, query);
        LocalDateTime startTime = extractStartTime(parameters);
        LocalDateTime endTime = extractEndTime(parameters, startTime);
        String location = extractLocation(parameters);

        log.debug("创建日程参数: title={}, startTime={}, endTime={}, location={}",
            title, startTime, endTime, location);

        // 创建事件
        CalendarEvent event = calendarService.createEvent(
            userId,
            title,
            null, // description
            startTime,
            endTime,
            location,
            null, // category
            null  // reminderMinutes
        );

        // 构建响应
        String response = formatCreateResponse(event);

        // 构建结果参数
        Map<String, Object> resultParams = new HashMap<>();
        resultParams.put("eventId", event.getId());
        resultParams.put("title", event.getTitle());
        resultParams.put("startTime", event.getStartTime().toString());
        resultParams.put("endTime", event.getEndTime().toString());
        if (event.getLocation() != null) {
            resultParams.put("location", event.getLocation());
        }

        return RouteResult.builder()
            .handled(true)
            .response(response)
            .intentType(IntentType.CALENDAR_CREATE)
            .confidence(0.95)
            .parameters(resultParams)
            .routerLayer("rule")
            .build();
    }

    @Override
    public double canHandle(String query) {
        if (query == null || query.isEmpty()) {
            return 0.0;
        }

        String lowerQuery = query.toLowerCase();

        // 检查是否有创建关键词
        boolean hasCreateKeyword = false;
        for (String keyword : CREATE_KEYWORDS) {
            if (lowerQuery.contains(keyword)) {
                hasCreateKeyword = true;
                break;
            }
        }

        if (!hasCreateKeyword) {
            return 0.0;
        }

        // 检查是否有日历相关关键词
        boolean hasCalendarKeyword = false;
        for (String keyword : new String[]{"日程", "会议", "活动", "安排"}) {
            if (lowerQuery.contains(keyword)) {
                hasCalendarKeyword = true;
                break;
            }
        }

        if (!hasCalendarKeyword) {
            return 0.0;
        }

        return 0.95;
    }

    /**
     * 提取事件标题
     */
    private String extractTitle(Map<String, Object> parameters, String query) {
        // 优先从参数中提取
        if (parameters.containsKey("title")) {
            String title = parameters.get("title").toString();
            if (!title.isEmpty() && !"一个会议".equals(title)) {
                return title;
            }
        }

        // 尝试从查询中提取更具体的标题
        String lowerQuery = query.toLowerCase();

        // 匹配"坐高铁去XX"、"去XX"等模式
        if (lowerQuery.contains("坐高铁去") || lowerQuery.contains("坐火车去") ||
            lowerQuery.contains("坐飞机去") || lowerQuery.contains("去")) {
            // 提取目的地作为标题的一部分
            String location = extractLocationFromQuery(query);
            if (location != null) {
                if (lowerQuery.contains("坐高铁")) {
                    return "坐高铁去" + location;
                } else if (lowerQuery.contains("坐火车")) {
                    return "坐火车去" + location;
                } else if (lowerQuery.contains("坐飞机")) {
                    return "坐飞机去" + location;
                } else {
                    return "去" + location;
                }
            }
        }

        // 默认标题
        return "新日程";
    }

    /**
     * 从查询中提取地点
     */
    private String extractLocationFromQuery(String query) {
        String lowerQuery = query.toLowerCase();

        // 匹配"去XX"、"到XX"模式
        String[] patterns = {"去", "到", "前往"};
        for (String pattern : patterns) {
            int index = lowerQuery.indexOf(pattern);
            if (index >= 0) {
                String after = query.substring(index + pattern.length()).trim();
                // 提取后面的地点（直到标点或结束）
                after = after.split("[,，。\\s]")[0];
                if (!after.isEmpty() && after.length() <= 20) {
                    return after;
                }
            }
        }

        return null;
    }

    /**
     * 提取开始时间
     */
    private LocalDateTime extractStartTime(Map<String, Object> parameters) {
        // 优先使用完整时间
        if (parameters.containsKey("startTime")) {
            Object value = parameters.get("startTime");
            if (value instanceof LocalDateTime) {
                return (LocalDateTime) value;
            }
            try {
                return LocalDateTime.parse(value.toString());
            } catch (Exception e) {
                log.debug("解析startTime失败: {}", value);
            }
        }

        // 使用日期 + 默认时间
        LocalDate date = LocalDate.now();
        if (parameters.containsKey("date")) {
            Object value = parameters.get("date");
            try {
                if (value instanceof LocalDate) {
                    date = (LocalDate) value;
                } else {
                    date = LocalDate.parse(value.toString());
                }
            } catch (Exception e) {
                log.debug("解析date失败: {}", value);
            }
        }

        // 默认上午9点
        return date.atTime(9, 0);
    }

    /**
     * 提取结束时间
     */
    private LocalDateTime extractEndTime(Map<String, Object> parameters, LocalDateTime startTime) {
        // 如果有明确的结束时间
        if (parameters.containsKey("endTime")) {
            Object value = parameters.get("endTime");
            try {
                if (value instanceof LocalDateTime) {
                    return (LocalDateTime) value;
                }
                return LocalDateTime.parse(value.toString());
            } catch (Exception e) {
                log.debug("解析endTime失败: {}", value);
            }
        }

        // 如果有时长
        if (parameters.containsKey("durationMinutes")) {
            Object value = parameters.get("durationMinutes");
            try {
                int minutes = Integer.parseInt(value.toString());
                return startTime.plusMinutes(minutes);
            } catch (Exception e) {
                log.debug("解析durationMinutes失败: {}", value);
            }
        }

        // 默认持续2小时
        return startTime.plusHours(2);
    }

    /**
     * 提取地点
     */
    private String extractLocation(Map<String, Object> parameters) {
        if (parameters.containsKey("location")) {
            return parameters.get("location").toString();
        }
        return null;
    }

    /**
     * 格式化创建成功响应
     */
    private String formatCreateResponse(CalendarEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ 已为您创建日程：\n\n");
        sb.append("📌 ").append(event.getTitle()).append("\n");

        // 日期时间显示
        LocalDateTime startTime = event.getStartTime();
        LocalDateTime endTime = event.getEndTime();
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        String dateLabel;
        if (startTime.toLocalDate().equals(today)) {
            dateLabel = "今天";
        } else if (startTime.toLocalDate().equals(tomorrow)) {
            dateLabel = "明天";
        } else {
            dateLabel = startTime.format(DateTimeFormatter.ofPattern("M月d日"));
        }

        String timeStr = String.format("%02d:%02d", startTime.getHour(), startTime.getMinute());
        String endTimeStr = String.format("%02d:%02d", endTime.getHour(), endTime.getMinute());

        sb.append("📅 ").append(dateLabel).append(" ").append(timeStr).append("-").append(endTimeStr);

        if (event.getLocation() != null && !event.getLocation().isEmpty()) {
            sb.append("\n📍 ").append(event.getLocation());
        }

        return sb.toString();
    }
}
