package com.mrshudson.android.ui.screens.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrshudson.android.data.remote.ApiResult
import com.mrshudson.android.data.repository.ChatRepository
import com.mrshudson.android.data.repository.SendMessageResult
import com.mrshudson.android.domain.model.Conversation
import com.mrshudson.android.domain.model.Message
import com.mrshudson.android.domain.model.MessageRole
import com.mrshudson.android.ui.components.chat.AudioPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * AI 对话页面的 UI 状态
 */
data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val conversations: List<Conversation> = emptyList(),
    val currentConversationId: String? = null,
    val currentConversationTitle: String = "新对话",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null
)

/**
 * AI 对话页面的 ViewModel
 * 管理对话状态、处理用户交互、TTS播放
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // 音频播放器
    val audioPlayer: AudioPlayer by lazy {
        AudioPlayer(application.applicationContext, chatRepository)
    }

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
     */
    fun loadConversations() {
        viewModelScope.launch {
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
    fun selectConversation(conversationId: String) {
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
    private fun loadHistory(conversationId: String) {
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
    fun deleteConversation(conversationId: String) {
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
