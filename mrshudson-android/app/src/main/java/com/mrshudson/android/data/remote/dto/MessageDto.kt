package com.mrshudson.android.data.remote.dto

/**
 * 历史消息数据类
 * 对应后端 ChatHistoryResponse.MessageInfo
 *
 * @property id 消息ID
 * @property role 消息角色：user, assistant, system
 * @property content 消息内容
 * @property createdAt 创建时间
 * @property functionCall 工具调用信息（可选）
 * @property audioUrl 语音合成音频URL（AI消息可能有）
 * @property thinkingContent 思考推理过程（可选，仅支持推理的模型返回）
 */
data class MessageDto(
    val id: String? = null,
    val role: String? = null,
    val content: String? = null,
    val createdAt: String? = null,
    val functionCall: String? = null,
    val audioUrl: String? = null,
    val thinkingContent: String? = null
)

/**
 * 发送消息响应数据类
 * 对应后端 SendMessageResponse
 *
 * @property messageId 消息ID
 * @property content 消息内容
 * @property functionCalls 工具调用列表
 * @property createdAt 创建时间
 * @property audioUrl 语音合成音频URL（AI消息可能有）
 */
data class SendMessageResponse(
    val messageId: String? = null,
    val content: String? = null,
    val functionCalls: List<FunctionCallInfo>? = null,
    val createdAt: String? = null,
    val audioUrl: String? = null
)

/**
 * 工具调用信息
 */
data class FunctionCallInfo(
    val name: String,
    val arguments: String,
    val result: String? = null
)

/**
 * 历史消息响应包装类
 * 对应后端 ChatHistoryResponse，包含 messages 字段
 *
 * @property messages 消息列表
 */
data class ChatHistoryResponseDto(
    val messages: List<MessageDto>? = null
)
