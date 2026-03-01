package com.mrshudson.service;

import com.mrshudson.domain.entity.CalendarEvent;
import com.mrshudson.domain.entity.Reminder;
import com.mrshudson.domain.entity.TodoItem;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提醒服务接口
 */
public interface ReminderService {

    /**
     * 创建提醒
     *
     * @param userId    用户ID
     * @param type      提醒类型
     * @param title     提醒标题
     * @param content   提醒内容
     * @param remindAt  提醒时间
     * @param refId     关联ID（事件ID或待办ID）
     * @return 创建的提醒
     */
    Reminder createReminder(Long userId, String type, String title,
                           String content, LocalDateTime remindAt, Long refId);

    /**
     * 获取用户的未读提醒
     *
     * @param userId 用户ID
     * @return 未读提醒列表
     */
    List<Reminder> getUnreadReminders(Long userId);

    /**
     * 标记提醒为已读
     *
     * @param reminderId 提醒ID
     * @return 是否成功
     */
    boolean markAsRead(Long reminderId);

    /**
     * 为日历事件创建提醒
     *
     * @param event 日历事件
     * @return 创建的提醒
     */
    Reminder createEventReminder(com.mrshudson.domain.entity.CalendarEvent event);

    /**
     * 为待办事项创建提醒
     *
     * @param todo 待办事项
     * @return 创建的提醒，如果待办没有截止日期则返回null
     */
    Reminder createTodoReminder(com.mrshudson.domain.entity.TodoItem todo);

    /**
     * 获取用户所有提醒
     *
     * @param userId 用户ID
     * @return 提醒列表
     */
    List<Reminder> getAllReminders(Long userId);

    /**
     * 获取需要发送的提醒（已到提醒时间且未读）
     *
     * @return 需要发送的提醒列表
     */
    List<Reminder> getPendingReminders();
}
