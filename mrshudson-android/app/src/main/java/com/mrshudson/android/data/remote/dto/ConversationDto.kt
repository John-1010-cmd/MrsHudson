package com.mrshudson.android.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 会话数据类
 *
 * @property id 会话ID
 * @property title 会话标题
 * @property provider AI提供商
 * @property lastMessageAt 最后消息时间
 * @property createdAt 创建时间
 */
data class ConversationDto(
    val id: Long,
    val title: String,
    val provider: String? = null,
    @SerializedName("last_message_at")
    val lastMessageAt: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null
)

/**
 * 创建会话请求
 *
 * @property title 会话标题
 */
data class CreateConversationRequest(
    val title: String = "新对话"
)

/**
 * 创建会话响应
 *
 * @property id 新创建的会话ID
 * @property title 会话标题
 */
data class CreateConversationResponse(
    val id: Long,
    val title: String
)

/**
 * 会话列表响应包装类
 * 对应后端 ConversationListResponse，包含 conversations 字段
 *
 * @property conversations 会话列表
 */
data class ConversationListResponseDto(
    val conversations: List<ConversationDto>? = null
)
