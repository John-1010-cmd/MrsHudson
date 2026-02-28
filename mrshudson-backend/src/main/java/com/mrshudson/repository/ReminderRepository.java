package com.mrshudson.repository;

import com.mrshudson.domain.entity.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提醒记录Repository
 */
@Repository
public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    /**
     * 查找用户未读的提醒
     */
    List<Reminder> findByUserIdAndIsReadFalseOrderByRemindAtDesc(Long userId);

    /**
     * 查找需要发送的提醒（已到提醒时间且未读）
     */
    List<Reminder> findByRemindAtBeforeAndIsReadFalse(LocalDateTime now);

    /**
     * 查找用户的所有提醒
     */
    List<Reminder> findByUserIdOrderByRemindAtDesc(Long userId);
}
