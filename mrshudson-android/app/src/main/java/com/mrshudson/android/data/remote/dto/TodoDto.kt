package com.mrshudson.android.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 待办事项响应 DTO
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
data class TodoDto(
    val id: Long,
    @SerializedName("user_id")
    val userId: Long,
    val title: String,
    val description: String?,
    val priority: String,
    val status: String,
    @SerializedName("due_date")
    val dueDate: String?,
    @SerializedName("completed_at")
    val completedAt: String?,
    @SerializedName("created_at")
    val createdAt: String?,
    @SerializedName("updated_at")
    val updatedAt: String?
)

/**
 * 创建待办事项请求
 *
 * @property title 待办标题（必填）
 * @property description 待办描述（可选）
 * @property priority 优先级（可选，默认 MEDIUM）
 * @property dueDate 截止日期（可选）
 */
data class CreateTodoRequest(
    val title: String,
    val description: String? = null,
    val priority: String = "MEDIUM",
    @SerializedName("due_date")
    val dueDate: String? = null
)

/**
 * 更新待办事项请求
 *
 * @property title 待办标题
 * @property description 待办描述
 * @property priority 优先级
 * @property status 状态
 * @property dueDate 截止日期
 */
data class UpdateTodoRequest(
    val title: String? = null,
    val description: String? = null,
    val priority: String? = null,
    val status: String? = null,
    @SerializedName("due_date")
    val dueDate: String? = null
)
