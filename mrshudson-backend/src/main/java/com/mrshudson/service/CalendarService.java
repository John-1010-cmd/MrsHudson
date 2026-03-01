package com.mrshudson.service;

import com.mrshudson.domain.entity.CalendarEvent;
import com.mrshudson.domain.entity.CalendarEvent.Category;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 日历服务接口
 */
public interface CalendarService {

    /**
     * 创建日历事件
     *
     * @param userId  用户ID
     * @param title   事件标题
     * @param description 事件描述
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param location  地点
     * @param category  分类
     * @param reminderMinutes 提前提醒分钟数
     * @return 创建的事件
     */
    CalendarEvent createEvent(Long userId, String title, String description,
                              LocalDateTime startTime, LocalDateTime endTime,
                              String location, String category, Integer reminderMinutes);

    /**
     * 查询用户在时间范围内的事件
     *
     * @param userId 用户ID
     * @param start  开始时间
     * @param end    结束时间
     * @return 事件列表
     */
    List<CalendarEvent> getEvents(Long userId, LocalDateTime start, LocalDateTime end);

    /**
     * 查询用户即将开始的事件
     *
     * @param userId 用户ID
     * @param limit  限制数量
     * @return 事件列表
     */
    List<CalendarEvent> getUpcomingEvents(Long userId, int limit);

    /**
     * 根据ID查询事件
     *
     * @param eventId 事件ID
     * @return 事件
     */
    Optional<CalendarEvent> getEventById(Long eventId);

    /**
     * 删除事件
     *
     * @param userId  用户ID
     * @param eventId 事件ID
     * @return 是否删除成功
     */
    boolean deleteEvent(Long userId, Long eventId);

    /**
     * 更新事件
     *
     * @param userId  用户ID
     * @param eventId 事件ID
     * @param event   更新的内容
     * @return 更新后的事件
     */
    CalendarEvent updateEvent(Long userId, Long eventId, CalendarEvent event);

    /**
     * 获取事件详情文本（用于AI回复）
     *
     * @param event 事件
     * @return 格式化的文本
     */
    String formatEventToText(CalendarEvent event);

    /**
     * 获取事件列表文本（用于AI回复）
     *
     * @param events 事件列表
     * @param title  标题
     * @return 格式化的文本
     */
    String formatEventsToText(List<CalendarEvent> events, String title);
}
