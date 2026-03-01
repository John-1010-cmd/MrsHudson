package com.mrshudson.domain.dto;

import com.mrshudson.domain.entity.TodoItem;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待办事项响应DTO
 */
@Data
public class TodoResponse {

    private Long id;
    private Long userId;
    private String title;
    private String description;
    private String priority;
    private String status;
    private LocalDateTime dueDate;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 从实体转换为响应DTO
     */
    public static TodoResponse fromEntity(TodoItem todo) {
        if (todo == null) {
            return null;
        }
        TodoResponse response = new TodoResponse();
        response.setId(todo.getId());
        response.setUserId(todo.getUserId());
        response.setTitle(todo.getTitle());
        response.setDescription(todo.getDescription());
        response.setPriority(todo.getPriority());
        response.setStatus(todo.getStatus());
        response.setDueDate(todo.getDueDate());
        response.setCompletedAt(todo.getCompletedAt());
        response.setCreatedAt(todo.getCreatedAt());
        response.setUpdatedAt(todo.getUpdatedAt());
        return response;
    }
}
