package com.mrshudson.android.ui.components.chat

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.mrshudson.android.data.local.GitHubAudioCache
import com.mrshudson.android.data.local.datastore.SettingsDataStore
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
import kotlinx.coroutines.flow.first
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
 * 支持客户端 TTS 缓存机制
 *
 * 使用单例模式，确保同一时间只有一个音频在播放
 */
class AudioPlayer(
    private val context: Context,
    private val chatRepository: ChatRepository,
    private val settingsDataStore: SettingsDataStore
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

    // 客户端 TTS 管理器（用于缓存）
    private val xfyunTtsManager: XfyunTtsManager by lazy {
        XfyunTtsManager(context).apply { initialize() }
    }

    // GitHub 音频缓存管理器
    private val gitHubAudioCache: GitHubAudioCache by lazy {
        GitHubAudioCache(context)
    }

    /**
     * 检查 URL 是否为 GitHub Raw URL
     */
    private fun isGitHubUrl(url: String?): Boolean {
        return url?.contains("raw.githubusercontent.com") == true ||
                url?.contains("github.com") == true
    }

    /**
     * 替换 URL 中的 localhost 为配置的服务器地址
     */
    private fun replaceLocalhostWithServerUrl(originalUrl: String): String {
        var result = originalUrl

        // 尝试从设置中获取服务器地址
        try {
            val serverUrl = kotlinx.coroutines.runBlocking {
                settingsDataStore.getServerUrl().first()
            }

            if (serverUrl.isNotBlank()) {
                // 提取服务器的基础地址，如 http://localhost:8080
                val baseUrl = serverUrl.trimEnd('/')

                // 替换 localhost:8080
                result = result.replace("http://localhost:8080", baseUrl)
                result = result.replace("http://127.0.0.1:8080", baseUrl)
                Log.i(TAG, "URL 转换: $originalUrl -> $result")
            } else {
                Log.i(TAG, "未配置服务器地址，使用原始 URL: $originalUrl")
            }
        } catch (e: Exception) {
            Log.w(TAG, "获取服务器地址失败，使用原始 URL: $e")
        }

        return result
    }

    /**
     * 检查是否为系统 TTS 标记
     */
    private fun isSystemTts(audioPath: String?): Boolean {
        return audioPath?.startsWith(XfyunTtsManager.SYSTEM_TTS_PREFIX) == true
    }

    /**
     * 获取系统 TTS 的文本
     */
    private fun getSystemTtsText(audioPath: String?): String? {
        return audioPath?.removePrefix(XfyunTtsManager.SYSTEM_TTS_PREFIX)
    }

    /**
     * 设置状态变化回调
     */
    fun setOnStateChangedListener(listener: (Message) -> Unit) {
        onStateChanged = listener
    }

    /**
     * 清理 TTS 缓存
     */
    fun clearTtsCache() {
        xfyunTtsManager.clearCache()
    }

    /**
     * 获取 TTS 缓存大小（MB）
     */
    fun getTtsCacheSize(): Long {
        return xfyunTtsManager.getCacheSize()
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
     * 注意：系统 TTS 不支持暂停/恢复，需要重新播放
     *
     * @param message 消息
     */
    fun resume(message: Message) {
        currentMessage = message

        // 检查是否为系统 TTS
        if (message.audioUrl?.startsWith(XfyunTtsManager.SYSTEM_TTS_PREFIX) == true) {
            val text = getSystemTtsText(message.audioUrl)
            if (text != null) {
                Log.i(TAG, "系统 TTS 重新播放: ${text.take(50)}...")
                xfyunTtsManager.speakWithSystemTts(text)
                updateState(message.id, AudioPlayState.PLAYING, 0L)
            }
            return
        }

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
        // 检查是否为系统 TTS
        if (message.audioUrl?.startsWith(XfyunTtsManager.SYSTEM_TTS_PREFIX) == true) {
            xfyunTtsManager.stopSystemTts()
            updateState(message.id, AudioPlayState.PAUSED, 0L)
            return
        }

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
        // 停止系统 TTS
        xfyunTtsManager.stopSystemTts()

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
        xfyunTtsManager.release()
    }

    /**
     * 从URL播放音频（带audioUrl更新）
     * 用于TTS生成成功后播放，同时更新UI层消息的audioUrl
     */
    private fun playFromUrlWithAudioUpdate(message: Message, audioUrl: String, startPosition: Long) {
        // 替换 localhost 为配置的服务器地址
        val actualUrl = replaceLocalhostWithServerUrl(audioUrl)

        updateState(message.id, AudioPlayState.LOADING, startPosition, newAudioUrl = actualUrl)

        currentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioStreamType(android.media.AudioManager.STREAM_MUSIC)

                setDataSource(actualUrl)

                setOnPreparedListener { player ->
                    try {
                        if (startPosition > 0) {
                            player.seekTo(startPosition.toInt())
                        }
                        player.start()
                        // 播放成功后更新状态，传入audioUrl以更新UI层
                        updateState(message.id, AudioPlayState.PLAYING, startPosition, newAudioUrl = audioUrl)
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
     * 从URL播放音频
     *
     * @param message 消息
     * @param audioUrl 音频URL
     * @param startPosition 起始位置（毫秒）
     */
    private fun playFromUrl(message: Message, audioUrl: String, startPosition: Long) {
        // 替换 localhost 为配置的服务器地址
        var processedUrl = replaceLocalhostWithServerUrl(audioUrl)

        // 如果是 GitHub URL，尝试使用本地缓存
        if (isGitHubUrl(processedUrl)) {
            val cachedFile = gitHubAudioCache.getCachedFile(processedUrl)
            if (cachedFile != null) {
                Log.i(TAG, "使用 GitHub 缓存播放: ${cachedFile.absolutePath}")
                playFromLocalFile(message, cachedFile.absolutePath, startPosition)
                return
            }

            // 没有缓存，下载后播放
            Log.i(TAG, "GitHub 音频无缓存，下载后播放: $processedUrl")
            downloadAndPlayFromGitHub(message, processedUrl, startPosition)
            return
        }

        // 非 GitHub URL，直接流式播放
        updateState(message.id, AudioPlayState.LOADING, startPosition)

        currentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioStreamType(android.media.AudioManager.STREAM_MUSIC)

                setDataSource(processedUrl)

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
     * 从 GitHub 下载音频并播放
     */
    private fun downloadAndPlayFromGitHub(message: Message, url: String, startPosition: Long) {
        updateState(message.id, AudioPlayState.LOADING, startPosition)

        currentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        currentScope?.launch {
            val cachedFile = gitHubAudioCache.downloadAndCache(url)
            if (cachedFile != null) {
                Log.i(TAG, "GitHub 音频下载成功，播放本地文件: ${cachedFile.absolutePath}")
                playFromLocalFile(message, cachedFile.absolutePath, startPosition)
            } else {
                Log.e(TAG, "GitHub 音频下载失败，尝试流式播放")
                // 下载失败，回退到流式播放
                streamPlayFromUrl(message, url, startPosition)
            }
        }
    }

    /**
     * 从本地文件播放
     */
    private fun playFromLocalFile(message: Message, localPath: String, startPosition: Long) {
        updateState(message.id, AudioPlayState.LOADING, startPosition)

        currentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioStreamType(android.media.AudioManager.STREAM_MUSIC)

                setDataSource(localPath)

                setOnPreparedListener { player ->
                    try {
                        if (startPosition > 0) {
                            player.seekTo(startPosition.toInt())
                        }
                        player.start()
                        updateState(message.id, AudioPlayState.PLAYING, startPosition)
                    } catch (e: Exception) {
                        Log.e(TAG, "Play from local file prepared failed", e)
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
            Log.e(TAG, "PlayFromLocalFile failed", e)
            updateState(message.id, AudioPlayState.IDLE, 0L, e.message)
        }
    }

    /**
     * 流式播放（下载失败时的回退方案）
     */
    private fun streamPlayFromUrl(message: Message, url: String, startPosition: Long) {
        updateState(message.id, AudioPlayState.LOADING, startPosition)

        currentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioStreamType(android.media.AudioManager.STREAM_MUSIC)

                setDataSource(url)

                setOnPreparedListener { player ->
                    try {
                        if (startPosition > 0) {
                            player.seekTo(startPosition.toInt())
                        }
                        player.start()
                        updateState(message.id, AudioPlayState.PLAYING, startPosition)
                    } catch (e: Exception) {
                        Log.e(TAG, "Stream play prepared failed", e)
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
            Log.e(TAG, "StreamPlayFromUrl failed", e)
            updateState(message.id, AudioPlayState.IDLE, 0L, e.message)
        }
    }

    /**
     * 调用TTS API生成音频并播放
     * 优先使用客户端缓存，如果没有缓存则调用服务端API或本地生成
     *
     * @param message 消息
     */
    private fun generateAndPlay(message: Message) {
        updateState(message.id, AudioPlayState.LOADING, 0L)

        ttsJob?.cancel()
        ttsJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                // 首先检查客户端缓存
                val cachedFile = xfyunTtsManager.getCachedAudio(message.content)
                if (cachedFile != null) {
                    Log.i(TAG, "使用客户端缓存音频: ${cachedFile.absolutePath}")
                    withContext(Dispatchers.Main) {
                        val updatedMessage = message.copy(audioUrl = cachedFile.absolutePath)
                        currentMessage = updatedMessage
                        playFromUrlWithAudioUpdate(updatedMessage, cachedFile.absolutePath, 0L)
                    }
                    return@launch
                }

                // 没有缓存，调用服务端 TTS API
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
                                // 传入新的audioUrl以便更新UI层
                                playFromUrlWithAudioUpdate(updatedMessage, response.audioUrl, 0L)
                            } else {
                                // 服务端 TTS 失败，尝试使用客户端 TTS
                                playWithClientTts(message)
                            }
                        }
                        is ApiResult.Error -> {
                            // 服务端 TTS 失败，尝试使用客户端 TTS
                            playWithClientTts(message)
                        }
                        is ApiResult.Loading -> { /* ignore */ }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "TTS request failed", e)
                withContext(Dispatchers.Main) {
                    // 尝试使用客户端 TTS
                    playWithClientTts(message)
                }
            }
        }
    }

    /**
     * 使用客户端 TTS 播放
     */
    private fun playWithClientTts(message: Message) {
        xfyunTtsManager.synthesize(message.content) { audioPath ->
            if (audioPath == null) {
                updateState(message.id, AudioPlayState.IDLE, 0L, "语音合成失败")
                return@synthesize
            }

            // 检查是否为系统 TTS
            if (isSystemTts(audioPath)) {
                val text = getSystemTtsText(audioPath)
                if (text != null) {
                    Log.i(TAG, "使用系统 TTS 播放: ${text.take(50)}...")
                    updateState(message.id, AudioPlayState.PLAYING, 0L)
                    xfyunTtsManager.speakWithSystemTts(text)
                } else {
                    updateState(message.id, AudioPlayState.IDLE, 0L, "语音合成失败")
                }
                return@synthesize
            }

            // 使用文件播放
            val updatedMessage = message.copy(audioUrl = audioPath)
            currentMessage = updatedMessage
            playFromUrlWithAudioUpdate(updatedMessage, audioPath, 0L)
        }
    }

    /**
     * 更新状态并通知回调
     */
    private fun updateState(
        messageId: Long,
        playState: AudioPlayState,
        position: Long,
        error: String? = null,
        newAudioUrl: String? = null
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
                // 如果提供了新的audioUrl，则更新消息的audioUrl
                val updatedMessage = if (newAudioUrl != null) {
                    msg.copy(audioUrl = newAudioUrl, audioPlayState = playState, pausedPosition = position)
                } else {
                    msg.withAudioPlayState(playState, position)
                }
                currentMessage = updatedMessage
                onStateChanged?.invoke(updatedMessage)
            }
        }
    }
}
