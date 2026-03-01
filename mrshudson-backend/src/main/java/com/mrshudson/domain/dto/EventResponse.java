package com.mrshudson.domain.dto;

import com.mrshudson.domain.entity.CalendarEvent;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 事件响应DTO
 */
@Data
public class EventResponse {

    private Long id;
    private Long userId;
    private String title;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String location;
    private String category;
    private Integer reminderMinutes;
    private Boolean isRecurring;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 从实体转换为响应DTO
     */
    public static EventResponse fromEntity(CalendarEvent event) {
        if (event == null) {
            return null;
        }
        EventResponse response = new EventResponse();
        response.setId(event.getId());
        response.setUserId(event.getUserId());
        response.setTitle(event.getTitle());
        response.setDescription(event.getDescription());
        response.setStartTime(event.getStartTime());
        response.setEndTime(event.getEndTime());
        response.setLocation(event.getLocation());
        response.setCategory(event.getCategory());
        response.setReminderMinutes(event.getReminderMinutes());
        response.setIsRecurring(event.getIsRecurring());
        response.setCreatedAt(event.getCreatedAt());
        response.setUpdatedAt(event.getUpdatedAt());
        return response;
    }
}
