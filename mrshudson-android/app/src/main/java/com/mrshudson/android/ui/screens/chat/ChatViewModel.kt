package com.mrshudson.android.ui.screens.chat

import android.app.Application
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

    init {
        // 初始化时加载会话列表
        loadConversations()
    }

    override fun onCleared() {
        super.onCleared()
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
        val conversation = _uiState.value.conversations.find { it.id == conversationId }
        _uiState.update {
            it.copy(
                currentConversationId = conversationId,
                currentConversationTitle = conversation?.title ?: "新对话"
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

            // 创建一个占位的 AI 消息用于增量更新
            val aiMessageId = System.currentTimeMillis() + 1
            var currentAiContent = StringBuilder()

            // 添加空的 AI 消息占位符
            _uiState.update { state ->
                state.copy(
                    messages = state.messages + Message(
                        id = aiMessageId,
                        role = MessageRole.ASSISTANT,
                        content = "",
                        createdAt = LocalDateTime.now()
                    )
                )
            }

            // 使用流式 API 发送消息
            chatRepository.streamMessage(
                message = content,
                conversationId = _uiState.value.currentConversationId
            ).collect { event ->
                when (event) {
                    is StreamEvent.Content -> {
                        currentAiContent.append(event.text)
                        // 增量更新 AI 消息
                        _uiState.update { state ->
                            val updatedMessages = state.messages.map { msg ->
                                if (msg.id == aiMessageId) {
                                    msg.copy(content = currentAiContent.toString())
                                } else {
                                    msg
                                }
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
                        _uiState.update { state ->
                            val updatedMessages = state.messages.map { msg ->
                                if (msg.id == aiMessageId) {
                                    msg.copy(content = currentAiContent.toString())
                                } else {
                                    msg
                                }
                            }
                            state.copy(messages = updatedMessages, lastTokenUsage = tokenUsage)
                        }
                    }
                    is StreamEvent.CacheHit -> {
                        // 缓存命中，直接显示缓存内容
                        currentAiContent.append(event.content)
                        _uiState.update { state ->
                            val updatedMessages = state.messages.map { msg ->
                                if (msg.id == aiMessageId) {
                                    msg.copy(content = currentAiContent.toString())
                                } else {
                                    msg
                                }
                            }
                            state.copy(messages = updatedMessages)
                        }
                    }
                    is StreamEvent.Clarification -> {
                        // 澄清提示，直接显示
                        currentAiContent.append(event.content)
                        _uiState.update { state ->
                            val updatedMessages = state.messages.map { msg ->
                                if (msg.id == aiMessageId) {
                                    msg.copy(content = currentAiContent.toString())
                                } else {
                                    msg
                                }
                            }
                            state.copy(messages = updatedMessages)
                        }
                    }
                    is StreamEvent.AudioUrl -> {
                        // 语音合成完成，更新 AI 消息的 audioUrl
                        _uiState.update { state ->
                            val updatedMessages = state.messages.map { msg ->
                                if (msg.id == aiMessageId) {
                                    msg.copy(audioUrl = event.url)
                                } else {
                                    msg
                                }
                            }
                            state.copy(messages = updatedMessages)
                        }
                    }
                    is StreamEvent.Done -> {
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
                        _uiState.update {
                            it.copy(
                                isSending = false,
                                error = event.message
                            )
                        }
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
}
