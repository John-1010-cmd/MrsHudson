package com.mrshudson.repository;

import com.mrshudson.domain.entity.TodoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 待办事项Repository
 */
@Repository
public interface TodoItemRepository extends JpaRepository<TodoItem, Long> {

    /**
     * 查找用户的待办列表
     */
    List<TodoItem> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 按状态和优先级查找
     */
    List<TodoItem> findByUserIdAndStatusOrderByDueDateAsc(Long userId, TodoItem.Status status);

    /**
     * 查找逾期待办
     */
    List<TodoItem> findByUserIdAndStatusNotAndDueDateBefore(
            Long userId, TodoItem.Status status, java.time.LocalDateTime now);
}
