package com.mrshudson.android.data.remote.dto

/**
 * 发送消息请求数据类
 *
 * @property message 消息内容
 * @property sessionId 会话ID（可选）
 * @property conversationId 会话ID，可选，为空时创建新会话
 */
data class SendMessageRequest(
    val message: String,
    val sessionId: String? = null,
    val conversationId: Long? = null
)
