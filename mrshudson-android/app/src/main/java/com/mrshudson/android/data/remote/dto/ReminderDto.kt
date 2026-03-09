package com.mrshudson.android.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 提醒响应 DTO
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
data class ReminderDto(
    val id: Long,
    val type: String,
    val title: String,
    val content: String?,
    @SerializedName("remind_at")
    val remindAt: String?,
    @SerializedName("is_read")
    val isRead: Boolean?,
    @SerializedName("ref_id")
    val refId: Long?,
    @SerializedName("created_at")
    val createdAt: String?
)
