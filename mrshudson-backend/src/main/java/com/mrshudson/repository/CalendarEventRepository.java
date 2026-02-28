package com.mrshudson.repository;

import com.mrshudson.domain.entity.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日历事件Repository
 */
@Repository
public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    /**
     * 查找用户在时间范围内的事件
     */
    List<CalendarEvent> findByUserIdAndStartTimeBetweenOrderByStartTimeAsc(
            Long userId, LocalDateTime start, LocalDateTime end);

    /**
     * 查找用户即将开始的事件
     */
    List<CalendarEvent> findByUserIdAndStartTimeAfterOrderByStartTimeAsc(
            Long userId, LocalDateTime now);
}
