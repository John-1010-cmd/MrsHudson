package com.mrshudson.optim.intent.handler;

import com.mrshudson.domain.entity.CalendarEvent;
import com.mrshudson.optim.intent.IntentType;
import com.mrshudson.optim.intent.RouteResult;
import com.mrshudson.service.CalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 日历查询意图处理器
 * 识别日历相关查询，提取时间参数，返回用户日程安排
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalendarIntentHandler extends AbstractIntentHandler {

    private final CalendarService calendarService;

    // 日历查询关键词
    private static final String[] CALENDAR_KEYWORDS = {
            "日程", "会议", "安排", "日历", "今天", "明天", "下周", "这周", "有什么"
    };

    // 时间关键词
    private static final String[] TIME_KEYWORDS = {
            "今天", "明天", "后天", "本周", "下周", "这周", "周末", "周一", "周二",
            "周三", "周四", "周五", "周六", "周日", "星期"
    };

    @Override
    public IntentType getIntentType() {
        return IntentType.CALENDAR_QUERY;
    }

    @Override
    protected RouteResult doHandle(Long userId, String query, Map<String, Object> parameters) {
        // 计算查询时间范围（今天到未来7天）
        LocalDateTime startTime = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endTime = startTime.plusDays(7).withHour(23).withMinute(59).withSecond(59);

        log.debug("查询用户{}的日程，时间范围: {} 到 {}", userId, startTime, endTime);

        // 查询日历事件
        List<CalendarEvent> events = calendarService.getEvents(userId, startTime, endTime);

        // 格式化结果
        String response = formatCalendarResponse(events, query);

        // 构建参数Map
        Map<String, Object> resultParams = new HashMap<>();
        resultParams.put("eventCount", events.size());
        resultParams.put("startTime", startTime.toString());
        resultParams.put("endTime", endTime.toString());
        resultParams.put("query", query);

        // 返回结果，固定置信度0.95
        return RouteResult.builder()
                .handled(true)
                .response(response)
                .intentType(IntentType.CALENDAR_QUERY)
                .confidence(0.95)
                .parameters(resultParams)
                .routerLayer("rule")
                .build();
    }

    /**
     * 格式化日历查询响应
     */
    private String formatCalendarResponse(List<CalendarEvent> events, String query) {
        if (events == null || events.isEmpty()) {
            return "📅 您未来7天内没有安排任何日程。\n\n您可以对我说：\n• \"帮我创建一个会议\"\n• \"添加一个日程\"";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📅 您未来7天的日程安排（共").append(events.size()).append("项）：\n\n");

        // 按日期分组
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0);
        LocalDateTime tomorrow = today.plusDays(1);

        for (CalendarEvent event : events) {
            LocalDateTime startTime = event.getStartTime();

            // 日期标签
            String dateLabel;
            if (startTime.toLocalDate().equals(today.toLocalDate())) {
                dateLabel = "今天";
            } else if (startTime.toLocalDate().equals(tomorrow.toLocalDate())) {
                dateLabel = "明天";
            } else {
                dateLabel = String.format("%d月%d日", startTime.getMonthValue(), startTime.getDayOfMonth());
            }

            // 时间显示
            String timeStr = String.format("%02d:%02d", startTime.getHour(), startTime.getMinute());

            sb.append("• ").append(dateLabel).append(" ").append(timeStr);
            sb.append(" - ").append(event.getTitle());

            if (event.getLocation() != null && !event.getLocation().isEmpty()) {
                sb.append(" 📍").append(event.getLocation());
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    @Override
    public double canHandle(String query) {
        if (query == null || query.isEmpty()) {
            return 0.0;
        }

        String lowerQuery = query.toLowerCase();
        int matchCount = 0;

        // 检查日历关键词
        for (String keyword : CALENDAR_KEYWORDS) {
            if (lowerQuery.contains(keyword)) {
                matchCount++;
            }
        }

        if (matchCount == 0) {
            return 0.0;
        }

        // 日历查询固定返回高置信度0.95
        return 0.95;
    }
}
