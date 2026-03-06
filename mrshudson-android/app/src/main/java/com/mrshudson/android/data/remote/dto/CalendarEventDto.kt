package com.mrshudson.android.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 日历事件响应 DTO
 *
 * @property id 事件ID
 * @property userId 用户ID
 * @property title 事件标题
 * @property description 事件描述
 * @property startTime 开始时间
 * @property endTime 结束时间
 * @property location 事件地点
 * @property category 事件分类
 * @property reminderMinutes 提醒分钟数
 * @property isRecurring 是否重复事件
 * @property createdAt 创建时间
 * @property updatedAt 更新时间
 */
data class CalendarEventDto(
    val id: Long,
    @SerializedName("user_id")
    val userId: Long,
    val title: String,
    val description: String?,
    @SerializedName("start_time")
    val startTime: String,
    @SerializedName("end_time")
    val endTime: String,
    val location: String?,
    val category: String,
    @SerializedName("reminder_minutes")
    val reminderMinutes: Int?,
    @SerializedName("is_recurring")
    val isRecurring: Boolean?,
    @SerializedName("created_at")
    val createdAt: String?,
    @SerializedName("updated_at")
    val updatedAt: String?
)

/**
 * 创建日历事件请求
 *
 * @property title 事件标题（必填）
 * @property description 事件描述（可选）
 * @property startTime 开始时间（必填）
 * @property endTime 结束时间（必填）
 * @property location 事件地点（可选）
 * @property category 事件分类（可选，默认 PERSONAL）
 * @property reminderMinutes 提醒分钟数（可选，默认 15）
 */
data class CreateEventRequest(
    val title: String,
    val description: String? = null,
    @SerializedName("start_time")
    val startTime: String,
    @SerializedName("end_time")
    val endTime: String,
    val location: String? = null,
    val category: String = "PERSONAL",
    @SerializedName("reminder_minutes")
    val reminderMinutes: Int = 15
)

/**
 * 更新日历事件请求
 *
 * @property title 事件标题
 * @property description 事件描述
 * @property startTime 开始时间
 * @property endTime 结束时间
 * @property location 事件地点
 * @property category 事件分类
 * @property reminderMinutes 提醒分钟数
 */
data class UpdateEventRequest(
    val title: String,
    val description: String? = null,
    @SerializedName("start_time")
    val startTime: String,
    @SerializedName("end_time")
    val endTime: String,
    val location: String? = null,
    val category: String? = null,
    @SerializedName("reminder_minutes")
    val reminderMinutes: Int? = null
)
