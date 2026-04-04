package com.mrshudson.service;

import com.mrshudson.domain.entity.TodoItem;
import com.mrshudson.domain.entity.TodoItem.Priority;
import com.mrshudson.domain.entity.TodoItem.Status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 待办事项服务接口
 */
public interface TodoService {

    /**
     * 创建待办事项
     *
     * @param userId      用户ID
     * @param title       标题
     * @param description 描述
     * @param priority    优先级
     * @param dueDate     截止日期
     * @return 创建的待办事项
     */
    TodoItem createTodo(Long userId, String title, String description,
                        String priority, LocalDateTime dueDate);

    /**
     * 获取用户的所有待办事项
     *
     * @param userId 用户ID
     * @return 待办事项列表
     */
    List<TodoItem> getTodos(Long userId);

    /**
     * 按状态获取待办事项
     *
     * @param userId 用户ID
     * @param status 状态
     * @return 待办事项列表
     */
    List<TodoItem> getTodosByStatus(Long userId, String status);

    /**
     * 根据ID获取待办事项
     *
     * @param todoId 待办事项ID
     * @return 待办事项
     */
    Optional<TodoItem> getTodoById(Long todoId);

    /**
     * 完成/取消完成待办事项
     *
     * @param userId 用户ID
     * @param todoId 待办事项ID
     * @param completed 是否完成
     * @return 更新后的待办事项
     */
    TodoItem completeTodo(Long userId, Long todoId, Boolean completed);

    /**
     * 删除待办事项
     *
     * @param userId 用户ID
     * @param todoId 待办事项ID
     * @return 是否成功
     */
    boolean deleteTodo(Long userId, Long todoId);

    /**
     * 更新待办事项
     *
     * @param userId 用户ID
     * @param todoId 待办事项ID
     * @param todo   更新的内容
     * @return 更新后的待办事项
     */
    TodoItem updateTodo(Long userId, Long todoId, TodoItem todo);

    /**
     * 获取待办事项详情文本（用于AI回复）
     *
     * @param todo 待办事项
     * @return 格式化的文本
     */
    String formatTodoToText(TodoItem todo);

    /**
     * 获取待办事项列表文本（用于AI回复）
     *
     * @param todos 待办事项列表
     * @param title 标题
     * @return 格式化的文本
     */
    String formatTodosToText(List<TodoItem> todos, String title);
}
