package com.mrshudson.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 待办事项实体类
 * 用于 Room 数据库存储待办事项
 *
 * @property id 待办ID
 * @property userId 用户ID
 * @property title 待办标题
 * @property description 待办描述
 * @property priority 优先级
 * @property status 状态
 * @property dueDate 截止日期
 * @property completedAt 完成时间
 * @property createdAt 创建时间
 * @property updatedAt 更新时间
 */
@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey
    val id: Long,
    val userId: Long,
    val title: String,
    val description: String?,
    val priority: String,
    val status: String,
    val dueDate: String?,
    val completedAt: String?,
    val createdAt: String?,
    val updatedAt: String?
)
