package com.mrshudson.android.domain.model

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 待办事项优先级枚举
 */
enum class TodoPriority {
    LOW,
    MEDIUM,
    HIGH;

    companion object {
        fun fromString(value: String?): TodoPriority {
            return when (value?.uppercase()) {
                "LOW" -> LOW
                "HIGH" -> HIGH
                else -> MEDIUM
            }
        }
    }

    fun toDisplayString(): String {
        return when (this) {
            LOW -> "低"
            MEDIUM -> "中"
            HIGH -> "高"
        }
    }

    /**
     * 获取优先级对应的颜色值
     */
    fun toColor(): Long {
        return when (this) {
            HIGH -> 0xFFE53935  // 红色
            MEDIUM -> 0xFFFFA726 // 橙色
            LOW -> 0xFF66BB6A    // 绿色
        }
    }

    fun toApiString(): String {
        return name
    }
}

/**
 * 待办事项状态枚举
 */
enum class TodoStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED;

    companion object {
        fun fromString(value: String?): TodoStatus {
            return when (value?.uppercase()) {
                "IN_PROGRESS" -> IN_PROGRESS
                "COMPLETED" -> COMPLETED
                else -> PENDING
            }
        }
    }

    fun toDisplayString(): String {
        return when (this) {
            PENDING -> "待处理"
            IN_PROGRESS -> "进行中"
            COMPLETED -> "已完成"
        }
    }

    fun toApiString(): String {
        return name
    }
}

/**
 * 待办事项领域模型
 *
 * @property id 待办ID
 * @property title 待办标题
 * @property description 待办描述
 * @property priority 优先级
 * @property status 状态
 * @property dueDate 截止日期
 * @property completedAt 完成时间
 * @property createdAt 创建时间
 */
data class TodoItem(
    val id: Long,
    val title: String,
    val description: String?,
    val priority: TodoPriority,
    val status: TodoStatus,
    val dueDate: LocalDateTime?,
    val completedAt: LocalDateTime?,
    val createdAt: LocalDateTime?
) {
    /**
     * 判断待办是否已完成
     */
    fun isCompleted(): Boolean = status == TodoStatus.COMPLETED

    /**
     * 判断待办是否已逾期
     */
    fun isOverdue(): Boolean {
        if (isCompleted()) return false
        val now = LocalDateTime.now()
        return dueDate != null && now.isAfter(dueDate)
    }

    /**
     * 格式化截止日期显示
     */
    fun formattedDueDate(): String? {
        return dueDate?.let {
            val formatter = DateTimeFormatter.ofPattern("MM/dd HH:mm")
            it.format(formatter)
        }
    }

    /**
     * 获取优先级对应的颜色值
     */
    fun priorityColor(): Long {
        return when (priority) {
            TodoPriority.HIGH -> 0xFFE53935  // 红色
            TodoPriority.MEDIUM -> 0xFFFFA726 // 橙色
            TodoPriority.LOW -> 0xFF66BB6A    // 绿色
        }
    }
}

/**
 * 创建新待办事项的辅助函数
 *
 * @param id 待办ID
 * @param title 待办标题
 * @param description 待办描述
 * @param priority 优先级
 * @param status 状态
 * @param dueDate 截止日期
 * @param completedAt 完成时间
 * @param createdAt 创建时间
 */
fun createTodoItem(
    id: Long,
    title: String,
    description: String?,
    priority: String?,
    status: String?,
    dueDate: String?,
    completedAt: String?,
    createdAt: String?
): TodoItem {
    return TodoItem(
        id = id,
        title = title,
        description = description,
        priority = TodoPriority.fromString(priority),
        status = TodoStatus.fromString(status),
        dueDate = dueDate?.let { parseDateTime(it) },
        completedAt = completedAt?.let { parseDateTime(it) },
        createdAt = createdAt?.let { parseDateTime(it) }
    )
}

/**
 * 解析日期时间字符串
 */
private fun parseDateTime(dateTimeStr: String): LocalDateTime {
    return try {
        LocalDateTime.parse(dateTimeStr.replace("Z", "").substringBefore("."))
    } catch (e: Exception) {
        LocalDateTime.now()
    }
}
