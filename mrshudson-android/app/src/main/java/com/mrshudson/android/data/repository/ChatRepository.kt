package com.mrshudson.android.data.repository

import com.mrshudson.android.data.remote.ApiResult
import com.mrshudson.android.data.remote.dto.TtsRequest
import com.mrshudson.android.data.remote.dto.TtsResponse
import com.mrshudson.android.domain.model.Conversation
import com.mrshudson.android.domain.model.Message
import kotlinx.coroutines.flow.Flow

/**
 * 聊天仓库接口
 * 定义消息发送、历史查询、会话管理等数据操作
 */
interface ChatRepository {

    /**
     * 发送消息
     * 向 AI 发送消息并获取回复
     *
     * @param message 消息内容
     * @param conversationId 会话ID，可选，为空时创建新会话
     * @return 发送消息结果的 Flow，成功时返回 AI 回复消息和会话ID
     */
    fun sendMessage(message: String, conversationId: Long?): Flow<ApiResult<SendMessageResult>>

    /**
     * 获取会话历史消息
     * 获取指定会话的消息历史
     *
     * @param conversationId 会话ID
     * @param limit 限制返回消息数量，默认50
     * @return 消息列表结果的 Flow
     */
    fun getHistory(conversationId: Long, limit: Int = 50): Flow<ApiResult<List<Message>>>

    /**
     * 获取会话列表
     * 获取当前用户的所有会话
     *
     * @return 会话列表结果的 Flow
     */
    fun getConversations(): Flow<ApiResult<List<Conversation>>>

    /**
     * 创建新会话
     * 创建一个新的聊天会话
     *
     * @param title 会话标题
     * @return 新会话结果的 Flow
     */
    fun createConversation(title: String = "新对话"): Flow<ApiResult<Conversation>>

    /**
     * 删除会话
     * 删除指定的会话
     *
     * @param conversationId 要删除的会话ID
     * @return 删除结果
     */
    fun deleteConversation(conversationId: Long): Flow<ApiResult<Unit>>

    /**
     * 语音合成（TTS）
     * 将文本转换为语音
     *
     * @param request TTS请求
     * @return TTS响应，包含音频URL
     */
    suspend fun textToSpeech(request: TtsRequest): ApiResult<TtsResponse>

    /**
     * 流式发送消息
     * 通过 SSE 接收 AI 流式响应增量更新
     *
     * @param message 消息内容
     * @param conversationId 会话ID，可选，为空时创建新会话
     * @return 流式事件 Flow，每个事件包含增量内容或完成状态
     */
    fun streamMessage(message: String, conversationId: Long?): Flow<StreamEvent>
}

/**
 * 流式事件
 * 包含 SSE 流中的 JSON 事件数据
 * 遵循 SSE_TTS_UNIFIED_SPEC.md 规范
 */
sealed class StreamEvent {
    abstract val conversationId: Long?

    /**
     * 工具调用事件
     */
    data class ToolCall(
        override val conversationId: Long?,
        val id: String,
        val name: String,
        val arguments: String
    ) : StreamEvent()

    /**
     * 工具结果事件
     */
    data class ToolResult(
        override val conversationId: Long?,
        val id: String,
        val name: String,
        val result: String
    ) : StreamEvent()

    /**
     * 增量内容事件
     */
    data class Content(
        override val conversationId: Long?,
        val messageId: Long?,
        val text: String
    ) : StreamEvent()

    /**
     * AI 内容结束事件（TTS 开始后台合成）
     */
    data class ContentDone(
        override val conversationId: Long?,
        val messageId: Long?
    ) : StreamEvent()

    /**
     * TTS 语音合成结束事件
     */
    data class AudioDone(
        override val conversationId: Long?,
        val messageId: Long?,
        val url: String?,
        val timeout: Boolean,
        val error: String?,
        val noaudio: Boolean
    ) : StreamEvent()

    /**
     * Token 使用统计事件
     */
    data class TokenUsage(
        override val conversationId: Long?,
        val inputTokens: Int,
        val outputTokens: Int,
        val duration: Long,
        val model: String
    ) : StreamEvent()

    /**
     * 缓存命中事件
     */
    data class CacheHit(
        override val conversationId: Long?,
        val content: String
    ) : StreamEvent()

    /**
     * 澄清提示事件
     */
    data class Clarification(
        override val conversationId: Long?,
        val content: String
    ) : StreamEvent()

    /**
     * 音频URL事件（旧格式，保留向后兼容）
     * @deprecated 请使用 AudioDone
     */
    @Deprecated("Use AudioDone instead")
    data class AudioUrl(
        override val conversationId: Long?,
        val url: String
    ) : StreamEvent()

    /**
     * 流式完成事件
     */
    data class Done(
        override val conversationId: Long?
    ) : StreamEvent()

    /**
     * 错误事件
     */
    data class Error(
        override val conversationId: Long?,
        val message: String
    ) : StreamEvent()
}

/**
 * 发送消息结果数据类
 *
 * @property message AI 回复的消息
 * @property conversationId 会话ID（如果是新会话则返回新ID）
 */
data class SendMessageResult(
    val message: Message,
    val conversationId: Long?
)
