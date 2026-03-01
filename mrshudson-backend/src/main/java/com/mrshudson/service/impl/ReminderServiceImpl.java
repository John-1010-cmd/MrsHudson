package com.mrshudson.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mrshudson.domain.entity.CalendarEvent;
import com.mrshudson.domain.entity.Reminder;
import com.mrshudson.domain.entity.TodoItem;
import com.mrshudson.mapper.ReminderMapper;
import com.mrshudson.service.ReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 提醒服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderServiceImpl implements ReminderService {

    private final ReminderMapper reminderMapper;

    @Override
    @Transactional
    public Reminder createReminder(Long userId, String type, String title,
                                   String content, LocalDateTime remindAt, Long refId) {
        // 参数校验
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        if (type == null) {
            throw new IllegalArgumentException("提醒类型不能为空");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("提醒标题不能为空");
        }
        if (remindAt == null) {
            throw new IllegalArgumentException("提醒时间不能为空");
        }

        Reminder reminder = new Reminder();
        reminder.setUserId(userId);
        reminder.setType(type);
        reminder.setTitle(title.trim());
        reminder.setContent(content);
        reminder.setRemindAt(remindAt);
        reminder.setRefId(refId);
        reminder.setIsRead(false);
        reminder.setChannel(Reminder.Channel.IN_APP.name());

        reminderMapper.insert(reminder);
        log.info("创建提醒成功: userId={}, type={}, title={}, remindAt={}",
                userId, type, title, remindAt);
        return reminder;
    }

    @Override
    public List<Reminder> getUnreadReminders(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        return reminderMapper.selectList(
                new LambdaQueryWrapper<Reminder>()
                        .eq(Reminder::getUserId, userId)
                        .eq(Reminder::getIsRead, false)
                        .orderByDesc(Reminder::getRemindAt)
        );
    }

    @Override
    @Transactional
    public boolean markAsRead(Long reminderId) {
        if (reminderId == null) {
            log.warn("标记已读失败，提醒ID不能为空");
            return false;
        }

        Reminder reminder = reminderMapper.selectById(reminderId);
        if (reminder == null) {
            log.warn("标记已读失败，提醒不存在: {}", reminderId);
            return false;
        }

        reminder.setIsRead(true);
        reminderMapper.updateById(reminder);
        log.info("提醒已标记为已读: {}", reminderId);
        return true;
    }

    @Override
    @Transactional
    public Reminder createEventReminder(CalendarEvent event) {
        if (event == null) {
            log.warn("创建事件提醒失败，事件不能为空");
            return null;
        }

        // 计算提醒时间
        LocalDateTime remindAt;
        Integer reminderMinutes = event.getReminderMinutes();
        if (reminderMinutes != null && reminderMinutes > 0) {
            remindAt = event.getStartTime().minusMinutes(reminderMinutes);
        } else {
            // 默认提前15分钟提醒
            remindAt = event.getStartTime().minusMinutes(15);
        }

        // 如果提醒时间已经过去，则不创建提醒
        if (remindAt.isBefore(LocalDateTime.now())) {
            log.info("事件提醒时间已过期，跳过创建: eventId={}, remindAt={}",
                    event.getId(), remindAt);
            return null;
        }

        // 构建提醒内容
        String content = buildEventReminderContent(event);

        Reminder reminder = createReminder(
                event.getUserId(),
                Reminder.Type.EVENT.name(),
                "日程提醒: " + event.getTitle(),
                content,
                remindAt,
                event.getId()
        );

        log.info("为日历事件创建提醒成功: eventId={}, reminderId={}",
                event.getId(), reminder.getId());
        return reminder;
    }

    @Override
    @Transactional
    public Reminder createTodoReminder(TodoItem todo) {
        if (todo == null) {
            log.warn("创建待办提醒失败，待办不能为空");
            return null;
        }

        // 如果没有截止日期，则不创建提醒
        if (todo.getDueDate() == null) {
            log.info("待办事项没有截止日期，跳过创建提醒: todoId={}", todo.getId());
            return null;
        }

        // 提醒时间为截止日期当天早上9点
        LocalDateTime remindAt = todo.getDueDate().withHour(9).withMinute(0).withSecond(0);

        // 如果提醒时间已经过去，则不创建提醒
        if (remindAt.isBefore(LocalDateTime.now())) {
            log.info("待办提醒时间已过期，跳过创建: todoId={}, remindAt={}",
                    todo.getId(), remindAt);
            return null;
        }

        // 构建提醒内容
        String content = buildTodoReminderContent(todo);

        Reminder reminder = createReminder(
                todo.getUserId(),
                Reminder.Type.TODO.name(),
                "待办提醒: " + todo.getTitle(),
                content,
                remindAt,
                todo.getId()
        );

        log.info("为待办事项创建提醒成功: todoId={}, reminderId={}",
                todo.getId(), reminder.getId());
        return reminder;
    }

    @Override
    public List<Reminder> getAllReminders(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        return reminderMapper.selectList(
                new LambdaQueryWrapper<Reminder>()
                        .eq(Reminder::getUserId, userId)
                        .orderByDesc(Reminder::getRemindAt)
        );
    }

    @Override
    public List<Reminder> getPendingReminders() {
        return reminderMapper.selectList(
                new LambdaQueryWrapper<Reminder>()
                        .lt(Reminder::getRemindAt, LocalDateTime.now())
                        .eq(Reminder::getIsRead, false)
        );
    }

    /**
     * 构建事件提醒内容
     */
    private String buildEventReminderContent(CalendarEvent event) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        StringBuilder sb = new StringBuilder();

        sb.append("您有一个日程即将开始\n\n");
        sb.append("事件: ").append(event.getTitle()).append("\n");
        sb.append("时间: ").append(event.getStartTime().format(formatter));
        sb.append(" ~ ").append(event.getEndTime().format(formatter)).append("\n");

        if (event.getLocation() != null && !event.getLocation().isEmpty()) {
            sb.append("地点: ").append(event.getLocation()).append("\n");
        }

        if (event.getDescription() != null && !event.getDescription().isEmpty()) {
            sb.append("备注: ").append(event.getDescription()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 构建待办提醒内容
     */
    private String buildTodoReminderContent(TodoItem todo) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        StringBuilder sb = new StringBuilder();

        sb.append("您有一个待办事项即将到期\n\n");
        sb.append("待办: ").append(todo.getTitle()).append("\n");

        if (todo.getDueDate() != null) {
            sb.append("截止日期: ").append(todo.getDueDate().format(formatter)).append("\n");
        }

        if (todo.getPriority() != null) {
            String priorityText = switch (TodoItem.Priority.valueOf(todo.getPriority())) {
                case HIGH -> "高";
                case MEDIUM -> "中";
                case LOW -> "低";
            };
            sb.append("优先级: ").append(priorityText).append("\n");
        }

        if (todo.getDescription() != null && !todo.getDescription().isEmpty()) {
            sb.append("描述: ").append(todo.getDescription()).append("\n");
        }

        return sb.toString();
    }
}
