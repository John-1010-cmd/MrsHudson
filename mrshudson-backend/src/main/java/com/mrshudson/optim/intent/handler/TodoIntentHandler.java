package com.mrshudson.optim.intent.handler;

import com.mrshudson.domain.entity.TodoItem;
import com.mrshudson.optim.intent.IntentType;
import com.mrshudson.optim.intent.RouteResult;
import com.mrshudson.service.TodoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 待办查询意图处理器
 * 识别待办相关查询，返回用户未完成的待办事项列表
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TodoIntentHandler extends AbstractIntentHandler {

    private final TodoService todoService;

    // 待办查询关键词
    private static final String[] TODO_KEYWORDS = {
            "待办", "任务", "todo", "提醒", "未完成", "要做"
    };

    @Override
    public IntentType getIntentType() {
        return IntentType.TODO_QUERY;
    }

    @Override
    protected RouteResult doHandle(Long userId, String query, Map<String, Object> parameters) {
        log.debug("查询用户{}的待办事项", userId);

        // 查询所有待办事项
        List<TodoItem> allTodos = todoService.getTodos(userId);

        // 过滤未完成的待办，并按优先级排序
        List<TodoItem> pendingTodos = allTodos.stream()
                .filter(todo -> !TodoItem.Status.COMPLETED.name().equals(todo.getStatus()))
                .sorted(Comparator
                        .comparing(this::getPriorityOrder).reversed()
                        .thenComparing(TodoItem::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        // 格式化结果
        String response = formatTodoResponse(pendingTodos);

        // 构建参数Map
        Map<String, Object> resultParams = new HashMap<>();
        resultParams.put("pendingCount", pendingTodos.size());
        resultParams.put("totalCount", allTodos.size());
        resultParams.put("query", query);

        // 返回结果，固定置信度0.95
        return RouteResult.builder()
                .handled(true)
                .response(response)
                .intentType(IntentType.TODO_QUERY)
                .confidence(0.95)
                .parameters(resultParams)
                .routerLayer("rule")
                .build();
    }

    /**
     * 获取优先级排序值（高=3，中=2，低=1）
     */
    private int getPriorityOrder(TodoItem todo) {
        String priority = todo.getPriority();
        if (TodoItem.Priority.HIGH.name().equals(priority)) {
            return 3;
        } else if (TodoItem.Priority.MEDIUM.name().equals(priority)) {
            return 2;
        } else {
            return 1;
        }
    }

    /**
     * 格式化待办查询响应
     */
    private String formatTodoResponse(List<TodoItem> todos) {
        if (todos == null || todos.isEmpty()) {
            return "✅ 太棒了！您当前没有未完成的待办事项。\n\n您可以对我说：\n• \"帮我添加一个待办\"\n• \"创建一个任务\"";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📝 您有 ").append(todos.size()).append(" 项待办事项：\n\n");

        int index = 1;
        for (TodoItem todo : todos) {
            // 优先级图标
            String priorityIcon = getPriorityIcon(todo.getPriority());

            sb.append(index).append(". ").append(priorityIcon).append(" ").append(todo.getTitle());

            // 截止日期
            if (todo.getDueDate() != null) {
                sb.append(" 📅").append(formatDueDate(todo.getDueDate()));
            }

            sb.append("\n");
            index++;
        }

        // 添加提示
        sb.append("\n💡 提示：\n");
        sb.append("🔴 高优先级  🟡 中优先级  🟢 低优先级\n");
        sb.append("\n您可以对我说：\"完成第1个任务\"来标记完成");

        return sb.toString();
    }

    /**
     * 获取优先级图标
     */
    private String getPriorityIcon(String priority) {
        if (TodoItem.Priority.HIGH.name().equals(priority)) {
            return "🔴";
        } else if (TodoItem.Priority.MEDIUM.name().equals(priority)) {
            return "🟡";
        } else {
            return "🟢";
        }
    }

    /**
     * 格式化截止日期
     */
    private String formatDueDate(java.time.LocalDateTime dueDate) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime today = now.withHour(0).withMinute(0);
        java.time.LocalDateTime tomorrow = today.plusDays(1);

        if (dueDate.toLocalDate().equals(today.toLocalDate())) {
            return "今天";
        } else if (dueDate.toLocalDate().equals(tomorrow.toLocalDate())) {
            return "明天";
        } else {
            return String.format("%d月%d日", dueDate.getMonthValue(), dueDate.getDayOfMonth());
        }
    }

    @Override
    public double canHandle(String query) {
        if (query == null || query.isEmpty()) {
            return 0.0;
        }

        String lowerQuery = query.toLowerCase();
        int matchCount = 0;

        // 检查待办关键词
        for (String keyword : TODO_KEYWORDS) {
            if (lowerQuery.contains(keyword.toLowerCase())) {
                matchCount++;
            }
        }

        if (matchCount == 0) {
            return 0.0;
        }

        // 待办查询固定返回高置信度0.95
        return 0.95;
    }
}
