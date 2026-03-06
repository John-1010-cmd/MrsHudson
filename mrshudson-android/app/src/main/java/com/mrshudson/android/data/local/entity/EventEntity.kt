package com.mrshudson.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 日历事件实体类
 * 用于 Room 数据库存储日历事件
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
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey
    val id: Long,
    val userId: Long,
    val title: String,
    val description: String?,
    val startTime: String,
    val endTime: String,
    val location: String?,
    val category: String,
    val reminderMinutes: Int?,
    val isRecurring: Boolean?,
    val createdAt: String?,
    val updatedAt: String?
)
