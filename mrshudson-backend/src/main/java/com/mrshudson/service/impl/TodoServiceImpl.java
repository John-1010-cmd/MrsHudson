package com.mrshudson.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mrshudson.domain.entity.TodoItem;
import com.mrshudson.domain.entity.TodoItem.Priority;
import com.mrshudson.domain.entity.TodoItem.Status;
import com.mrshudson.mapper.TodoItemMapper;
import com.mrshudson.service.ReminderService;
import com.mrshudson.service.TodoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 待办事项服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TodoServiceImpl implements TodoService {

    private final TodoItemMapper todoItemMapper;
    private final ReminderService reminderService;

    @Override
    @Transactional
    public TodoItem createTodo(Long userId, String title, String description,
                               String priority, LocalDateTime dueDate) {

        // 参数校验
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("待办事项标题不能为空");
        }

        TodoItem todo = new TodoItem();
        todo.setUserId(userId);
        todo.setTitle(title.trim());
        todo.setDescription(description);
        todo.setPriority(priority != null ? priority : TodoItem.Priority.MEDIUM.name());
        todo.setDueDate(dueDate);
        todo.setStatus(TodoItem.Status.PENDING.name());

        todoItemMapper.insert(todo);
        log.info("用户{}创建了待办事项: {}, ID: {}", userId, title, todo.getId());

        // 如果设置了截止日期，自动创建提醒
        if (dueDate != null) {
            try {
                reminderService.createTodoReminder(todo);
            } catch (Exception e) {
                log.warn("为待办事项创建提醒失败: todoId={}, error={}", todo.getId(), e.getMessage());
            }
        }

        return todo;
    }

    @Override
    public List<TodoItem> getTodos(Long userId) {
        return todoItemMapper.selectList(
                new LambdaQueryWrapper<TodoItem>()
                        .eq(TodoItem::getUserId, userId)
                        .orderByDesc(TodoItem::getCreatedAt)
        );
    }

    @Override
    public List<TodoItem> getTodosByStatus(Long userId, String status) {
        return todoItemMapper.selectList(
                new LambdaQueryWrapper<TodoItem>()
                        .eq(TodoItem::getUserId, userId)
                        .eq(TodoItem::getStatus, status)
                        .orderByAsc(TodoItem::getDueDate)
        );
    }

    @Override
    public Optional<TodoItem> getTodoById(Long todoId) {
        return Optional.ofNullable(todoItemMapper.selectById(todoId));
    }

    @Override
    @Transactional
    public boolean completeTodo(Long userId, Long todoId) {
        TodoItem todo = todoItemMapper.selectById(todoId);
        if (todo == null) {
            log.warn("完成待办失败，待办事项不存在: {}", todoId);
            return false;
        }

        if (!todo.getUserId().equals(userId)) {
            log.warn("完成待办失败，用户{}无权完成待办{}", userId, todoId);
            return false;
        }

        todo.setStatus(TodoItem.Status.COMPLETED.name());
        todo.setCompletedAt(LocalDateTime.now());
        todoItemMapper.updateById(todo);
        log.info("用户{}完成了待办事项: {}", userId, todoId);
        return true;
    }

    @Override
    @Transactional
    public boolean deleteTodo(Long userId, Long todoId) {
        TodoItem todo = todoItemMapper.selectById(todoId);
        if (todo == null) {
            log.warn("删除待办失败，待办事项不存在: {}", todoId);
            return false;
        }

        if (!todo.getUserId().equals(userId)) {
            log.warn("删除待办失败，用户{}无权删除待办{}", userId, todoId);
            return false;
        }

        todoItemMapper.deleteById(todoId);
        log.info("用户{}删除了待办事项: {}", userId, todoId);
        return true;
    }

    @Override
    @Transactional
    public TodoItem updateTodo(Long userId, Long todoId, TodoItem updatedTodo) {
        TodoItem todo = todoItemMapper.selectById(todoId);
        if (todo == null) {
            throw new IllegalArgumentException("待办事项不存在: " + todoId);
        }

        if (!todo.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权更新此待办事项");
        }

        // 更新字段
        if (updatedTodo.getTitle() != null) {
            todo.setTitle(updatedTodo.getTitle().trim());
        }
        if (updatedTodo.getDescription() != null) {
            todo.setDescription(updatedTodo.getDescription());
        }
        if (updatedTodo.getPriority() != null) {
            todo.setPriority(updatedTodo.getPriority());
        }
        if (updatedTodo.getDueDate() != null) {
            todo.setDueDate(updatedTodo.getDueDate());
        }
        if (updatedTodo.getStatus() != null) {
            todo.setStatus(updatedTodo.getStatus());
        }

        todoItemMapper.updateById(todo);
        log.info("用户{}更新了待办事项: {}", userId, todoId);
        return todo;
    }

    @Override
    public String formatTodoToText(TodoItem todo) {
        if (todo == null) {
            return "待办事项不存在";
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        StringBuilder sb = new StringBuilder();

        // 根据状态选择图标
        String icon = switch (Status.valueOf(todo.getStatus())) {
            case COMPLETED -> "✅";
            case IN_PROGRESS -> "🔄";
            case PENDING -> "⏳";
        };

        sb.append(icon).append(" ").append(todo.getTitle()).append("\n");
        sb.append("🆔 ID：").append(todo.getId()).append("\n");
        sb.append("📊 状态：").append(getStatusText(todo.getStatus())).append("\n");
        sb.append("🔥 优先级：").append(getPriorityText(todo.getPriority())).append("\n");

        if (todo.getDueDate() != null) {
            sb.append("📅 截止日期：").append(todo.getDueDate().format(formatter)).append("\n");
        }

        if (todo.getDescription() != null && !todo.getDescription().isEmpty()) {
            sb.append("📝 描述：").append(todo.getDescription()).append("\n");
        }

        if (todo.getCompletedAt() != null) {
            sb.append("✨ 完成时间：").append(todo.getCompletedAt().format(formatter)).append("\n");
        }

        return sb.toString();
    }

    @Override
    public String formatTodosToText(List<TodoItem> todos, String title) {
        if (todos == null || todos.isEmpty()) {
            return "📋 " + title + "\n\n暂无待办事项";
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        StringBuilder sb = new StringBuilder();
        sb.append("📋 ").append(title).append("\n");

        // 统计各状态数量
        long pendingCount = todos.stream().filter(t -> Status.PENDING.name().equals(t.getStatus())).count();
        long inProgressCount = todos.stream().filter(t -> Status.IN_PROGRESS.name().equals(t.getStatus())).count();
        long completedCount = todos.stream().filter(t -> Status.COMPLETED.name().equals(t.getStatus())).count();

        sb.append(String.format("共 %d 项（⏳待处理%d 🔄进行中%d ✅已完成%d）\n\n",
                todos.size(), pendingCount, inProgressCount, completedCount));

        // 按状态分组，优先级排序
        todos.stream()
                .sorted(Comparator
                        .comparing(TodoItem::getStatus)
                        .thenComparing((TodoItem t) -> t.getPriority() != null ? Priority.valueOf(t.getPriority()) : Priority.MEDIUM).reversed()
                        .thenComparing(TodoItem::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(todo -> {
                    String icon = switch (Status.valueOf(todo.getStatus())) {
                        case COMPLETED -> "✅";
                        case IN_PROGRESS -> "🔄";
                        case PENDING -> Priority.HIGH.name().equals(todo.getPriority()) ? "🔥" : "⏳";
                    };

                    String priorityTag = switch (Priority.valueOf(todo.getPriority())) {
                        case HIGH -> "[高]";
                        case MEDIUM -> "[中]";
                        case LOW -> "[低]";
                    };

                    String dueStr = "";
                    if (todo.getDueDate() != null) {
                        String dateStr = todo.getDueDate().format(formatter);
                        dueStr = " 📅" + dateStr;
                    }

                    sb.append(String.format("%s #%d %s %s%s\n",
                            icon, todo.getId(), priorityTag, todo.getTitle(), dueStr));
                });

        return sb.toString();
    }

    private String getStatusText(String status) {
        try {
            return switch (Status.valueOf(status)) {
                case PENDING -> "待处理";
                case IN_PROGRESS -> "进行中";
                case COMPLETED -> "已完成";
            };
        } catch (Exception e) {
            return status;
        }
    }

    private String getPriorityText(String priority) {
        try {
            return switch (Priority.valueOf(priority)) {
                case LOW -> "低";
                case MEDIUM -> "中";
                case HIGH -> "高";
            };
        } catch (Exception e) {
            return priority;
        }
    }
}
