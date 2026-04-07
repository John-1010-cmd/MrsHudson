package com.mrshudson.optim.intent.handler;

import com.mrshudson.domain.entity.TodoItem;
import com.mrshudson.optim.intent.IntentType;
import com.mrshudson.optim.intent.RouteResult;
import com.mrshudson.service.TodoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 待办完成意图处理器
 * 识别完成待办的请求，提取参数并完成待办事项
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TodoCompleteIntentHandler extends AbstractIntentHandler {

    private final TodoService todoService;

    // 完成类关键词
    private static final String[] COMPLETE_KEYWORDS = {
        "完成", "做完", "划掉", "结束", "搞定"
    };

    @Override
    public IntentType getIntentType() {
        return IntentType.TODO_COMPLETE;
    }

    @Override
    protected RouteResult doHandle(Long userId, String query, Map<String, Object> parameters) {
        String todoId = null;
        String title = null;

        // 提取参数
        if (parameters.containsKey("todoId")) {
            todoId = parameters.get("todoId").toString();
        }
        if (parameters.containsKey("title")) {
            title = parameters.get("title").toString();
        }

        log.debug("完成待办参数: todoId={}, title={}", todoId, title);

        TodoItem completedTodo = null;

        // 如果提供了todoId，直接完成
        if (todoId != null) {
            try {
                Long id = Long.parseLong(todoId);
                completedTodo = todoService.getTodoById(id).orElse(null);
                if (completedTodo != null && completedTodo.getUserId().equals(userId)) {
                    todoService.completeTodo(userId, id, true);
                } else {
                    return RouteResult.builder()
                        .handled(true)
                        .response("❌ 未找到ID为 " + todoId + " 的待办事项")
                        .intentType(IntentType.TODO_COMPLETE)
                        .confidence(0.9)
                        .routerLayer("rule")
                        .build();
                }
            } catch (NumberFormatException e) {
                log.warn("无效的待办ID: {}", todoId);
            }
        }

        // 如果提供了标题，尝试查找并完成
        if (completedTodo == null && title != null) {
            List<TodoItem> todos = todoService.getTodos(userId);
            for (TodoItem todo : todos) {
                if (todo.getTitle().contains(title) && !TodoItem.Status.COMPLETED.name().equals(todo.getStatus())) {
                    todoService.completeTodo(userId, todo.getId(), true);
                    completedTodo = todo;
                    break;
                }
            }
        }

        // 构建响应
        String response;
        if (completedTodo != null) {
            response = "✅ 已完成待办：\n\n" + completedTodo.getTitle() + "\n\n🎉 恭喜！又完成一项任务！";
        } else {
            response = "❌ 未找到匹配的未完成待办\n\n您可以先查看待办列表，然后说\"完成待办1\"或\"完成XX任务\"";
        }

        Map<String, Object> resultParams = new HashMap<>();
        if (completedTodo != null) {
            resultParams.put("todoId", completedTodo.getId());
            resultParams.put("title", completedTodo.getTitle());
        }

        return RouteResult.builder()
            .handled(true)
            .response(response)
            .intentType(IntentType.TODO_COMPLETE)
            .confidence(0.9)
            .parameters(resultParams)
            .routerLayer("rule")
            .build();
    }

    @Override
    public double canHandle(String query) {
        if (query == null || query.isEmpty()) {
            return 0.0;
        }

        String lowerQuery = query.toLowerCase();

        // 检查是否有完成关键词
        boolean hasCompleteKeyword = false;
        for (String keyword : COMPLETE_KEYWORDS) {
            if (lowerQuery.contains(keyword)) {
                hasCompleteKeyword = true;
                break;
            }
        }

        if (!hasCompleteKeyword) {
            return 0.0;
        }

        // 检查是否有待办相关关键词
        boolean hasTodoKeyword = false;
        for (String keyword : new String[]{"待办", "任务", "todo"}) {
            if (lowerQuery.contains(keyword.toLowerCase())) {
                hasTodoKeyword = true;
                break;
            }
        }

        if (!hasTodoKeyword) {
            return 0.0;
        }

        return 0.9;
    }
}
