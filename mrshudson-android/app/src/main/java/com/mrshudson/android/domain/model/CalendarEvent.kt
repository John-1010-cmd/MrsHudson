package com.mrshudson.android.domain.model

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 日历事件分类枚举
 */
enum class EventCategory {
    WORK,
    PERSONAL,
    FAMILY;

    companion object {
        fun fromString(value: String?): EventCategory {
            return when (value?.uppercase()) {
                "WORK" -> WORK
                "FAMILY" -> FAMILY
                else -> PERSONAL
            }
        }
    }

    fun toDisplayString(): String {
        return when (this) {
            WORK -> "工作"
            PERSONAL -> "个人"
            FAMILY -> "家庭"
        }
    }
}

/**
 * 日历事件领域模型
 *
 * @property id 事件ID
 * @property title 事件标题
 * @property description 事件描述
 * @property startTime 开始时间
 * @property endTime 结束时间
 * @property location 事件地点
 * @property category 事件分类
 * @property reminderMinutes 提醒分钟数
 * @property isRecurring 是否重复事件
 */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val description: String?,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val location: String?,
    val category: EventCategory,
    val reminderMinutes: Int?,
    val isRecurring: Boolean?
) {
    /**
     * 格式化显示日期时间
     */
    fun formattedDateTime(): String {
        val dateFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm")
        return "${startTime.format(dateFormatter)} - ${endTime.format(dateFormatter)}"
    }

    /**
     * 判断事件是否在指定日期
     */
    fun isOnDate(year: Int, month: Int, day: Int): Boolean {
        return startTime.year == year && startTime.monthValue == month && startTime.dayOfMonth == day
    }

    /**
     * 判断事件是否正在进行中
     */
    fun isOngoing(): Boolean {
        val now = LocalDateTime.now()
        return now.isAfter(startTime) && now.isBefore(endTime)
    }

    /**
     * 判断事件是否即将开始
     */
    fun isUpcoming(): Boolean {
        val now = LocalDateTime.now()
        return now.isBefore(startTime)
    }
}

/**
 * 创建新日历事件的辅助函数
 *
 * @param id 事件ID
 * @param title 事件标题
 * @param description 事件描述
 * @param startTime 开始时间字符串
 * @param endTime 结束时间字符串
 * @param location 事件地点
 * @param category 事件分类
 * @param reminderMinutes 提醒分钟数
 * @param isRecurring 是否重复
 */
fun createCalendarEvent(
    id: Long,
    title: String,
    description: String?,
    startTime: String,
    endTime: String,
    location: String?,
    category: String?,
    reminderMinutes: Int?,
    isRecurring: Boolean?
): CalendarEvent {
    val startDateTime = parseDateTime(startTime)
    val endDateTime = parseDateTime(endTime)

    return CalendarEvent(
        id = id,
        title = title,
        description = description,
        startTime = startDateTime,
        endTime = endDateTime,
        location = location,
        category = EventCategory.fromString(category),
        reminderMinutes = reminderMinutes,
        isRecurring = isRecurring
    )
}

/**
 * 解析日期时间字符串
 */
private fun parseDateTime(dateTimeStr: String): LocalDateTime {
    return try {
        // 尝试 ISO 格式
        LocalDateTime.parse(dateTimeStr.replace("Z", "").substringBefore("."))
    } catch (e: Exception) {
        LocalDateTime.now()
    }
}
