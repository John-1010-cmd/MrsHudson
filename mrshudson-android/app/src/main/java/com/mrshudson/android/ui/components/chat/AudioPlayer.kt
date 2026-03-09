package com.mrshudson.android.ui.components.chat

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.mrshudson.android.data.remote.ApiResult
import com.mrshudson.android.data.remote.dto.TtsRequest
import com.mrshudson.android.data.repository.ChatRepository
import com.mrshudson.android.domain.model.AudioPlayState
import com.mrshudson.android.domain.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 音频播放器状态
 */
data class AudioPlayerState(
    val currentMessageId: Long? = null,
    val playState: AudioPlayState = AudioPlayState.IDLE,
    val pausedPosition: Long = 0L,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * 音频播放器
 * 管理MediaPlayer实例，提供播放、暂停、继续功能
 *
 * 使用单例模式，确保同一时间只有一个音频在播放
 */
class AudioPlayer(
    private val context: Context,
    private val chatRepository: ChatRepository
) {
    companion object {
        private const val TAG = "AudioPlayer"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var currentScope: CoroutineScope? = null
    private var ttsJob: Job? = null

    private val _state = MutableStateFlow(AudioPlayerState())
    val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    private var currentMessage: Message? = null
    private var onStateChanged: ((Message) -> Unit)? = null

    /**
     * 设置状态变化回调
     */
    fun setOnStateChangedListener(listener: (Message) -> Unit) {
        onStateChanged = listener
    }

    /**
     * 播放或暂停音频
     * 如果有音频URL，直接播放；如果没有，调用TTS API生成
     *
     * @param message 包含音频URL或文本的消息
     */
    fun playOrPause(message: Message) {
        currentMessage = message

        when (message.audioPlayState) {
            AudioPlayState.PLAYING -> {
                pause(message)
            }
            AudioPlayState.PAUSED -> {
                resume(message)
            }
            AudioPlayState.IDLE, AudioPlayState.LOADING -> {
                if (!message.audioUrl.isNullOrBlank()) {
                    playFromUrl(message, message.audioUrl, message.pausedPosition)
                } else {
                    generateAndPlay(message)
                }
            }
        }
    }

    /**
     * 从头播放
     *
     * @param message 消息
     */
    fun replay(message: Message) {
        currentMessage = message
        stop()

        if (!message.audioUrl.isNullOrBlank()) {
            playFromUrl(message, message.audioUrl, 0L)
        } else {
            generateAndPlay(message)
        }
    }

    /**
     * 继续播放（从暂停位置）
     *
     * @param message 消息
     */
    fun resume(message: Message) {
        currentMessage = message
        mediaPlayer?.let { player ->
            if (player.isPlaying) return

            try {
                player.seekTo(message.pausedPosition.toInt())
                player.start()
                updateState(message.id, AudioPlayState.PLAYING, message.pausedPosition)
            } catch (e: Exception) {
                Log.e(TAG, "Resume failed", e)
                updateState(message.id, AudioPlayState.IDLE, 0L, e.message)
            }
        }
    }

    /**
     * 暂停播放
     *
     * @param message 消息
     */
    fun pause(message: Message) {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                try {
                    player.pause()
                    val position = player.currentPosition.toLong()
                    updateState(message.id, AudioPlayState.PAUSED, position)
                } catch (e: Exception) {
                    Log.e(TAG, "Pause failed", e)
                }
            }
        }
    }

    /**
     * 停止播放
     */
    fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stop failed", e)
        }
        mediaPlayer = null

        currentMessage?.let { msg ->
            updateState(msg.id, AudioPlayState.IDLE, 0L)
        }
        currentMessage = null
    }

    /**
     * 释放资源
     */
    fun release() {
        ttsJob?.cancel()
        stop()
        currentScope = null
    }

    /**
     * 从URL播放音频
     *
     * @param message 消息
     * @param audioUrl 音频URL
     * @param startPosition 起始位置（毫秒）
     */
    private fun playFromUrl(message: Message, audioUrl: String, startPosition: Long) {
        updateState(message.id, AudioPlayState.LOADING, startPosition)

        currentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioStreamType(android.media.AudioManager.STREAM_MUSIC)

                setDataSource(audioUrl)

                setOnPreparedListener { player ->
                    try {
                        if (startPosition > 0) {
                            player.seekTo(startPosition.toInt())
                        }
                        player.start()
                        updateState(message.id, AudioPlayState.PLAYING, startPosition)
                    } catch (e: Exception) {
                        Log.e(TAG, "Play prepared failed", e)
                        updateState(message.id, AudioPlayState.IDLE, 0L, e.message)
                    }
                }

                setOnCompletionListener {
                    updateState(message.id, AudioPlayState.IDLE, 0L)
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    updateState(message.id, AudioPlayState.IDLE, 0L, "播放出错")
                    true
                }

                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "PlayFromUrl failed", e)
            updateState(message.id, AudioPlayState.IDLE, 0L, e.message)
        }
    }

    /**
     * 调用TTS API生成音频并播放
     *
     * @param message 消息
     */
    private fun generateAndPlay(message: Message) {
        updateState(message.id, AudioPlayState.LOADING, 0L)

        ttsJob?.cancel()
        ttsJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val result = chatRepository.textToSpeech(
                    TtsRequest(text = message.content)
                )

                withContext(Dispatchers.Main) {
                    when (result) {
                        is ApiResult.Success -> {
                            val response = result.data
                            if (response.success && !response.audioUrl.isNullOrBlank()) {
                                // 更新消息的audioUrl
                                val updatedMessage = message.copy(audioUrl = response.audioUrl)
                                currentMessage = updatedMessage
                                playFromUrl(updatedMessage, response.audioUrl, 0L)
                            } else {
                                updateState(
                                    message.id,
                                    AudioPlayState.IDLE,
                                    0L,
                                    response.errorMessage ?: "语音合成失败"
                                )
                            }
                        }
                        is ApiResult.Error -> {
                            updateState(message.id, AudioPlayState.IDLE, 0L, result.message)
                        }
                        is ApiResult.Loading -> { /* ignore */ }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "TTS request failed", e)
                withContext(Dispatchers.Main) {
                    updateState(
                        message.id,
                        AudioPlayState.IDLE,
                        0L,
                        e.message ?: "语音合成失败"
                    )
                }
            }
        }
    }

    /**
     * 更新状态并通知回调
     */
    private fun updateState(
        messageId: Long,
        playState: AudioPlayState,
        position: Long,
        error: String? = null
    ) {
        _state.value = AudioPlayerState(
            currentMessageId = messageId,
            playState = playState,
            pausedPosition = position,
            isLoading = playState == AudioPlayState.LOADING,
            error = error
        )

        currentMessage?.let { msg ->
            if (msg.id == messageId) {
                val updatedMessage = msg.withAudioPlayState(playState, position)
                currentMessage = updatedMessage
                onStateChanged?.invoke(updatedMessage)
            }
        }
    }
}
