package com.mrshudson.controller;

import com.mrshudson.domain.dto.Result;
import com.mrshudson.domain.entity.Reminder;
import com.mrshudson.service.ReminderService;
import com.mrshudson.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 提醒控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;
    private final CurrentUserUtil currentUserUtil;

    /**
     * 获取当前用户的所有提醒
     */
    @GetMapping
    public Result<List<ReminderResponse>> getReminders() {
        Long userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        log.info("用户{}查询所有提醒", userId);

        List<Reminder> reminders = reminderService.getAllReminders(userId);
        List<ReminderResponse> responses = reminders.stream()
                .map(ReminderResponse::fromEntity)
                .collect(Collectors.toList());

        return Result.success(responses);
    }

    /**
     * 获取未读提醒列表
     */
    @GetMapping("/unread")
    public Result<List<ReminderResponse>> getUnreadReminders() {
        Long userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        log.info("用户{}查询未读提醒", userId);

        List<Reminder> reminders = reminderService.getUnreadReminders(userId);
        List<ReminderResponse> responses = reminders.stream()
                .map(ReminderResponse::fromEntity)
                .collect(Collectors.toList());

        return Result.success(responses);
    }

    /**
     * 获取未读提醒数量
     */
    @GetMapping("/unread-count")
    public Result<Map<String, Integer>> getUnreadCount() {
        Long userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        List<Reminder> unreadReminders = reminderService.getUnreadReminders(userId);
        int count = unreadReminders.size();

        Map<String, Integer> result = new HashMap<>();
        result.put("count", count);

        return Result.success(result);
    }

    /**
     * 标记提醒为已读
     */
    @PutMapping("/{id}/read")
    public Result<Void> markAsRead(@PathVariable Long id) {
        Long userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        log.info("用户{}标记提醒{}为已读", userId, id);

        boolean success = reminderService.markAsRead(id);
        if (success) {
            return Result.success(null);
        } else {
            return Result.error(404, "提醒不存在");
        }
    }

    /**
     * 批量标记提醒为已读
     */
    @PutMapping("/read-all")
    public Result<Void> markAllAsRead() {
        Long userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        log.info("用户{}批量标记所有提醒为已读", userId);

        List<Reminder> unreadReminders = reminderService.getUnreadReminders(userId);
        for (Reminder reminder : unreadReminders) {
            reminderService.markAsRead(reminder.getId());
        }

        return Result.success(null);
    }

    /**
     * 延迟提醒
     */
    @PutMapping("/{id}/snooze")
    public Result<ReminderResponse> snooze(
            @PathVariable Long id,
            @RequestParam Integer minutes) {
        Long userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        log.info("用户{}延迟提醒{}: {}分钟", userId, id, minutes);

        Reminder snoozedReminder = reminderService.snooze(id, minutes);
        if (snoozedReminder == null) {
            return Result.error(404, "提醒不存在或延迟失败");
        }

        return Result.success(ReminderResponse.fromEntity(snoozedReminder));
    }

    /**
     * 提醒响应DTO
     */
    public static class ReminderResponse {
        private Long id;
        private String type;
        private String title;
        private String content;
        private String remindAt;
        private Boolean isRead;
        private Long refId;
        private String createdAt;

        public static ReminderResponse fromEntity(Reminder reminder) {
            ReminderResponse response = new ReminderResponse();
            response.setId(reminder.getId());
            response.setType(reminder.getType());
            response.setTitle(reminder.getTitle());
            response.setContent(reminder.getContent());
            response.setRemindAt(reminder.getRemindAt() != null ? reminder.getRemindAt().toString() : null);
            response.setIsRead(reminder.getIsRead());
            response.setRefId(reminder.getRefId());
            response.setCreatedAt(reminder.getCreatedAt() != null ? reminder.getCreatedAt().toString() : null);
            return response;
        }

        // Getters and Setters
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getRemindAt() {
            return remindAt;
        }

        public void setRemindAt(String remindAt) {
            this.remindAt = remindAt;
        }

        public Boolean getIsRead() {
            return isRead;
        }

        public void setIsRead(Boolean isRead) {
            this.isRead = isRead;
        }

        public Long getRefId() {
            return refId;
        }

        public void setRefId(Long refId) {
            this.refId = refId;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }
    }
}
