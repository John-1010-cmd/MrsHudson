package com.mrshudson.mcp.calendar;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mrshudson.domain.entity.CalendarEvent;

import com.mrshudson.mcp.BaseTool;
import com.mrshudson.mcp.ToolRegistry;
import com.mrshudson.mcp.kimi.dto.Tool;
import com.mrshudson.service.CalendarService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * 日历MCP工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalendarTool implements BaseTool {

    private final CalendarService calendarService;
    private final ToolRegistry toolRegistry;

    // 创建事件工具定义
    private static final Tool CREATE_EVENT_TOOL = Tool.builder()
            .type("function")
            .function(Tool.Function.builder()
                    .name("create_calendar_event")
                    .description("为用户创建一个新的日历事件或日程安排")
                    .parameters(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "title", Map.of(
                                            "type", "string",
                                            "description", "事件标题，简洁明了地描述事件内容"
                                    ),
                                    "description", Map.of(
                                            "type", "string",
                                            "description", "事件详细描述（可选）"
                                    ),
                                    "start_time", Map.of(
                                            "type", "string",
                                            "description", "开始时间，格式为ISO 8601，如2026-03-01T15:00:00"
                                    ),
                                    "end_time", Map.of(
                                            "type", "string",
                                            "description", "结束时间，格式为ISO 8601，如2026-03-01T16:00:00"
                                    ),
                                    "location", Map.of(
                                            "type", "string",
                                            "description", "事件地点（可选）"
                                    ),
                                    "category", Map.of(
                                            "type", "string",
                                            "description", "事件分类：work(工作), personal(个人), family(家庭)，默认为personal",
                                            "enum", new String[]{"work", "personal", "family"}
                                    ),
                                    "reminder_minutes", Map.of(
                                            "type", "integer",
                                            "description", "提前提醒分钟数，默认为15分钟"
                                    )
                            ),
                            "required", new String[]{"title", "start_time", "end_time"}
                    ))
                    .build())
            .build();

    // 查询事件工具定义
    private static final Tool GET_EVENTS_TOOL = Tool.builder()
            .type("function")
            .function(Tool.Function.builder()
                    .name("get_calendar_events")
                    .description("查询用户在指定时间范围内的日历事件")
                    .parameters(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "start_date", Map.of(
                                            "type", "string",
                                            "description", "开始日期，格式为YYYY-MM-DD，不传则默认为今天"
                                    ),
                                    "end_date", Map.of(
                                            "type", "string",
                                            "description", "结束日期，格式为YYYY-MM-DD，不传则默认为7天后"
                                    ),
                                    "limit", Map.of(
                                            "type", "integer",
                                            "description", "返回的最大事件数量，默认为10"
                                    )
                            ),
                            "required", new String[]{}
                    ))
                    .build())
            .build();

    // 删除事件工具定义
    private static final Tool DELETE_EVENT_TOOL = Tool.builder()
            .type("function")
            .function(Tool.Function.builder()
                    .name("delete_calendar_event")
                    .description("删除指定的日历事件")
                    .parameters(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "event_id", Map.of(
                                            "type", "integer",
                                            "description", "要删除的事件ID"
                                    )
                            ),
                            "required", new String[]{"event_id"}
                    ))
                    .build())
            .build();

    @PostConstruct
    public void register() {
        toolRegistry.registerTool(CREATE_EVENT_TOOL, this::createEvent);
        toolRegistry.registerTool(GET_EVENTS_TOOL, this::getEvents);
        toolRegistry.registerTool(DELETE_EVENT_TOOL, this::deleteEvent);
        log.info("日历工具已注册: create_calendar_event, get_calendar_events, delete_calendar_event");
    }

    @Override
    public Tool getToolDefinition() {
        return CREATE_EVENT_TOOL;
    }

    @Override
    public String execute(String arguments) {
        return createEvent(arguments);
    }

    /**
     * 创建事件
     */
    private String createEvent(String arguments) {
        try {
            JSONObject params = JSON.parseObject(arguments);

            // 解析必填参数
            String title = params.getString("title");
            String startTimeStr = params.getString("start_time");
            String endTimeStr = params.getString("end_time");

            if (title == null || title.isEmpty()) {
                return "错误：事件标题不能为空";
            }

            // 解析时间
            LocalDateTime startTime = parseDateTime(startTimeStr);
            LocalDateTime endTime = parseDateTime(endTimeStr);

            if (startTime == null || endTime == null) {
                return "错误：时间格式不正确，请使用ISO 8601格式，如2026-03-01T15:00:00";
            }

            // 解析可选参数
            String description = params.getString("description");
            String location = params.getString("location");
            String categoryStr = params.getString("category");
            Integer reminderMinutes = params.getInteger("reminder_minutes");

            String category = parseCategory(categoryStr);

            // TODO: 从上下文中获取当前用户ID，这里暂时使用1L
            Long userId = 1L;

            CalendarEvent event = calendarService.createEvent(
                    userId, title, description, startTime, endTime,
                    location, category, reminderMinutes
            );

            return String.format("✅ 日历事件创建成功！\n\n%s", calendarService.formatEventToText(event));

        } catch (IllegalArgumentException e) {
            return "创建事件失败: " + e.getMessage();
        } catch (Exception e) {
            log.error("创建日历事件失败", e);
            return "创建事件失败: " + e.getMessage();
        }
    }

    /**
     * 查询事件
     */
    private String getEvents(String arguments) {
        try {
            JSONObject params = JSON.parseObject(arguments);

            // 解析日期参数
            String startDateStr = params.getString("start_date");
            String endDateStr = params.getString("end_date");
            Integer limit = params.getInteger("limit");

            // 默认查询今天到7天后
            LocalDate startDate = startDateStr != null ?
                    LocalDate.parse(startDateStr) : LocalDate.now();
            LocalDate endDate = endDateStr != null ?
                    LocalDate.parse(endDateStr) : startDate.plusDays(7);

            LocalDateTime startTime = startDate.atStartOfDay();
            LocalDateTime endTime = endDate.atTime(LocalTime.MAX);

            // TODO: 从上下文中获取当前用户ID
            Long userId = 1L;

            List<CalendarEvent> events = calendarService.getEvents(userId, startTime, endTime);

            if (limit != null && limit > 0 && events.size() > limit) {
                events = events.subList(0, limit);
            }

            String title = String.format("%s 至 %s 的日程", startDate, endDate);
            return calendarService.formatEventsToText(events, title);

        } catch (DateTimeParseException e) {
            return "错误：日期格式不正确，请使用YYYY-MM-DD格式";
        } catch (Exception e) {
            log.error("查询日历事件失败", e);
            return "查询事件失败: " + e.getMessage();
        }
    }

    /**
     * 删除事件
     */
    private String deleteEvent(String arguments) {
        try {
            JSONObject params = JSON.parseObject(arguments);
            Long eventId = params.getLong("event_id");

            if (eventId == null) {
                return "错误：请提供事件ID";
            }

            // TODO: 从上下文中获取当前用户ID
            Long userId = 1L;

            boolean success = calendarService.deleteEvent(userId, eventId);

            if (success) {
                return "✅ 事件已成功删除";
            } else {
                return "删除失败：事件不存在或您没有权限删除";
            }

        } catch (Exception e) {
            log.error("删除日历事件失败", e);
            return "删除事件失败: " + e.getMessage();
        }
    }

    /**
     * 解析日期时间字符串
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            // 尝试ISO格式
            return LocalDateTime.parse(dateTimeStr);
        } catch (DateTimeParseException e) {
            try {
                // 尝试带T的格式
                if (dateTimeStr.contains("T")) {
                    return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                }
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    /**
     * 解析分类字符串
     */
    private String parseCategory(String categoryStr) {
        if (categoryStr == null || categoryStr.isEmpty()) {
            return CalendarEvent.Category.PERSONAL.name();
        }
        return switch (categoryStr.toLowerCase()) {
            case "work" -> CalendarEvent.Category.WORK.name();
            case "family" -> CalendarEvent.Category.FAMILY.name();
            default -> CalendarEvent.Category.PERSONAL.name();
        };
    }
}
