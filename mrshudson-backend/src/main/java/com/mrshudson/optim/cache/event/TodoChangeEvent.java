package com.mrshudson.optim.cache.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 待办事项数据变更事件
 * 当待办事项被创建、更新、完成或删除时触发
 */
@Getter
public class TodoChangeEvent extends ApplicationEvent {

    private final Long userId;
    private final Long todoId;
    private final OperationType operationType;

    public TodoChangeEvent(Object source, Long userId, Long todoId, OperationType operationType) {
        super(source);
        this.userId = userId;
        this.todoId = todoId;
        this.operationType = operationType;
    }

    /**
     * 操作类型枚举
     */
    public enum OperationType {
        CREATE,
        UPDATE,
        COMPLETE,
        DELETE
    }
}
