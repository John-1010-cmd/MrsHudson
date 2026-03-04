package com.mrshudson.mcp.todo;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mrshudson.domain.entity.TodoItem;

import com.mrshudson.mcp.BaseTool;
import com.mrshudson.mcp.ToolRegistry;
import com.mrshudson.mcp.kimi.dto.Tool;
import com.mrshudson.service.TodoService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * 待办事项MCP工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TodoTool implements BaseTool {

    private final TodoService todoService;
    private final ToolRegistry toolRegistry;

    // 创建待办工具定义
    private static final Tool CREATE_TODO_TOOL = Tool.builder()
            .type("function")
            .function(Tool.Function.builder()
                    .name("create_todo")
                    .description("为用户创建一个新的待办事项")
                    .parameters(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "title", Map.of(
                                            "type", "string",
                                            "description", "待办事项标题，简洁明了地描述待办内容"
                                    ),
                                    "description", Map.of(
                                            "type", "string",
                                            "description", "待办事项详细描述（可选）"
                                    ),
                                    "priority", Map.of(
                                            "type", "string",
                                            "description", "优先级：low(低), medium(中), high(高)，默认为medium",
                                            "enum", new String[]{"low", "medium", "high"}
                                    ),
                                    "due_date", Map.of(
                                            "type", "string",
                                            "description", "截止日期，格式为ISO 8601，如2026-03-01T15:00:00（可选）"
                                    )
                            ),
                            "required", new String[]{"title"}
                    ))
                    .build())
            .build();

    // 列出待办工具定义
    private static final Tool LIST_TODOS_TOOL = Tool.builder()
            .type("function")
            .function(Tool.Function.builder()
                    .name("list_todos")
                    .description("查询用户的待办事项列表，支持按状态筛选")
                    .parameters(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "status", Map.of(
                                            "type", "string",
                                            "description", "筛选状态：all(全部), pending(待处理), in_progress(进行中), completed(已完成)，默认为all",
                                            "enum", new String[]{"all", "pending", "in_progress", "completed"}
                                    ),
                                    "limit", Map.of(
                                            "type", "integer",
                                            "description", "返回的最大数量，默认为10"
                                    )
                            ),
                            "required", new String[]{}
                    ))
                    .build())
            .build();

    // 完成待办工具定义
    private static final Tool COMPLETE_TODO_TOOL = Tool.builder()
            .type("function")
            .function(Tool.Function.builder()
                    .name("complete_todo")
                    .description("将指定的待办事项标记为已完成")
                    .parameters(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "todo_id", Map.of(
                                            "type", "integer",
                                            "description", "要完成的待办事项ID"
                                    )
                            ),
                            "required", new String[]{"todo_id"}
                    ))
                    .build())
            .build();

    // 删除待办工具定义
    private static final Tool DELETE_TODO_TOOL = Tool.builder()
            .type("function")
            .function(Tool.Function.builder()
                    .name("delete_todo")
                    .description("删除指定的待办事项")
                    .parameters(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "todo_id", Map.of(
                                            "type", "integer",
                                            "description", "要删除的待办事项ID"
                                    )
                            ),
                            "required", new String[]{"todo_id"}
                    ))
                    .build())
            .build();

    @PostConstruct
    public void register() {
        toolRegistry.registerTool(CREATE_TODO_TOOL, this::createTodo);
        toolRegistry.registerTool(LIST_TODOS_TOOL, this::listTodos);
        toolRegistry.registerTool(COMPLETE_TODO_TOOL, this::completeTodo);
        toolRegistry.registerTool(DELETE_TODO_TOOL, this::deleteTodo);
        log.info("待办事项工具已注册: create_todo, list_todos, complete_todo, delete_todo");
    }

    @Override
    public Tool getToolDefinition() {
        return CREATE_TODO_TOOL;
    }

    @Override
    public String execute(String arguments) {
        return createTodo(arguments);
    }

    /**
     * 创建待办事项
     */
    private String createTodo(String arguments) {
        try {
            JSONObject params = JSON.parseObject(arguments);

            // 解析必填参数
            String title = params.getString("title");

            if (title == null || title.isEmpty()) {
                return "错误：待办事项标题不能为空";
            }

            // 解析可选参数
            String description = params.getString("description");
            String priorityStr = params.getString("priority");
            String dueDateStr = params.getString("due_date");

            String priority = parsePriority(priorityStr);
            LocalDateTime dueDate = parseDateTime(dueDateStr);

            // TODO: 从上下文中获取当前用户ID，这里暂时使用1L
            Long userId = 1L;

            TodoItem todo = todoService.createTodo(userId, title, description, priority, dueDate);

            return String.format("✅ 待办事项创建成功！\n\n%s", todoService.formatTodoToText(todo));

        } catch (IllegalArgumentException e) {
            return "创建待办事项失败: " + e.getMessage();
        } catch (Exception e) {
            JSONObject params = JSON.parseObject(arguments);
            log.error("创建待办事项失败: title={}, priority={}, dueDate={}",
                    params.getString("title"), params.getString("priority"), params.getString("due_date"), e);
            return "创建待办事项失败: " + e.getMessage();
        }
    }

    /**
     * 列出待办事项
     */
    private String listTodos(String arguments) {
        try {
            JSONObject params = JSON.parseObject(arguments);

            // 解析参数
            String statusStr = params.getString("status");
            Integer limit = params.getInteger("limit");

            if (limit == null || limit <= 0) {
                limit = 10;
            }

            // TODO: 从上下文中获取当前用户ID
            Long userId = 1L;

            List<TodoItem> todos;
            String title;

            String status = parseStatus(statusStr);
            if (status != null) {
                todos = todoService.getTodosByStatus(userId, status);
                title = getStatusTitle(statusStr);
            } else {
                todos = todoService.getTodos(userId);
                title = "全部待办事项";
            }

            // 限制数量
            if (todos.size() > limit) {
                todos = todos.subList(0, limit);
            }

            return todoService.formatTodosToText(todos, title);

        } catch (Exception e) {
            JSONObject params = JSON.parseObject(arguments);
            log.error("查询待办事项失败: status={}, limit={}",
                    params.getString("status"), params.getInteger("limit"), e);
            return "查询待办事项失败: " + e.getMessage();
        }
    }

    /**
     * 完成待办事项
     */
    private String completeTodo(String arguments) {
        try {
            JSONObject params = JSON.parseObject(arguments);
            Long todoId = params.getLong("todo_id");

            if (todoId == null) {
                return "错误：请提供待办事项ID";
            }

            // TODO: 从上下文中获取当前用户ID
            Long userId = 1L;

            boolean success = todoService.completeTodo(userId, todoId);

            if (success) {
                return "✅ 待办事项已完成！做得好！";
            } else {
                return "完成失败：待办事项不存在或您没有权限";
            }

        } catch (Exception e) {
            JSONObject params = JSON.parseObject(arguments);
            log.error("完成待办事项失败: todoId={}", params.getLong("todo_id"), e);
            return "完成待办事项失败: " + e.getMessage();
        }
    }

    /**
     * 删除待办事项
     */
    private String deleteTodo(String arguments) {
        try {
            JSONObject params = JSON.parseObject(arguments);
            Long todoId = params.getLong("todo_id");

            if (todoId == null) {
                return "错误：请提供待办事项ID";
            }

            // TODO: 从上下文中获取当前用户ID
            Long userId = 1L;

            boolean success = todoService.deleteTodo(userId, todoId);

            if (success) {
                return "🗑️ 待办事项已删除";
            } else {
                return "删除失败：待办事项不存在或您没有权限删除";
            }

        } catch (Exception e) {
            JSONObject params = JSON.parseObject(arguments);
            log.error("删除待办事项失败: todoId={}", params.getLong("todo_id"), e);
            return "删除待办事项失败: " + e.getMessage();
        }
    }

    /**
     * 解析优先级字符串
     */
    private String parsePriority(String priorityStr) {
        if (priorityStr == null || priorityStr.isEmpty()) {
            return TodoItem.Priority.MEDIUM.name();
        }
        return switch (priorityStr.toLowerCase()) {
            case "low" -> TodoItem.Priority.LOW.name();
            case "high" -> TodoItem.Priority.HIGH.name();
            default -> TodoItem.Priority.MEDIUM.name();
        };
    }

    /**
     * 解析状态字符串
     */
    private String parseStatus(String statusStr) {
        if (statusStr == null || statusStr.isEmpty() || "all".equalsIgnoreCase(statusStr)) {
            return null;
        }
        return switch (statusStr.toLowerCase()) {
            case "pending" -> TodoItem.Status.PENDING.name();
            case "in_progress" -> TodoItem.Status.IN_PROGRESS.name();
            case "completed" -> TodoItem.Status.COMPLETED.name();
            default -> null;
        };
    }

    /**
     * 获取状态标题
     */
    private String getStatusTitle(String statusStr) {
        return switch (statusStr.toLowerCase()) {
            case "pending" -> "待处理的待办事项";
            case "in_progress" -> "进行中的待办事项";
            case "completed" -> "已完成的待办事项";
            default -> "待办事项";
        };
    }

    /**
     * 解析日期时间字符串
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            // 尝试ISO格式
            return LocalDateTime.parse(dateTimeStr);
        } catch (DateTimeParseException e) {
            try {
                // 尝试带T的格式
                if (dateTimeStr.contains("T")) {
                    return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                }
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }
}
