package com.mrshudson.android.data.repository

import com.mrshudson.android.data.remote.ApiResult
import com.mrshudson.android.data.remote.BaseApi
import com.mrshudson.android.data.remote.ChatApi
import com.mrshudson.android.data.remote.dto.ConversationDto
import com.mrshudson.android.data.remote.dto.CreateConversationRequest
import com.mrshudson.android.data.remote.dto.MessageDto
import com.mrshudson.android.data.remote.dto.SendMessageRequest
import com.mrshudson.android.data.remote.dto.SendMessageResponse
import com.mrshudson.android.domain.model.Conversation
import com.mrshudson.android.domain.model.Message
import com.mrshudson.android.domain.model.createConversation
import com.mrshudson.android.domain.model.createMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 聊天仓库实现类
 * 使用 ChatApi 进行网络请求
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatApi: ChatApi
) : ChatRepository, BaseApi {

    override fun sendMessage(message: String, conversationId: String?): Flow<ApiResult<SendMessageResult>> = flow {
        emit(ApiResult.Loading)
        try {
            val request = SendMessageRequest(
                message = message,
                conversationId = conversationId
            )
            val response = chatApi.sendMessage(request)
            val result = handleResultResponse(response)

            when (result) {
                is ApiResult.Success -> {
                    val sendMessageResponse = result.data
                    val msg = sendMessageResponse.toDomainModel()
                    val sendResult = SendMessageResult(
                        message = msg,
                        conversationId = sendMessageResponse.conversationId
                    )
                    emit(ApiResult.Success(sendResult))
                }
                is ApiResult.Error -> {
                    emit(ApiResult.Error(result.code, result.message))
                }
                is ApiResult.Loading -> { /* ignore */ }
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(-1, e.message ?: "发送消息失败，请检查网络连接"))
        }
    }

    override fun getHistory(conversationId: String, limit: Int): Flow<ApiResult<List<Message>>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = chatApi.getHistory(conversationId, limit)
            val result = handleResultResponse(response)

            when (result) {
                is ApiResult.Success -> {
                    val messageDtos = result.data
                    val messages = messageDtos.map { it.toDomainModel() }
                    emit(ApiResult.Success(messages))
                }
                is ApiResult.Error -> {
                    emit(ApiResult.Error(result.code, result.message))
                }
                is ApiResult.Loading -> { /* ignore */ }
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(-1, e.message ?: "获取历史消息失败，请检查网络连接"))
        }
    }

    override fun getConversations(): Flow<ApiResult<List<Conversation>>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = chatApi.getConversations()
            val result = handleResultResponse(response)

            when (result) {
                is ApiResult.Success -> {
                    val conversationDtos = result.data
                    val conversations = conversationDtos.map { it.toDomainModel() }
                    emit(ApiResult.Success(conversations))
                }
                is ApiResult.Error -> {
                    emit(ApiResult.Error(result.code, result.message))
                }
                is ApiResult.Loading -> { /* ignore */ }
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(-1, e.message ?: "获取会话列表失败，请检查网络连接"))
        }
    }

    override fun createConversation(title: String): Flow<ApiResult<Conversation>> = flow {
        emit(ApiResult.Loading)
        try {
            val request = CreateConversationRequest(title)
            val response = chatApi.createConversation(request)
            val result = handleResultResponse(response)

            when (result) {
                is ApiResult.Success -> {
                    val createResponse = result.data
                    val conversation = Conversation(
                        id = createResponse.id,
                        title = createResponse.title
                    )
                    emit(ApiResult.Success(conversation))
                }
                is ApiResult.Error -> {
                    emit(ApiResult.Error(result.code, result.message))
                }
                is ApiResult.Loading -> { /* ignore */ }
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(-1, e.message ?: "创建会话失败，请检查网络连接"))
        }
    }

    override fun deleteConversation(conversationId: String): Flow<ApiResult<Unit>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = chatApi.deleteConversation(conversationId)
            val result = handleResultResponse(response)
            when (result) {
                is ApiResult.Success -> emit(ApiResult.Success(Unit))
                is ApiResult.Error -> emit(ApiResult.Error(result.code, result.message))
                is ApiResult.Loading -> emit(ApiResult.Loading)
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(-1, e.message ?: "删除会话失败，请检查网络连接"))
        }
    }

    /**
     * 将 SendMessageResponse 转换为领域模型 Message
     */
    private fun SendMessageResponse.toDomainModel(): Message {
        return createMessage(
            id = id,
            role = role,
            content = content,
            createdAt = createdAt
        )
    }

    /**
     * 将 MessageDto 转换为领域模型 Message
     */
    private fun MessageDto.toDomainModel(): Message {
        return createMessage(
            id = id,
            role = role,
            content = content,
            createdAt = createdAt
        )
    }

    /**
     * 将 ConversationDto 转换为领域模型 Conversation
     */
    private fun ConversationDto.toDomainModel(): Conversation {
        return createConversation(
            id = id,
            title = title,
            provider = provider,
            lastMessageAt = lastMessageAt,
            createdAt = createdAt
        )
    }
}
