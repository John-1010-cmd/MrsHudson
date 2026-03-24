package com.mrshudson.android.data.repository

import com.google.gson.Gson
import com.mrshudson.android.data.remote.ApiResult
import com.mrshudson.android.data.remote.BaseApi
import com.mrshudson.android.data.remote.ChatApi
import com.mrshudson.android.data.remote.dto.ConversationDto
import com.mrshudson.android.data.remote.dto.ConversationListResponseDto
import com.mrshudson.android.data.remote.dto.CreateConversationRequest
import com.mrshudson.android.data.remote.dto.MessageDto
import com.mrshudson.android.data.remote.dto.SendMessageRequest
import com.mrshudson.android.data.remote.dto.SendMessageResponse
import com.mrshudson.android.data.remote.dto.TtsRequest
import com.mrshudson.android.data.remote.dto.TtsResponse
import com.mrshudson.android.domain.model.Conversation
import com.mrshudson.android.domain.model.Message
import com.mrshudson.android.domain.model.createConversation
import com.mrshudson.android.domain.model.createMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 聊天仓库实现类
 * 使用 ChatApi 进行网络请求
 *
 * SSE 流式请求使用 SseClient 统一处理
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatApi: ChatApi,
    private val okHttpClient: OkHttpClient
) : ChatRepository, BaseApi {

    /**
     * 获取 API Base URL
     * 用于 SseClient 构建流式请求 URL
     */
    private fun getBaseUrl(): String {
        return chatApi.let {
            val retrofitField = it::class.java.getDeclaredField("retrofit")
            retrofitField.isAccessible = true
            val retrofit = retrofitField.get(it) as retrofit2.Retrofit
            retrofit.baseUrl().toString()
        }
    }

    override fun sendMessage(message: String, conversationId: Long?): Flow<ApiResult<SendMessageResult>> = flow {
        emit(ApiResult.Loading)
        try {
            val request = SendMessageRequest(
                message = message,
                conversationId = conversationId
            )
            val response = chatApi.sendMessage(request)
            android.util.Log.d("ChatRepository", "sendMessage response: ${response.code()}, body: ${response.body()}")
            val result = handleResultResponse(response)

            when (result) {
                is ApiResult.Success -> {
                    val sendMessageResponse = result.data
                    android.util.Log.d("ChatRepository", "sendMessageResponse: $sendMessageResponse")
                    val msg = sendMessageResponse.toDomainModel()
                    android.util.Log.d("ChatRepository", "converted message: $msg")
                    // 后端不返回 conversationId，使用请求中的 conversationId
                    val sendResult = SendMessageResult(
                        message = msg,
                        conversationId = conversationId
                    )
                    emit(ApiResult.Success(sendResult))
                }
                is ApiResult.Error -> {
                    android.util.Log.e("ChatRepository", "sendMessage error: ${result.code}, ${result.message}")
                    emit(ApiResult.Error(result.code, result.message))
                }
                is ApiResult.Loading -> { /* ignore */ }
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "sendMessage exception", e)
            emit(ApiResult.Error(-1, e.message ?: "发送消息失败，请检查网络连接"))
        }
    }

    override fun getHistory(conversationId: Long, limit: Int): Flow<ApiResult<List<Message>>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = chatApi.getHistory(conversationId, limit)
            val result = handleResultResponse(response)

            when (result) {
                is ApiResult.Success -> {
                    val historyResponse = result.data
                    val messageDtos = historyResponse.messages ?: emptyList()
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
                    val listResponse = result.data
                    val conversationDtos = listResponse.conversations ?: emptyList()
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

    override fun deleteConversation(conversationId: Long): Flow<ApiResult<Unit>> = flow {
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
     * 后端返回: messageId, content, functionCalls, createdAt, audioUrl
     */
    private fun SendMessageResponse.toDomainModel(): Message {
        // 解析 messageId，支持普通数字和 cache_ 前缀格式
        val parsedId = try {
            if (messageId.startsWith("cache_")) {
                messageId.removePrefix("cache_").toLong()
            } else {
                messageId.toLong()
            }
        } catch (e: NumberFormatException) {
            System.currentTimeMillis() // 解析失败使用时间戳
        }

        return createMessage(
            id = parsedId,
            role = "assistant", // 发送消息返回的都是 AI 回复
            content = content,
            createdAt = createdAt,
            audioUrl = audioUrl
        )
    }

    /**
     * 将 MessageDto 转换为领域模型 Message
     * 后端返回: id (String), role, content, createdAt, functionCall, audioUrl
     */
    private fun MessageDto.toDomainModel(): Message {
        return createMessage(
            id = id.toLong(),
            role = role,
            content = content,
            createdAt = createdAt,
            audioUrl = audioUrl
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

    override fun streamMessage(message: String, conversationId: Long?): Flow<StreamEvent> {
        // 使用 SseClient 统一处理 SSE 流
        val sseClient = SseClient(okHttpClient, getBaseUrl())
        val requestBody = Gson().toJson(
            SendMessageRequest(message = message, conversationId = conversationId)
        )

        android.util.Log.d("ChatRepository", "streamMessage 使用 SseClient")

        return sseClient.stream(
            endpoint = "chat/stream",
            requestBody = requestBody
        )
    }

    override suspend fun textToSpeech(request: TtsRequest): ApiResult<TtsResponse> {
        return try {
            val response = chatApi.textToSpeech(request)
            val result = handleResultResponse(response)

            when (result) {
                is ApiResult.Success -> {
                    ApiResult.Success(result.data)
                }
                is ApiResult.Error -> {
                    ApiResult.Error(result.code, result.message)
                }
                is ApiResult.Loading -> {
                    ApiResult.Error(-1, "Unexpected loading state")
                }
            }
        } catch (e: Exception) {
            ApiResult.Error(-1, e.message ?: "语音合成失败")
        }
    }
}
