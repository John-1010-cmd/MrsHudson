package com.mrshudson.controller;

import com.mrshudson.domain.dto.*;
import com.mrshudson.domain.entity.CalendarEvent;
import com.mrshudson.service.CalendarService;
import com.mrshudson.util.CurrentUserUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 日历控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;
    private final CurrentUserUtil currentUserUtil;

    /**
     * 获取事件列表
     */
    @GetMapping("/events")
    public Result<List<EventResponse>> getEvents(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        Long userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        // 默认查询当月
        if (startDate == null) {
            startDate = LocalDate.now();
        }
        if (endDate == null) {
            endDate = startDate.plusMonths(1);
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        log.info("用户{}查询日历事件: {} 至 {}", userId, startDate, endDate);

        List<CalendarEvent> events = calendarService.getEvents(userId, start, end);
        List<EventResponse> responses = events.stream()
                .map(EventResponse::fromEntity)
                .collect(Collectors.toList());

        return Result.success(responses);
    }

    /**
     * 获取即将开始的事件
     */
    @GetMapping("/events/upcoming")
    public Result<List<EventResponse>> getUpcomingEvents(
            @RequestParam(defaultValue = "5") int limit) {

        Long userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        log.info("用户{}查询即将开始的事件, 限制: {}", userId, limit);

        List<CalendarEvent> events = calendarService.getUpcomingEvents(userId, limit);
        List<EventResponse> responses = events.stream()
                .map(EventResponse::fromEntity)
                .collect(Collectors.toList());

        return Result.success(responses);
    }

    /**
     * 创建事件
     */
    @PostMapping("/events")
    public Result<EventResponse> createEvent(
            @Valid @RequestBody CreateEventRequest request) {

        Long userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        log.info("用户{}创建日历事件: {}", userId, request.getTitle());

        CalendarEvent event = calendarService.createEvent(
                userId,
                request.getTitle(),
                request.getDescription(),
                request.getStartTime(),
                request.getEndTime(),
                request.getLocation(),
                request.getCategory(),
                request.getReminderMinutes()
        );

        return Result.success(EventResponse.fromEntity(event));
    }

    /**
     * 获取单个事件详情
     */
    @GetMapping("/events/{id}")
    public Result<EventResponse> getEventById(
            @PathVariable Long id) {

        Long userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        CalendarEvent event = calendarService.getEventById(id).orElse(null);
        if (event == null) {
            return Result.error(404, "事件不存在");
        }

        if (!event.getUserId().equals(userId)) {
            return Result.error(403, "无权访问此事件");
        }

        return Result.success(EventResponse.fromEntity(event));
    }

    /**
     * 更新事件
     */
    @PutMapping("/events/{id}")
    public Result<EventResponse> updateEvent(
            @PathVariable Long id,
            @RequestBody UpdateEventRequest request) {

        Long userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        log.info("用户{}更新日历事件: {}", userId, id);

        CalendarEvent existingEvent = calendarService.getEventById(id).orElse(null);
        if (existingEvent == null) {
            return Result.error(404, "事件不存在");
        }

        if (!existingEvent.getUserId().equals(userId)) {
            return Result.error(403, "无权更新此事件");
        }

        // 构建更新实体
        CalendarEvent updateEntity = new CalendarEvent();
        updateEntity.setTitle(request.getTitle());
        updateEntity.setDescription(request.getDescription());
        updateEntity.setStartTime(request.getStartTime());
        updateEntity.setEndTime(request.getEndTime());
        updateEntity.setLocation(request.getLocation());
        updateEntity.setCategory(request.getCategory());
        updateEntity.setReminderMinutes(request.getReminderMinutes());

        CalendarEvent updated = calendarService.updateEvent(userId, id, updateEntity);
        return Result.success(EventResponse.fromEntity(updated));
    }

    /**
     * 删除事件
     */
    @DeleteMapping("/events/{id}")
    public Result<Void> deleteEvent(
            @PathVariable Long id) {

        Long userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        log.info("用户{}删除日历事件: {}", userId, id);

        boolean success = calendarService.deleteEvent(userId, id);
        if (success) {
            return Result.success(null);
        } else {
            return Result.error(404, "事件不存在或删除失败");
        }
    }
}
