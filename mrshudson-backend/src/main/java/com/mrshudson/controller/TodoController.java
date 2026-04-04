package com.mrshudson.controller;

import com.mrshudson.domain.dto.CompleteTodoRequest;
import com.mrshudson.domain.dto.CreateTodoRequest;
import com.mrshudson.domain.dto.Result;
import com.mrshudson.domain.dto.TodoResponse;
import com.mrshudson.domain.dto.UpdateTodoRequest;
import com.mrshudson.domain.entity.TodoItem;
import com.mrshudson.service.TodoService;
import com.mrshudson.util.CurrentUserUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 待办事项控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;
    private final CurrentUserUtil currentUserUtil;

    /**
     * 获取待办列表
     */
    @GetMapping
    public Result<List<TodoResponse>> getTodos(
            @RequestParam(required = false) String status) {

        Long userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        log.info("用户{}查询待办列表, 状态筛选: {}", userId, status);

        List<TodoItem> todos;
        if (status != null) {
            todos = todoService.getTodosByStatus(userId, status);
        } else {
            todos = todoService.getTodos(userId);
        }

        List<TodoResponse> responses = todos.stream()
                .map(TodoResponse::fromEntity)
                .collect(Collectors.toList());

        return Result.success(responses);
    }

    /**
     * 创建待办事项
     */
    @PostMapping
    public Result<TodoResponse> createTodo(
            @Valid @RequestBody CreateTodoRequest request) {

        Long userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        log.info("用户{}创建待办事项: {}", userId, request.getTitle());

        TodoItem todo = todoService.createTodo(
                userId,
                request.getTitle(),
                request.getDescription(),
                request.getPriority(),
                request.getDueDate()
        );

        return Result.success(TodoResponse.fromEntity(todo));
    }

    /**
     * 获取单个待办详情
     */
    @GetMapping("/{id}")
    public Result<TodoResponse> getTodoById(
            @PathVariable Long id) {

        Long userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        TodoItem todo = todoService.getTodoById(id).orElse(null);
        if (todo == null) {
            return Result.error(404, "待办事项不存在");
        }

        if (!todo.getUserId().equals(userId)) {
            return Result.error(403, "无权访问此待办事项");
        }

        return Result.success(TodoResponse.fromEntity(todo));
    }

    /**
     * 更新待办事项
     */
    @PutMapping("/{id}")
    public Result<TodoResponse> updateTodo(
            @PathVariable Long id,
            @RequestBody UpdateTodoRequest request) {

        Long userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        log.info("用户{}更新待办事项: {}", userId, id);

        TodoItem existingTodo = todoService.getTodoById(id).orElse(null);
        if (existingTodo == null) {
            return Result.error(404, "待办事项不存在");
        }

        if (!existingTodo.getUserId().equals(userId)) {
            return Result.error(403, "无权更新此待办事项");
        }

        // 构建更新实体
        TodoItem updateEntity = new TodoItem();
        updateEntity.setTitle(request.getTitle());
        updateEntity.setDescription(request.getDescription());
        updateEntity.setPriority(request.getPriority());
        updateEntity.setStatus(request.getStatus());
        updateEntity.setDueDate(request.getDueDate());

        TodoItem updated = todoService.updateTodo(userId, id, updateEntity);
        return Result.success(TodoResponse.fromEntity(updated));
    }

    /**
     * 完成/取消完成待办事项
     */
    @PatchMapping("/{id}/complete")
    public Result<TodoResponse> completeTodo(
            @PathVariable Long id,
            @RequestBody CompleteTodoRequest request) {

        Long userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        log.info("用户{}完成待办事项: {}, completed: {}", userId, id, request.getCompleted());

        TodoItem todo = todoService.completeTodo(userId, id, request.getCompleted());
        if (todo == null) {
            return Result.error(404, "待办事项不存在或无权完成");
        }
        return Result.success(TodoResponse.fromEntity(todo));
    }

    /**
     * 删除待办事项
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteTodo(
            @PathVariable Long id) {

        Long userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        log.info("用户{}删除待办事项: {}", userId, id);

        boolean success = todoService.deleteTodo(userId, id);
        if (success) {
            return Result.success(null);
        } else {
            return Result.error(404, "待办事项不存在或删除失败");
        }
    }
}
