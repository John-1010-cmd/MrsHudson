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
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 日程删除意图处理器
 * 识别删除日程的请求，提取参数并删除日程事件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalendarDeleteIntentHandler extends AbstractIntentHandler {

    private final CalendarService calendarService;

    // 删除类关键词
    private static final String[] DELETE_KEYWORDS = {
        "删除", "取消", "移除", "删掉", "去掉"
    };

    @Override
    public IntentType getIntentType() {
        return IntentType.CALENDAR_DELETE;
    }

    @Override
    protected RouteResult doHandle(Long userId, String query, Map<String, Object> parameters) {
        String eventId = null;
        String title = null;

        // 提取参数
        if (parameters.containsKey("eventId")) {
            eventId = parameters.get("eventId").toString();
        }
        if (parameters.containsKey("title")) {
            title = parameters.get("title").toString();
        }

        log.debug("删除日程参数: eventId={}, title={}", eventId, title);

        CalendarEvent deletedEvent = null;

        // 如果提供了eventId，直接删除
        if (eventId != null) {
            try {
                Long id = Long.parseLong(eventId);
                deletedEvent = calendarService.getEventById(id).orElse(null);
                if (deletedEvent != null && deletedEvent.getUserId().equals(userId)) {
                    calendarService.deleteEvent(userId, id);
                } else {
                    return RouteResult.builder()
                        .handled(true)
                        .response("❌ 未找到ID为 " + eventId + " 的日程")
                        .intentType(IntentType.CALENDAR_DELETE)
                        .confidence(0.9)
                        .routerLayer("rule")
                        .build();
                }
            } catch (NumberFormatException e) {
                log.warn("无效的日程ID: {}", eventId);
            }
        }

        // 如果提供了标题，尝试查找并删除
        if (deletedEvent == null && title != null) {
            // 默认查询今天到未来7天的事件
            LocalDate startDate = LocalDate.now();
            LocalDate endDate = startDate.plusDays(7);
            List<CalendarEvent> events = calendarService.getEvents(
                userId, startDate.atStartOfDay(), endDate.atTime(LocalTime.MAX));
            
            for (CalendarEvent event : events) {
                if (event.getTitle().contains(title)) {
                    calendarService.deleteEvent(userId, event.getId());
                    deletedEvent = event;
                    break;
                }
            }
        }

        // 构建响应
        String response;
        if (deletedEvent != null) {
            LocalDateTime startTime = deletedEvent.getStartTime();
            String dateStr = startTime.toLocalDate().toString();
            String timeStr = String.format("%02d:%02d", startTime.getHour(), startTime.getMinute());
            
            response = "🗑️ 已取消日程：\n\n" + 
                      "📌 " + deletedEvent.getTitle() + "\n" +
                      "📅 " + dateStr + " " + timeStr;
        } else {
            response = "❌ 未找到匹配的日程\n\n您可以先查看日程列表，然后说\"取消日程1\"或\"取消XX会议\"";
        }

        Map<String, Object> resultParams = new HashMap<>();
        if (deletedEvent != null) {
            resultParams.put("eventId", deletedEvent.getId());
            resultParams.put("title", deletedEvent.getTitle());
        }

        return RouteResult.builder()
            .handled(true)
            .response(response)
            .intentType(IntentType.CALENDAR_DELETE)
            .confidence(0.9)
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

        // 检查是否有删除关键词
        boolean hasDeleteKeyword = false;
        for (String keyword : DELETE_KEYWORDS) {
            if (lowerQuery.contains(keyword)) {
                hasDeleteKeyword = true;
                break;
            }
        }

        if (!hasDeleteKeyword) {
            return 0.0;
        }

        // 检查是否有日程相关关键词
        boolean hasCalendarKeyword = false;
        for (String keyword : new String[]{"日程", "会议", "安排", "日历"}) {
            if (lowerQuery.contains(keyword.toLowerCase())) {
                hasCalendarKeyword = true;
                break;
            }
        }

        if (!hasCalendarKeyword) {
            return 0.0;
        }

        return 0.9;
    }
}
