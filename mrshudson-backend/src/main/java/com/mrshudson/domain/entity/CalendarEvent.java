package com.mrshudson.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 日历事件实体
 */
@Data
@Entity
@Table(name = "calendar_event")
@EntityListeners(AuditingEntityListener.class)
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(length = 200)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Category category = Category.PERSONAL;

    @Column(name = "reminder_minutes")
    private Integer reminderMinutes = 15;

    @Column(name = "is_recurring")
    private Boolean isRecurring = false;

    @Column(name = "recurrence_rule", length = 100)
    private String recurrenceRule;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 事件分类枚举
     */
    public enum Category {
        WORK,
        PERSONAL,
        FAMILY
    }
}
