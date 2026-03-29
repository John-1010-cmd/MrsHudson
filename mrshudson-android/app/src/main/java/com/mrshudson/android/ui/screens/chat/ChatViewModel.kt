package com.mrshudson.android.ui.screens.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrshudson.android.data.local.datastore.SettingsDataStore
import com.mrshudson.android.data.remote.ApiResult
import com.mrshudson.android.data.repository.ChatRepository
import com.mrshudson.android.data.repository.SendMessageResult
import com.mrshudson.android.data.repository.StreamEvent
import com.mrshudson.android.domain.model.Conversation
import com.mrshudson.android.domain.model.Message
import com.mrshudson.android.domain.model.MessageRole
import com.mrshudson.android.domain.model.TtsStatus
import com.mrshudson.android.ui.components.chat.AudioPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.time.LocalDateTime
import javax.inject.Inject

private const val TAG = "ChatViewModel"

/**
 * AI 对话页面的 UI 状态
 */
data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val conversations: List<Conversation> = emptyList(),
    val currentConversationId: Long? = null,
    val currentConversationTitle: String = "新对话",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,
    val lastTokenUsage: TokenUsageInfo? = null
)

/**
 * Token 使用统计信息
 */
data class TokenUsageInfo(
    val inputTokens: Int,
    val outputTokens: Int,
    val duration: Long,
    val model: String
) {
    fun toDisplayString(): String {
        return buildString {
            append("\n\n--- 💡 本次对话消耗 ---\n")
            append("📥 输入: $inputTokens tokens\n")
            append("📤 输出: $outputTokens tokens\n")
            append("⏱️ 耗时: ${duration}ms\n")
            append("🤖 模型: $model\n")
            append("------------------------")
        }
    }
}

/**
 * AI 对话页面的 ViewModel
 * 管理对话状态、处理用户交互、TTS播放
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val settingsDataStore: SettingsDataStore,
    private val okHttpClient: OkHttpClient,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // 音频播放器
    val audioPlayer: AudioPlayer by lazy {
        AudioPlayer(application.applicationContext, chatRepository, settingsDataStore, okHttpClient)
    }

    // 跟踪加载会话列表的 coroutine job，用于取消之前的任务
    private var loadConversationsJob: Job? = null

    // 跟踪流式消息的 coroutine job，用于切换会话时取消
    private var streamJob: Job? = null

    // 最近一次发送失败的内容，用于重试
    private var lastFailedContent: String? = null

    init {
        // 初始化时加载会话列表
        loadConversations()
    }

    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
        audioPlayer.release()
    }

    /**
     * 加载会话列表
     * 取消之前未完成的加载任务，避免重复收集导致的状态混乱
     */
    fun loadConversations() {
        // 取消之前的加载任务
        loadConversationsJob?.cancel()
        loadConversationsJob = viewModelScope.launch {
            chatRepository.getConversations().collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                    is ApiResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                conversations = result.data
                            )
                        }
                    }
                    is ApiResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = result.message
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 选择会话
     * 加载指定会话的消息历史
     *
     * @param conversationId 会话ID
     */
    fun selectConversation(conversationId: Long) {
        // 切换会话时取消当前流式任务
        streamJob?.cancel()
        streamJob = null

        val conversation = _uiState.value.conversations.find { it.id == conversationId }
        _uiState.update {
            it.copy(
                currentConversationId = conversationId,
                currentConversationTitle = conversation?.title ?: "新对话",
                isSending = false
            )
        }
        loadHistory(conversationId)
    }

    /**
     * 加载消息历史
     *
     * @param conversationId 会话ID
     */
    private fun loadHistory(conversationId: Long) {
        viewModelScope.launch {
            chatRepository.getHistory(conversationId).collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                    is ApiResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                messages = result.data
                            )
                        }
                    }
                    is ApiResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = result.message
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 发送消息
     *
     * @param content 消息内容
     */
    fun sendMessage(content: String) {
        if (content.isBlank()) return

        viewModelScope.launch {
            // 先添加用户消息到列表（乐观更新）
            val userMessage = Message(
                id = System.currentTimeMillis(),
                role = MessageRole.USER,
                content = content,
                createdAt = LocalDateTime.now()
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + userMessage,
                    isSending = true
                )
            }

            // 发送消息到服务器
            chatRepository.sendMessage(
                message = content,
                conversationId = _uiState.value.currentConversationId
            ).collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        // 已经在上面设置了 Loading 状态
                    }
                    is ApiResult.Success -> {
                        val sendResult = result.data

                        // 如果是新会话，更新当前会话ID
                        if (_uiState.value.currentConversationId == null && sendResult.conversationId != null) {
                            _uiState.update {
                                it.copy(currentConversationId = sendResult.conversationId)
                            }
                            // 刷新会话列表
                            loadConversations()
                        }

                        // 添加 AI 回复消息
                        _uiState.update {
                            it.copy(
                                messages = it.messages + sendResult.message,
                                isSending = false
                            )
                        }
                    }
                    is ApiResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isSending = false,
                                error = result.message
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 发送消息（流式模式）
     * 使用 SSE 流式接收 AI 响应增量更新
     *
     * @param content 消息内容
     */
    fun sendMessageStream(content: String) {
        if (content.isBlank()) return

        // 取消之前的流式任务
        streamJob?.cancel()
        lastFailedContent = content  // 记录本次发送内容，用于重试
        streamJob = viewModelScope.launch {
            // 先添加用户消息到列表（乐观更新）
            val userMessage = Message(
                id = System.currentTimeMillis(),
                role = MessageRole.USER,
                content = content,
                createdAt = LocalDateTime.now()
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + userMessage,
                    isSending = true
                )
            }

            // 创建一个占位的 AI 消息用于增量更新
            val localAiMessageId = System.currentTimeMillis() + 1
            var backendMessageId: Long? = null  // 后端返回的真实 messageId
            var currentAiContent = StringBuilder()
            var currentThinkingContent: String? = null  // 思考过程增量

            // 添加空的 AI 消息占位符（流式接收时 thinking 默认展开）
            _uiState.update { state ->
                state.copy(
                    messages = state.messages + Message(
                        id = localAiMessageId,
                        role = MessageRole.ASSISTANT,
                        content = "",
                        thinkingContent = null,
                        isThinkingExpanded = true,  // 流式接收时默认展开
                        createdAt = LocalDateTime.now()
                    )
                )
            }

            // 辅助函数：获取当前消息ID（优先使用后端 messageId）
            fun getCurrentMessageId(): Long = backendMessageId ?: localAiMessageId

            // 使用流式 API 发送消息
            chatRepository.streamMessage(
                message = content,
                conversationId = _uiState.value.currentConversationId
            ).collect { event ->
                when (event) {
                    is StreamEvent.Thinking -> {
                        // 规范 14.3：思考过程调试日志
                        Log.d(TAG, "SSE thinking: ${event.text.take(50)}")
                        // 思考过程增量事件
                        // 首次收到后端 messageId 时更新
                        if (backendMessageId == null && event.messageId != null) {
                            backendMessageId = event.messageId
                        }
                        currentThinkingContent = (currentThinkingContent ?: "") + event.text
                        val targetId = getCurrentMessageId()
                        _uiState.update { state ->
                            val updatedMessages = state.messages.map { msg ->
                                if (msg.id == targetId) {
                                    msg.copy(thinkingContent = currentThinkingContent)
                                } else {
                                    msg
                                }
                            }
                            state.copy(messages = updatedMessages)
                        }
                    }
                    is StreamEvent.Content -> {
                        // 规范 14.3：内容调试日志
                        Log.d(TAG, "SSE content: ${event.text}")
                        // 首次收到后端 messageId 时更新
                        // 首次收到后端 messageId 时更新
                        if (backendMessageId == null && event.messageId != null) {
                            backendMessageId = event.messageId
                        }
                        currentAiContent.append(event.text)
                        // 增量更新 AI 消息，同时设置 ttsStatus = PENDING
                        val targetId = getCurrentMessageId()
                        _uiState.update { state ->
                            val updatedMessages = state.messages.map { msg ->
                                if (msg.id == targetId) {
                                    msg.copy(content = currentAiContent.toString(), ttsStatus = TtsStatus.PENDING)
                                } else {
                                    msg
                                }
                            }
                            state.copy(messages = updatedMessages)
                        }
                    }
                    is StreamEvent.ContentDone -> {
                        // 规范 14.3：content_done 调试日志
                        Log.d(TAG, "content_done 收到，convId=${event.conversationId}, msgId=${event.messageId}")
                        // 首次收到后端 messageId 时更新
                        if (backendMessageId == null && event.messageId != null) {
                            backendMessageId = event.messageId
                        }
                        // AI 内容结束，TTS 开始后台合成
                        val targetId = getCurrentMessageId()
                        _uiState.update { state ->
                            val updatedMessages = state.messages.map { msg ->
                                if (msg.id == targetId) msg.copy(ttsStatus = TtsStatus.SYNTHESIZING) else msg
                            }
                            state.copy(messages = updatedMessages)
                        }
                    }
                    is StreamEvent.AudioDone -> {
                        // 规范 14.3：audio_done 调试日志
                        Log.d(TAG, "audio_done: url=${event.url}, timeout=${event.timeout}, error=${event.error}, noaudio=${event.noaudio}")
                        // 首次收到后端 messageId 时更新
                        if (backendMessageId == null && event.messageId != null) {
                            backendMessageId = event.messageId
                        }
                        val targetId = getCurrentMessageId()
                        _uiState.update { state ->
                            val updatedMessages = state.messages.map { msg ->
                                if (msg.id == targetId) {
                                    msg.copy(
                                        audioUrl = event.url,
                                        ttsStatus = when {
                                            event.timeout -> TtsStatus.TIMEOUT
                                            event.error != null -> TtsStatus.ERROR
                                            event.noaudio -> TtsStatus.NOAUDIO
                                            event.url != null -> TtsStatus.READY
                                            else -> TtsStatus.ERROR
                                        }
                                    )
                                } else msg
                            }
                            state.copy(messages = updatedMessages)
                        }
                    }
                    is StreamEvent.TokenUsage -> {
                        // Token 统计，存储并显示
                        val tokenUsage = TokenUsageInfo(
                            inputTokens = event.inputTokens,
                            outputTokens = event.outputTokens,
                            duration = event.duration,
                            model = event.model
                        )
                        // 更新消息末尾的 Token 统计
                        val statsDisplay = tokenUsage.toDisplayString()
                        currentAiContent.append(statsDisplay)
                        val targetId = getCurrentMessageId()
                        _uiState.update { state ->
                            val updatedMessages = state.messages.map { msg ->
                                if (msg.id == targetId) {
                                    msg.copy(content = currentAiContent.toString())
                                } else {
                                    msg
                                }
                            }
                            state.copy(messages = updatedMessages, lastTokenUsage = tokenUsage)
                        }
                    }
                    is StreamEvent.CacheHit -> {
                        // 首次收到后端 messageId 时更新
                        if (backendMessageId == null && event.messageId != null) {
                            backendMessageId = event.messageId
                        }
                        // 缓存命中，直接显示缓存内容
                        currentAiContent.append(event.content)
                        val targetId = getCurrentMessageId()
                        _uiState.update { state ->
                            val updatedMessages = state.messages.map { msg ->
                                if (msg.id == targetId) {
                                    msg.copy(content = currentAiContent.toString())
                                } else {
                                    msg
                                }
                            }
                            state.copy(messages = updatedMessages)
                        }
                    }
                    is StreamEvent.Clarification -> {
                        // 首次收到后端 messageId 时更新
                        if (backendMessageId == null && event.messageId != null) {
                            backendMessageId = event.messageId
                        }
                        // 澄清提示，直接显示
                        currentAiContent.append(event.content)
                        val targetId = getCurrentMessageId()
                        _uiState.update { state ->
                            val updatedMessages = state.messages.map { msg ->
                                if (msg.id == targetId) {
                                    msg.copy(content = currentAiContent.toString())
                                } else {
                                    msg
                                }
                            }
                            state.copy(messages = updatedMessages)
                        }
                    }
                    is StreamEvent.Done -> {
                        // 规范 14.3：done 调试日志
                        Log.d(TAG, "SSE done")
                        // 流式完成
                        val newConversationId = event.conversationId
                        if (_uiState.value.currentConversationId == null && newConversationId != null) {
                            _uiState.update {
                                it.copy(currentConversationId = newConversationId)
                            }
                            // 刷新会话列表
                            loadConversations()
                        }
                        _uiState.update { it.copy(isSending = false) }
                    }
                    is StreamEvent.Error -> {
                        // lastFailedContent 已在 sendMessageStream 开头设置
                        _uiState.update {
                            it.copy(
                                isSending = false,
                                error = event.message
                            )
                        }
                    }
                    is StreamEvent.ToolCall -> {
                        // 工具调用事件，记录但不直接显示
                        Log.d(TAG, "Tool call: ${event.name}")
                    }
                    is StreamEvent.ToolResult -> {
                        // 工具结果事件，记录但不直接显示
                        Log.d(TAG, "Tool result: ${event.name}")
                    }
                }
            }
        }
    }

    /**
     * 创建新会话
     */
    fun createNewConversation() {
        viewModelScope.launch {
            chatRepository.createConversation().collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                    is ApiResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                currentConversationId = result.data.id,
                                currentConversationTitle = result.data.title,
                                messages = emptyList()
                            )
                        }
                        // 刷新会话列表
                        loadConversations()
                    }
                    is ApiResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = result.message
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 删除会话
     *
     * @param conversationId 要删除的会话ID
     */
    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            chatRepository.deleteConversation(conversationId).collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                    is ApiResult.Success -> {
                        // 如果删除的是当前会话，清空当前会话
                        if (_uiState.value.currentConversationId == conversationId) {
                            _uiState.update {
                                it.copy(
                                    currentConversationId = null,
                                    currentConversationTitle = "新对话",
                                    messages = emptyList()
                                )
                            }
                        }
                        // 刷新会话列表
                        loadConversations()
                    }
                    is ApiResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = result.message
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * 重试上次失败的消息（规范 12.1 节 error 状态 + 重试按钮）
     */
    fun retryLastMessage() {
        val content = lastFailedContent
        if (content.isNullOrBlank()) return
        lastFailedContent = null
        clearError()
        // 移除最后的 AI 占位消息（如果有）
        _uiState.update { state ->
            val messages = state.messages.dropLastWhile { it.role == MessageRole.ASSISTANT && it.content.isBlank() }
            state.copy(messages = messages)
        }
        sendMessageStream(content)
    }

    /**
     * 更新消息的音频播放状态
     *
     * @param updatedMessage 更新后的消息
     */
    fun updateMessageAudioState(updatedMessage: Message) {
        _uiState.update { state ->
            val updatedMessages = state.messages.map { msg ->
                if (msg.id == updatedMessage.id) {
                    updatedMessage
                } else {
                    // 停止其他消息的播放状态
                    if (msg.audioPlayState == com.mrshudson.android.domain.model.AudioPlayState.PLAYING) {
                        msg.copy(audioPlayState = com.mrshudson.android.domain.model.AudioPlayState.IDLE)
                    } else {
                        msg
                    }
                }
            }
            state.copy(messages = updatedMessages)
        }
    }

    /**
     * 切换思考过程的展开/折叠状态
     *
     * @param messageId 消息ID
     */
    fun toggleThinking(messageId: Long) {
        _uiState.update { state ->
            val updatedMessages = state.messages.map { msg ->
                if (msg.id == messageId) {
                    msg.copy(isThinkingExpanded = !msg.isThinkingExpanded)
                } else {
                    msg
                }
            }
            state.copy(messages = updatedMessages)
        }
    }
}
