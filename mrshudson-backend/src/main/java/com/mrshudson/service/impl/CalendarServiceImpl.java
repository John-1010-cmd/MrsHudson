package com.mrshudson.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mrshudson.domain.entity.CalendarEvent;
import com.mrshudson.domain.entity.CalendarEvent.Category;
import com.mrshudson.mapper.CalendarEventMapper;
import com.mrshudson.optim.cache.event.CalendarChangeEvent;
import com.mrshudson.optim.cost.CacheInvalidationService;
import com.mrshudson.service.CalendarService;
import com.mrshudson.service.ReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 日历服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarServiceImpl implements CalendarService {

    private final CalendarEventMapper calendarEventMapper;
    private final ReminderService reminderService;
    private final ApplicationEventPublisher eventPublisher;
    private final CacheInvalidationService cacheInvalidationService;

    @Override
    @Transactional
    public CalendarEvent createEvent(Long userId, String title, String description,
                                     LocalDateTime startTime, LocalDateTime endTime,
                                     String location, String category, Integer reminderMinutes) {

        // 参数校验
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("开始时间和结束时间不能为空");
        }
        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("事件标题不能为空");
        }

        CalendarEvent event = new CalendarEvent();
        event.setUserId(userId);
        event.setTitle(title.trim());
        event.setDescription(description);
        event.setStartTime(startTime);
        event.setEndTime(endTime);
        event.setLocation(location);
        event.setCategory(category != null ? category : CalendarEvent.Category.PERSONAL.name());
        event.setReminderMinutes(reminderMinutes != null ? reminderMinutes : 15);
        event.setIsRecurring(false);

        calendarEventMapper.insert(event);
        log.info("用户{}创建了日历事件: {}, ID: {}", userId, title, event.getId());

        // 自动创建提醒
        try {
            reminderService.createEventReminder(event);
        } catch (Exception e) {
            log.warn("为日历事件创建提醒失败: eventId={}, error={}", event.getId(), e.getMessage());
        }

        // 发布变更事件，触发缓存清除
        eventPublisher.publishEvent(new CalendarChangeEvent(this, userId, event.getId(), CalendarChangeEvent.OperationType.CREATE));

        // 清除用户缓存（包括语义缓存）
        cacheInvalidationService.invalidateUserCache(userId);

        return event;
    }

    @Override
    public List<CalendarEvent> getEvents(Long userId, LocalDateTime start, LocalDateTime end) {
        return calendarEventMapper.selectList(
                new LambdaQueryWrapper<CalendarEvent>()
                        .eq(CalendarEvent::getUserId, userId)
                        .between(CalendarEvent::getStartTime, start, end)
                        .orderByAsc(CalendarEvent::getStartTime)
        );
    }

    @Override
    public List<CalendarEvent> getUpcomingEvents(Long userId, int limit) {
        LocalDateTime now = LocalDateTime.now();
        return calendarEventMapper.selectList(
                new LambdaQueryWrapper<CalendarEvent>()
                        .eq(CalendarEvent::getUserId, userId)
                        .gt(CalendarEvent::getStartTime, now)
                        .orderByAsc(CalendarEvent::getStartTime)
                        .last("LIMIT " + limit)
        );
    }

    @Override
    public Optional<CalendarEvent> getEventById(Long eventId) {
        return Optional.ofNullable(calendarEventMapper.selectById(eventId));
    }

    @Override
    @Transactional
    public boolean deleteEvent(Long userId, Long eventId) {
        CalendarEvent event = calendarEventMapper.selectById(eventId);
        if (event == null) {
            log.warn("删除失败，事件不存在: {}", eventId);
            return false;
        }

        if (!event.getUserId().equals(userId)) {
            log.warn("删除失败，用户{}无权删除事件{}", userId, eventId);
            return false;
        }

        calendarEventMapper.deleteById(eventId);
        log.info("用户{}删除了日历事件: {}", userId, eventId);

        // 发布变更事件，触发缓存清除
        eventPublisher.publishEvent(new CalendarChangeEvent(this, userId, eventId, CalendarChangeEvent.OperationType.DELETE));

        // 清除用户缓存（包括语义缓存）
        cacheInvalidationService.invalidateUserCache(userId);

        return true;
    }

    @Override
    @Transactional
    public CalendarEvent updateEvent(Long userId, Long eventId, CalendarEvent updatedEvent) {
        CalendarEvent event = calendarEventMapper.selectById(eventId);
        if (event == null) {
            throw new IllegalArgumentException("事件不存在: " + eventId);
        }

        if (!event.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权更新此事件");
        }

        // 更新字段
        if (updatedEvent.getTitle() != null) {
            event.setTitle(updatedEvent.getTitle().trim());
        }
        if (updatedEvent.getDescription() != null) {
            event.setDescription(updatedEvent.getDescription());
        }
        if (updatedEvent.getStartTime() != null) {
            event.setStartTime(updatedEvent.getStartTime());
        }
        if (updatedEvent.getEndTime() != null) {
            event.setEndTime(updatedEvent.getEndTime());
        }
        if (updatedEvent.getLocation() != null) {
            event.setLocation(updatedEvent.getLocation());
        }
        if (updatedEvent.getCategory() != null) {
            event.setCategory(updatedEvent.getCategory());
        }
        if (updatedEvent.getReminderMinutes() != null) {
            event.setReminderMinutes(updatedEvent.getReminderMinutes());
        }

        // 校验时间
        if (event.getStartTime().isAfter(event.getEndTime())) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }

        calendarEventMapper.updateById(event);
        log.info("用户{}更新了日历事件: {}", userId, eventId);

        // 发布变更事件，触发缓存清除
        eventPublisher.publishEvent(new CalendarChangeEvent(this, userId, eventId, CalendarChangeEvent.OperationType.UPDATE));

        // 清除用户缓存（包括语义缓存）
        cacheInvalidationService.invalidateUserCache(userId);

        return event;
    }

    @Override
    public String formatEventToText(CalendarEvent event) {
        if (event == null) {
            return "事件不存在";
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        StringBuilder sb = new StringBuilder();

        sb.append("📅 ").append(event.getTitle()).append("\n");
        sb.append("🕐 时间：").append(event.getStartTime().format(formatter));
        sb.append(" ~ ").append(event.getEndTime().format(formatter)).append("\n");

        if (event.getLocation() != null && !event.getLocation().isEmpty()) {
            sb.append("📍 地点：").append(event.getLocation()).append("\n");
        }

        sb.append("🏷️ 分类：").append(getCategoryText(event.getCategory())).append("\n");

        if (event.getDescription() != null && !event.getDescription().isEmpty()) {
            sb.append("📝 备注：").append(event.getDescription()).append("\n");
        }

        if (event.getReminderMinutes() != null && event.getReminderMinutes() > 0) {
            sb.append("🔔 提醒：提前").append(event.getReminderMinutes()).append("分钟\n");
        }

        return sb.toString();
    }

    @Override
    public String formatEventsToText(List<CalendarEvent> events, String title) {
        if (events == null || events.isEmpty()) {
            return "📅 " + title + "\n\n暂无事件";
        }

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM-dd");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        StringBuilder sb = new StringBuilder();
        sb.append("📅 ").append(title).append("\n");
        sb.append("共 ").append(events.size()).append(" 个事件\n\n");

        // 按日期分组
        events.stream()
                .sorted(Comparator.comparing(CalendarEvent::getStartTime))
                .forEach(event -> {
                    String date = event.getStartTime().format(dateFormatter);
                    String startTime = event.getStartTime().format(timeFormatter);
                    String endTime = event.getEndTime().format(timeFormatter);

                    sb.append(String.format("• %s %s-%s %s\n",
                            date, startTime, endTime, event.getTitle()));

                    if (event.getLocation() != null && !event.getLocation().isEmpty()) {
                        sb.append("  📍 ").append(event.getLocation()).append("\n");
                    }
                });

        return sb.toString();
    }

    private String getCategoryText(String category) {
        try {
            return switch (Category.valueOf(category)) {
                case WORK -> "工作";
                case PERSONAL -> "个人";
                case FAMILY -> "家庭";
            };
        } catch (Exception e) {
            return category;
        }
    }
}
