package com.mrshudson.android.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 消息响应数据类
 *
 * @property id 消息ID
 * @property role 消息角色：user, assistant, system
 * @property content 消息内容
 * @property createdAt 创建时间
 */
data class MessageDto(
    val id: Long,
    val role: String,
    val content: String,
    @SerializedName("created_at")
    val createdAt: String
)

/**
 * 发送消息响应数据类
 * 包含AI返回的消息
 *
 * @property id 消息ID
 * @property role 消息角色
 * @property content 消息内容
 * @property createdAt 创建时间
 * @property conversationId 会话ID（新增消息时返回）
 */
data class SendMessageResponse(
    val id: Long,
    val role: String,
    val content: String,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("conversation_id")
    val conversationId: String? = null
)
