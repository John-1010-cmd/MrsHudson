package com.mrshudson.optim.intent.handler;

import com.mrshudson.domain.entity.TodoItem;
import com.mrshudson.optim.intent.IntentType;
import com.mrshudson.optim.intent.RouteResult;
import com.mrshudson.service.TodoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 待办创建意图处理器
 * 识别创建待办的请求，提取参数并创建待办事项
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TodoCreateIntentHandler extends AbstractIntentHandler {

    private final TodoService todoService;

    // 创建类关键词
    private static final String[] CREATE_KEYWORDS = {
        "创建", "添加", "新建", "增加", "记"
    };

    @Override
    public IntentType getIntentType() {
        return IntentType.TODO_CREATE;
    }

    @Override
    protected RouteResult doHandle(Long userId, String query, Map<String, Object> parameters) {
        // 提取参数
        String title = extractTitle(parameters, query);
        String priority = extractPriority(parameters);
        LocalDateTime dueDate = extractDueDate(parameters);

        log.debug("创建待办参数: title={}, priority={}, dueDate={}",
            title, priority, dueDate);

        // 创建待办
        TodoItem todo = todoService.createTodo(
            userId,
            title,
            null, // description
            priority,
            dueDate
        );

        // 构建响应
        String response = formatCreateResponse(todo);

        // 构建结果参数
        Map<String, Object> resultParams = new HashMap<>();
        resultParams.put("todoId", todo.getId());
        resultParams.put("title", todo.getTitle());
        resultParams.put("priority", todo.getPriority());
        if (todo.getDueDate() != null) {
            resultParams.put("dueDate", todo.getDueDate().toString());
        }

        return RouteResult.builder()
            .handled(true)
            .response(response)
            .intentType(IntentType.TODO_CREATE)
            .confidence(0.95)
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

        // 检查是否有创建关键词
        boolean hasCreateKeyword = false;
        for (String keyword : CREATE_KEYWORDS) {
            if (lowerQuery.contains(keyword)) {
                hasCreateKeyword = true;
                break;
            }
        }

        if (!hasCreateKeyword) {
            return 0.0;
        }

        // 检查是否有待办相关关键词
        boolean hasTodoKeyword = false;
        for (String keyword : new String[]{"待办", "任务", "todo", "提醒", "事情"}) {
            if (lowerQuery.contains(keyword.toLowerCase())) {
                hasTodoKeyword = true;
                break;
            }
        }

        if (!hasTodoKeyword) {
            return 0.0;
        }

        return 0.95;
    }

    /**
     * 提取待办标题
     */
    private String extractTitle(Map<String, Object> parameters, String query) {
        // 优先从参数中提取
        if (parameters.containsKey("title")) {
            String title = parameters.get("title").toString();
            if (!title.isEmpty()) {
                return title;
            }
        }

        // 尝试从查询中提取更具体的标题
        String lowerQuery = query.toLowerCase();

        // 匹配"添加一个XX任务/待办"模式
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(?:创建|添加|新建|记)\s*(?:一个|个)?\s*([^\s]{2,30})(?:任务|待办|提醒|事情)?"
        );
        java.util.regex.Matcher matcher = pattern.matcher(query);
        if (matcher.find()) {
            String extracted = matcher.group(1).trim();
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }

        // 默认标题
        return "新待办";
    }

    /**
     * 提取优先级
     */
    private String extractPriority(Map<String, Object> parameters) {
        if (parameters.containsKey("priority")) {
            String priority = parameters.get("priority").toString();
            // 标准化优先级值
            switch (priority.toLowerCase()) {
                case "high":
                case "高":
                    return "HIGH";
                case "medium":
                case "中":
                    return "MEDIUM";
                case "low":
                case "低":
                    return "LOW";
                default:
                    return priority.toUpperCase();
            }
        }

        // 检查查询中是否有优先级关键词
        String[] highKeywords = {"紧急", "重要", "高优先级", "着急", "尽快"};
        String[] lowKeywords = {"不急", "低优先级", "随便", "有空"};

        // 注意：无法直接访问query参数，依赖RuleBasedExtractor提取
        return "MEDIUM"; // 默认中等优先级
    }

    /**
     * 提取截止日期
     */
    private LocalDateTime extractDueDate(Map<String, Object> parameters) {
        // 优先使用完整日期时间
        if (parameters.containsKey("dueDateTime")) {
            Object value = parameters.get("dueDateTime");
            if (value instanceof LocalDateTime) {
                return (LocalDateTime) value;
            }
            try {
                return LocalDateTime.parse(value.toString());
            } catch (Exception e) {
                log.debug("解析dueDateTime失败: {}", value);
            }
        }

        // 使用日期
        if (parameters.containsKey("dueDate")) {
            Object value = parameters.get("dueDate");
            try {
                LocalDate date;
                if (value instanceof LocalDate) {
                    date = (LocalDate) value;
                } else {
                    date = LocalDate.parse(value.toString());
                }
                // 默认当天18点截止
                return date.atTime(18, 0);
            } catch (Exception e) {
                log.debug("解析dueDate失败: {}", value);
            }
        }

        // 默认今天18点
        return LocalDate.now().atTime(18, 0);
    }

    /**
     * 格式化创建成功响应
     */
    private String formatCreateResponse(TodoItem todo) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ 已为您创建待办：\n\n");

        // 优先级图标
        String priorityIcon = getPriorityIcon(todo.getPriority());
        sb.append(priorityIcon).append(" ").append(todo.getTitle()).append("\n");

        // 优先级文字
        String priorityText = getPriorityText(todo.getPriority());
        sb.append("🔔 优先级：").append(priorityText).append("\n");

        // 截止日期
        if (todo.getDueDate() != null) {
            LocalDateTime dueDate = todo.getDueDate();
            LocalDate today = LocalDate.now();
            LocalDate tomorrow = today.plusDays(1);

            String dateLabel;
            if (dueDate.toLocalDate().equals(today)) {
                dateLabel = "今天";
            } else if (dueDate.toLocalDate().equals(tomorrow)) {
                dateLabel = "明天";
            } else {
                dateLabel = dueDate.format(DateTimeFormatter.ofPattern("M月d日"));
            }

            String timeStr = String.format("%02d:%02d", dueDate.getHour(), dueDate.getMinute());
            sb.append("📅 截止：").append(dateLabel).append(" ").append(timeStr).append("\n");
        }

        return sb.toString();
    }

    /**
     * 获取优先级图标
     */
    private String getPriorityIcon(String priority) {
        if (priority == null) {
            return "🟡";
        }
        switch (priority.toUpperCase()) {
            case "HIGH":
                return "🔴";
            case "MEDIUM":
                return "🟡";
            case "LOW":
                return "🟢";
            default:
                return "🟡";
        }
    }

    /**
     * 获取优先级文字
     */
    private String getPriorityText(String priority) {
        if (priority == null) {
            return "中";
        }
        switch (priority.toUpperCase()) {
            case "HIGH":
                return "高";
            case "MEDIUM":
                return "中";
            case "LOW":
                return "低";
            default:
                return priority;
        }
    }
}
