package com.mrshudson.job;

import com.mrshudson.domain.entity.Reminder;
import com.mrshudson.service.ReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 提醒定时任务
 * 每分钟扫描一次需要发送的提醒
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderJob {

    private final ReminderService reminderService;

    /**
     * 每分钟执行一次，扫描并推送提醒
     */
    @Scheduled(fixedRate = 60000)
    public void scanAndPushReminders() {
        log.debug("开始扫描待发送提醒...");

        try {
            // 获取所有已到时间且未读的提醒
            List<Reminder> pendingReminders = reminderService.getPendingReminders();

            if (pendingReminders.isEmpty()) {
                log.debug("没有待发送的提醒");
                return;
            }

            log.info("发现 {} 个待发送提醒", pendingReminders.size());

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime now = LocalDateTime.now();

            for (Reminder reminder : pendingReminders) {
                try {
                    // 推送提醒（目前仅打印日志模拟推送）
                    pushReminder(reminder, now, formatter);

                    // 标记为已推送（通过标记已读来实现）
                    // 注意：实际应用中可能需要增加一个 isPushed 字段来区分已推送和已读
                    // 这里为了简化，直接标记为已读
                    reminderService.markAsRead(reminder.getId());

                } catch (Exception e) {
                    log.error("推送提醒失败: reminderId={}, error={}", reminder.getId(), e.getMessage());
                }
            }

            log.info("提醒推送完成，共处理 {} 个提醒", pendingReminders.size());

        } catch (Exception e) {
            log.error("扫描提醒任务执行失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 推送提醒（模拟推送，后续接入真实推送服务）
     */
    private void pushReminder(Reminder reminder, LocalDateTime pushTime, DateTimeFormatter formatter) {
        String typeText = getTypeText(reminder.getType());

        // 打印推送日志（模拟推送）
        log.info("========================================");
        log.info("【提醒推送】");
        log.info("用户ID: {}", reminder.getUserId());
        log.info("类型: {}", typeText);
        log.info("标题: {}", reminder.getTitle());
        log.info("内容: {}", reminder.getContent());
        log.info("预定提醒时间: {}", reminder.getRemindAt().format(formatter));
        log.info("实际推送时间: {}", pushTime.format(formatter));
        log.info("关联ID: {}", reminder.getRefId());
        log.info("========================================");

        // TODO: 后续接入真实推送服务
        // 1. WebSocket 实时推送
        // 2. 邮件推送
        // 3. 短信推送
        // 4. 第三方推送服务（如极光推送、Firebase Cloud Messaging 等）
    }

    /**
     * 获取提醒类型文本
     */
    private String getTypeText(String type) {
        if (type == null) {
            return "未知";
        }
        try {
            return switch (Reminder.Type.valueOf(type)) {
                case EVENT -> "日程";
                case TODO -> "待办";
                case WEATHER -> "天气";
                case SYSTEM -> "系统";
            };
        } catch (Exception e) {
            return type;
        }
    }
}
