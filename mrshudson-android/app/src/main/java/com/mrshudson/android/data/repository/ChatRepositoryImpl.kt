package com.mrshudson.android.data.repository

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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 聊天仓库实现类
 * 使用 ChatApi 进行网络请求
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatApi: ChatApi,
    private val okHttpClient: OkHttpClient
) : ChatRepository, BaseApi {

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
     * 后端返回: id (String), role, content, createdAt, functionCall
     */
    private fun MessageDto.toDomainModel(): Message {
        return createMessage(
            id = id.toLong(),
            role = role,
            content = content,
            createdAt = createdAt,
            audioUrl = null // 历史消息没有 audioUrl
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

    override fun streamMessage(message: String, conversationId: Long?): Flow<StreamEvent> = callbackFlow {
        // 获取 baseUrl - 需要从 ChatApi 的 retrofit 实例获取
        val baseUrl = chatApi.let {
            // 通过反射获取 retrofit baseUrl（简化处理）
            val retrofitField = it::class.java.getDeclaredField("retrofit")
            retrofitField.isAccessible = true
            val retrofit = retrofitField.get(it) as retrofit2.Retrofit
            retrofit.baseUrl().toString()
        }

        // 构建 SSE 请求 URL
        // baseUrl like: http://10.0.2.2:8080/api/
        // streamUrl should be: http://10.0.2.2:8080/api/chat/stream
        val streamUrl = "${baseUrl.removeSuffix("/")}/chat/stream"

        android.util.Log.d("ChatRepository", "streamMessage URL: $streamUrl")

        // 构建请求体
        val requestBodyJson = com.google.gson.Gson().toJson(
            SendMessageRequest(message = message, conversationId = conversationId)
        )

        // 构建 HTTP 请求 - 使用 Java 兼容方式
        @Suppress("USELESS_CAST")
        val mediaType = okhttp3.MediaType::class.java.getMethod("get", String::class.java)
            .invoke(null, "application/json; charset=utf-8") as okhttp3.MediaType
        val requestBody = okhttp3.RequestBody::class.java.getMethod("create", okhttp3.MediaType::class.java, String::class.java)
            .invoke(null, mediaType, requestBodyJson) as okhttp3.RequestBody
        val request = Request.Builder()
            .url(streamUrl)
            .post(requestBody)
            .build()

        var closed = false

        // 创建 SSE EventSourceListener
        val eventSourceListener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                android.util.Log.d("ChatRepository", "SSE 连接打开")
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                android.util.Log.d("ChatRepository", "SSE 事件: $data")
                if (!closed) {
                    // 解析 JSON 格式的 SSE 事件
                    try {
                        val jsonElement = com.google.gson.JsonParser.parseString(data)
                        val obj = jsonElement.asJsonObject
                        val eventType = obj.get("type")?.asString ?: "content"

                        when (eventType) {
                            "content" -> {
                                val text = obj.get("text")?.asString ?: ""
                                trySend(StreamEvent.Content(text))
                            }
                            "token_usage" -> {
                                val inputTokens = obj.get("inputTokens")?.asInt ?: 0
                                val outputTokens = obj.get("outputTokens")?.asInt ?: 0
                                val duration = obj.get("duration")?.asLong ?: 0L
                                val model = obj.get("model")?.asString ?: "unknown"
                                trySend(StreamEvent.TokenUsage(inputTokens, outputTokens, duration, model))
                            }
                            "tool_call" -> {
                                // 工具调用事件 {"type":"tool_call","toolCall":{"name":"...","arguments":"..."}}
                                val toolCallObj = obj.getAsJsonObject("toolCall")
                                if (toolCallObj != null) {
                                    val name = toolCallObj.get("name")?.asString ?: ""
                                    val arguments = toolCallObj.get("arguments")?.asString ?: ""
                                    trySend(StreamEvent.ToolCall(id = "", name = name, arguments = arguments))
                                }
                            }
                            "tool_result" -> {
                                // 工具结果事件 {"type":"tool_result","toolResult":{"name":"...","result":"..."}}
                                val toolResultObj = obj.getAsJsonObject("toolResult")
                                if (toolResultObj != null) {
                                    val name = toolResultObj.get("name")?.asString ?: ""
                                    val result = toolResultObj.get("result")?.asString ?: ""
                                    trySend(StreamEvent.ToolResult(id = "", name = name, result = result))
                                }
                            }
                            "cache_hit" -> {
                                val content = obj.get("content")?.asString ?: ""
                                trySend(StreamEvent.CacheHit(content))
                            }
                            "clarification" -> {
                                val content = obj.get("content")?.asString ?: ""
                                trySend(StreamEvent.Clarification(content))
                            }
                            "audio_url" -> {
                                val url = obj.get("url")?.asString ?: ""
                                trySend(StreamEvent.AudioUrl(url))
                            }
                            "done" -> {
                                closed = true
                                trySend(StreamEvent.Done(conversationId))
                                close()
                            }
                            else -> {
                                // 未知类型，当作内容处理
                                trySend(StreamEvent.Content(data))
                            }
                        }
                    } catch (e: Exception) {
                        // JSON 解析失败，当作纯文本内容处理
                        android.util.Log.w("ChatRepository", "JSON 解析失败: ${e.message}")
                        trySend(StreamEvent.Content(data))
                    }
                }
            }

            override fun onClosed(eventSource: EventSource) {
                android.util.Log.d("ChatRepository", "SSE 连接关闭")
                if (!closed) {
                    closed = true
                    trySend(StreamEvent.Done(conversationId))
                    close()
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val errorMsg = when {
                    t != null -> t.message ?: "SSE 连接失败"
                    response != null -> "SSE 错误: ${response.code} ${response.message}"
                    else -> "SSE 连接失败"
                }
                android.util.Log.e("ChatRepository", "SSE 失败: $errorMsg")
                if (!closed) {
                    closed = true
                    trySend(StreamEvent.Error(errorMsg))
                    close()
                }
            }
        }

        // 创建 SSE 连接
        val factory = EventSources.createFactory(okHttpClient)
        val eventSource = factory.newEventSource(request, eventSourceListener)

        awaitClose {
            android.util.Log.d("ChatRepository", "SSE 清理资源")
            if (!closed) {
                closed = true
                eventSource.cancel()
            }
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
