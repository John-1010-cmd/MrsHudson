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
 * 待办删除意图处理器
 * 识别删除待办的请求，提取参数并删除待办事项
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TodoDeleteIntentHandler extends AbstractIntentHandler {

    private final TodoService todoService;

    // 删除类关键词
    private static final String[] DELETE_KEYWORDS = {
        "删除", "取消", "移除", "删掉", "去掉"
    };

    @Override
    public IntentType getIntentType() {
        return IntentType.TODO_DELETE;
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

        log.debug("删除待办参数: todoId={}, title={}", todoId, title);

        TodoItem deletedTodo = null;

        // 如果提供了todoId，直接删除
        if (todoId != null) {
            try {
                Long id = Long.parseLong(todoId);
                deletedTodo = todoService.getTodoById(id).orElse(null);
                if (deletedTodo != null && deletedTodo.getUserId().equals(userId)) {
                    todoService.deleteTodo(userId, id);
                } else {
                    return RouteResult.builder()
                        .handled(true)
                        .response("❌ 未找到ID为 " + todoId + " 的待办事项")
                        .intentType(IntentType.TODO_DELETE)
                        .confidence(0.9)
                        .routerLayer("rule")
                        .build();
                }
            } catch (NumberFormatException e) {
                log.warn("无效的待办ID: {}", todoId);
            }
        }

        // 如果提供了标题，尝试查找并删除
        if (deletedTodo == null && title != null) {
            List<TodoItem> todos = todoService.getTodos(userId);
            for (TodoItem todo : todos) {
                if (todo.getTitle().contains(title)) {
                    todoService.deleteTodo(userId, todo.getId());
                    deletedTodo = todo;
                    break;
                }
            }
        }

        // 构建响应
        String response;
        if (deletedTodo != null) {
            response = "🗑️ 已删除待办：\n\n" + deletedTodo.getTitle();
        } else {
            response = "❌ 未找到匹配的待办事项\n\n您可以先查看待办列表，然后说\"删除待办1\"或\"删除XX任务\"";
        }

        Map<String, Object> resultParams = new HashMap<>();
        if (deletedTodo != null) {
            resultParams.put("todoId", deletedTodo.getId());
            resultParams.put("title", deletedTodo.getTitle());
        }

        return RouteResult.builder()
            .handled(true)
            .response(response)
            .intentType(IntentType.TODO_DELETE)
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

        // 检查是否有删除关键词
        boolean hasDeleteKeyword = false;
        for (String keyword : DELETE_KEYWORDS) {
            if (lowerQuery.contains(keyword)) {
                hasDeleteKeyword = true;
                break;
            }
        }

        if (!hasDeleteKeyword) {
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
