package com.mrshudson.optim.cache.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 日历数据变更事件
 * 当日历事件被创建、更新或删除时触发
 */
@Getter
public class CalendarChangeEvent extends ApplicationEvent {

    private final Long userId;
    private final Long eventId;
    private final OperationType operationType;

    public CalendarChangeEvent(Object source, Long userId, Long eventId, OperationType operationType) {
        super(source);
        this.userId = userId;
        this.eventId = eventId;
        this.operationType = operationType;
    }

    /**
     * 操作类型枚举
     */
    public enum OperationType {
        CREATE,
        UPDATE,
        DELETE
    }
}
