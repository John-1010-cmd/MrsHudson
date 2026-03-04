package com.mrshudson.optim.cache.listener;

import com.mrshudson.optim.cache.ToolCacheManager;
import com.mrshudson.optim.cache.event.CalendarChangeEvent;
import com.mrshudson.optim.cache.event.TodoChangeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 数据变更监听器
 * 监听Calendar和Todo的数据变更事件，异步清除相关缓存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataChangeListener {

    private final ToolCacheManager toolCacheManager;

    private static final String CALENDAR_TOOL_NAME = "calendar";
    private static final String TODO_TOOL_NAME = "todo";

    /**
     * 监听日历变更事件
     * 异步清除该用户的日历工具缓存
     *
     * @param event 日历变更事件
     */
    @Async
    @EventListener
    public void onCalendarChange(CalendarChangeEvent event) {
        try {
            Long userId = event.getUserId();
            log.info("接收到日历变更事件: userId={}, eventId={}, operation={}",
                    userId, event.getEventId(), event.getOperationType());

            // 清除该用户的日历工具缓存
            toolCacheManager.invalidate(CALENDAR_TOOL_NAME, userId);
            log.info("已异步清除用户{}的日历工具缓存", userId);
        } catch (Exception e) {
            // 异步处理，异常不抛出，仅记录日志
            log.error("处理日历变更事件时发生异常: userId={}, error={}",
                    event.getUserId(), e.getMessage(), e);
        }
    }

    /**
     * 监听待办事项变更事件
     * 异步清除该用户的待办工具缓存
     *
     * @param event 待办事项变更事件
     */
    @Async
    @EventListener
    public void onTodoChange(TodoChangeEvent event) {
        try {
            Long userId = event.getUserId();
            log.info("接收到待办事项变更事件: userId={}, todoId={}, operation={}",
                    userId, event.getTodoId(), event.getOperationType());

            // 清除该用户的待办工具缓存
            toolCacheManager.invalidate(TODO_TOOL_NAME, userId);
            log.info("已异步清除用户{}的待办工具缓存", userId);
        } catch (Exception e) {
            // 异步处理，异常不抛出，仅记录日志
            log.error("处理待办事项变更事件时发生异常: userId={}, error={}",
                    event.getUserId(), e.getMessage(), e);
        }
    }
}
