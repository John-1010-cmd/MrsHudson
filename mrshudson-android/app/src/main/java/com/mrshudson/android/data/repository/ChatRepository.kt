package com.mrshudson.android.data.repository

import com.mrshudson.android.data.remote.ApiResult
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
    fun sendMessage(message: String, conversationId: String?): Flow<ApiResult<SendMessageResult>>

    /**
     * 获取会话历史消息
     * 获取指定会话的消息历史
     *
     * @param conversationId 会话ID
     * @param limit 限制返回消息数量，默认50
     * @return 消息列表结果的 Flow
     */
    fun getHistory(conversationId: String, limit: Int = 50): Flow<ApiResult<List<Message>>>

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
    fun deleteConversation(conversationId: String): Flow<ApiResult<Unit>>
}

/**
 * 发送消息结果数据类
 *
 * @property message AI 回复的消息
 * @property conversationId 会话ID（如果是新会话则返回新ID）
 */
data class SendMessageResult(
    val message: Message,
    val conversationId: String?
)
