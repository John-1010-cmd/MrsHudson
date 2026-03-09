package com.mrshudson.android.domain.model

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 提醒类型枚举
 */
enum class ReminderType {
    EVENT,
    TODO,
    WEATHER,
    SYSTEM;

    companion object {
        fun fromString(value: String?): ReminderType {
            return try {
                value?.let { valueOf(it) } ?: SYSTEM
            } catch (e: Exception) {
                SYSTEM
            }
        }
    }

    fun toDisplayString(): String {
        return when (this) {
            EVENT -> "日程"
            TODO -> "待办"
            WEATHER -> "天气"
            SYSTEM -> "系统"
        }
    }
}

/**
 * 提醒领域模型
 *
 * @property id 提醒ID
 * @property type 提醒类型
 * @property title 标题
 * @property content 内容
 * @property remindAt 提醒时间
 * @property isRead 是否已读
 * @property refId 关联ID
 * @property createdAt 创建时间
 */
data class Reminder(
    val id: Long,
    val type: ReminderType,
    val title: String,
    val content: String?,
    val remindAt: LocalDateTime,
    val isRead: Boolean,
    val refId: Long?,
    val createdAt: LocalDateTime?
) {
    /**
     * 判断是否为未读提醒
     */
    fun isUnread(): Boolean = !isRead

    /**
     * 格式化提醒时间显示
     */
    fun formattedRemindTime(): String {
        val now = LocalDateTime.now()
        val minutesDiff = ChronoUnit.MINUTES.between(now, remindAt)
        val hoursDiff = ChronoUnit.HOURS.between(now, remindAt)
        val daysDiff = ChronoUnit.DAYS.between(now, remindAt)

        return when {
            minutesDiff < 0 -> "已过期"
            minutesDiff < 60 -> "${minutesDiff}分钟后"
            hoursDiff < 24 -> "${hoursDiff}小时后"
            daysDiff < 7 -> "${daysDiff}天后"
            else -> {
                val formatter = DateTimeFormatter.ofPattern("MM/dd HH:mm")
                remindAt.format(formatter)
            }
        }
    }

    /**
     * 格式化创建时间
     */
    fun formattedCreatedTime(): String? {
        return createdAt?.let {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            it.format(formatter)
        }
    }
}

/**
 * 创建提醒的辅助函数
 */
fun createReminder(
    id: Long,
    type: String?,
    title: String,
    content: String?,
    remindAt: String?,
    isRead: Boolean?,
    refId: Long?,
    createdAt: String?
): Reminder {
    return Reminder(
        id = id,
        type = ReminderType.fromString(type),
        title = title,
        content = content,
        remindAt = parseDateTime(remindAt),
        isRead = isRead ?: false,
        refId = refId,
        createdAt = parseDateTime(createdAt)
    )
}

/**
 * 解析日期时间字符串
 */
private fun parseDateTime(dateTimeStr: String?): LocalDateTime {
    if (dateTimeStr.isNullOrBlank()) return LocalDateTime.now()
    return try {
        LocalDateTime.parse(dateTimeStr.replace("Z", "").substringBefore("."))
    } catch (e: Exception) {
        LocalDateTime.now()
    }
}
