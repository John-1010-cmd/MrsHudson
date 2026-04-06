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
import com.mrshudson.android.domain.model.TtsStatus
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
    private val okHttpClient: OkHttpClient,
    @javax.inject.Named("sse") private val sseOkHttpClient: OkHttpClient,
    private val baseUrl: String
) : ChatRepository, BaseApi {

    /**
     * 获取 API Base URL
     * 用于 SseClient 构建流式请求 URL
     */
    private fun getBaseUrl(): String = baseUrl

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
            android.util.Log.d("ChatRepository", "开始获取历史消息: conversationId=$conversationId, limit=$limit")
            val response = chatApi.getHistory(conversationId, limit)
            android.util.Log.d("ChatRepository", "获取历史消息响应: ${response.code()}")
            
            val result = handleResultResponse(response)

            when (result) {
                is ApiResult.Success -> {
                    val historyResponse = result.data
                    android.util.Log.d("ChatRepository", "历史消息响应数据: $historyResponse")
                    
                    val messageDtos = historyResponse.messages ?: emptyList()
                    android.util.Log.d("ChatRepository", "消息DTO列表大小: ${messageDtos.size}")
                    
                    val messages = messageDtos.mapIndexed { index, dto ->
                        try {
                            dto.toDomainModel()
                        } catch (e: Exception) {
                            android.util.Log.e("ChatRepository", "转换第 $index 条消息失败: $dto", e)
                            // 返回一个默认消息，避免整个列表加载失败
                            createMessage(
                                id = index.toLong(),
                                role = "assistant",
                                content = "[消息加载失败]",
                                createdAt = System.currentTimeMillis().toString()
                            )
                        }
                    }
                    android.util.Log.d("ChatRepository", "成功转换 ${messages.size} 条消息")
                    emit(ApiResult.Success(messages))
                }
                is ApiResult.Error -> {
                    android.util.Log.e("ChatRepository", "获取历史消息失败: ${result.code}, ${result.message}")
                    emit(ApiResult.Error(result.code, result.message))
                }
                is ApiResult.Loading -> { /* ignore */ }
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "获取历史消息异常", e)
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
            val msgId = messageId ?: System.currentTimeMillis().toString()
            if (msgId.startsWith("cache_")) {
                msgId.removePrefix("cache_").toLong()
            } else {
                msgId.toLong()
            }
        } catch (e: NumberFormatException) {
            System.currentTimeMillis() // 解析失败使用时间戳
        }

        return createMessage(
            id = parsedId,
            role = "assistant", // 发送消息返回的都是 AI 回复
            content = content ?: "",
            createdAt = createdAt ?: System.currentTimeMillis().toString(),
            audioUrl = audioUrl
        )
    }

    /**
     * 将 MessageDto 转换为领域模型 Message
     * 后端返回: id (String), role, content, createdAt, functionCall, audioUrl, thinkingContent
     */
    private fun MessageDto.toDomainModel(): Message {
        // 修复 localhost URL
        val fixedAudioUrl = audioUrl?.let { fixLocalhostUrl(it) }
        
        // 安全解析 messageId，支持普通数字和 cache_ 前缀格式
        val parsedId = try {
            val idValue = id ?: System.currentTimeMillis().toString()
            if (idValue.startsWith("cache_")) {
                idValue.removePrefix("cache_").toLong()
            } else {
                idValue.toLong()
            }
        } catch (e: NumberFormatException) {
            android.util.Log.e("ChatRepository", "无法解析消息ID: $id，使用时间戳代替")
            System.currentTimeMillis()
        }
        
        val msg = createMessage(
            id = parsedId,
            role = role ?: "assistant", // 默认角色
            content = content ?: "", // 空内容保护
            createdAt = createdAt ?: System.currentTimeMillis().toString(), // 默认当前时间
            audioUrl = fixedAudioUrl,
            thinkingContent = thinkingContent
        )
        // 历史消息：根据 audioUrl 设置 ttsStatus
        return msg.copy(
            ttsStatus = if (!fixedAudioUrl.isNullOrBlank()) TtsStatus.READY else TtsStatus.NO_AUDIO
        )
    }
    
    /**
     * 修复 localhost URL：将 localhost:port/127.0.0.1:port 替换为实际服务器地址
     * 正确处理端口号，避免重复端口问题
     */
    private fun fixLocalhostUrl(url: String): String {
        val baseUrl = getBaseUrl()
        // 从 baseUrl 提取服务器主机（包括端口），去掉 /api/ 等路径
        // e.g., "http://10.0.2.2:8080/api/" -> "10.0.2.2:8080"
        val serverHost = baseUrl.replace(Regex("https?://"), "")
            .replace(Regex("/.*$"), "")
        
        android.util.Log.d("ChatRepository", "fixLocalhostUrl: 原始URL=$url, baseUrl=$baseUrl, serverHost=$serverHost")
        
        val result = when {
            url.contains("localhost") -> {
                // 替换 "localhost:port" 或 "localhost" 为实际服务器地址
                url.replace(Regex("localhost(:\\d+)?"), serverHost)
            }
            url.contains("127.0.0.1") -> {
                // 替换 "127.0.0.1:port" 或 "127.0.0.1" 为实际服务器地址
                url.replace(Regex("127\\.0\\.0\\.1(:\\d+)?"), serverHost)
            }
            else -> url
        }
        
        android.util.Log.d("ChatRepository", "fixLocalhostUrl: 结果URL=$result")
        return result
    }

    /**
     * 将 ConversationDto 转换为领域模型 Conversation
     */
    private fun ConversationDto.toDomainModel(): Conversation {
        return createConversation(
            id = id ?: 0L,
            title = title ?: "新对话",
            provider = provider,
            lastMessageAt = lastMessageAt,
            createdAt = createdAt
        )
    }

    override fun streamMessage(message: String, conversationId: Long?): Flow<StreamEvent> = flow {
        try {
            val baseUrl = getBaseUrl()
            android.util.Log.d("ChatRepository", "SSE baseUrl: $baseUrl")

            // 使用 SseClient 统一处理 SSE 流（使用 SSE 专用的 OkHttpClient）
            val sseClient = SseClient(sseOkHttpClient, baseUrl)
            val requestBody = Gson().toJson(
                SendMessageRequest(message = message, conversationId = conversationId)
            )

            android.util.Log.d("ChatRepository", "streamMessage 请求体: $requestBody")

            sseClient.stream(
                endpoint = "chat/stream",
                requestBody = requestBody
            ).collect { event ->
                emit(event)
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "streamMessage 异常", e)
            emit(StreamEvent.Error(conversationId, "发送消息失败: ${e.message}"))
        }
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
