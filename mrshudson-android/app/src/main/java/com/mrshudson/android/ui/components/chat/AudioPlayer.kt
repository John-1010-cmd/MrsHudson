package com.mrshudson.android.ui.components.chat

import android.content.Context
import android.media.AudioAttributes
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Properties

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
    private val settingsDataStore: SettingsDataStore,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "AudioPlayer"

        // 临时文件清理配置
        private const val TEMP_FILE_PREFIX = "tts_auth_"
        private const val TEMP_FILE_EXTENSION = ".mp3"
        private const val MAX_TEMP_FILE_AGE_MS = 24 * 60 * 60 * 1000L // 24小时
        private const val MAX_TEMP_FILE_COUNT = 50 // 最大保留文件数
    }

    private var mediaPlayer: MediaPlayer? = null
    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
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

    // 已下载的临时文件列表，用于后续清理
    private val downloadedTempFiles = mutableListOf<File>()
    private val tempFilesLock = Object() // 用于同步访问临时文件列表

    /**
     * 检查 URL 是否为 GitHub Raw URL
     */
    private fun isGitHubUrl(url: String?): Boolean {
        return url?.contains("raw.githubusercontent.com") == true ||
                url?.contains("github.com") == true
    }

    /**
     * 检查 URL 是否需要认证（是否为服务端音频）
     * 公开的 URL（如 GitHub）不需要认证，服务端音频需要认证
     */
    private fun requiresAuth(url: String?, serverBaseUrl: String? = null): Boolean {
        if (url.isNullOrBlank()) return false
        // GitHub 公开 URL 不需要认证
        if (isGitHubUrl(url)) return false
        // 其他非 http/https 开头的或者包含服务端地址的都需要认证
        val serverUrl = serverBaseUrl ?: resolveServerBaseUrlSync() ?: "http://10.0.2.2:8080"
        return url.startsWith(serverUrl) || url.contains("/uploads/")
    }

    /**
     * 使用 OkHttp 下载音频到临时文件（带认证）
     * 通过 AuthInterceptor 自动添加 Authorization 头
     * 注意：需要在协程中调用此方法
     */
    private suspend fun downloadAudioWithAuth(url: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始下载音频: $url")
                val request = Request.Builder()
                    .url(url)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e(TAG, "下载音频失败: ${response.code}")
                    return@withContext null
                }

                // 记录响应头信息
                val contentType = response.header("Content-Type")
                val contentLength = response.body?.contentLength() ?: 0
                Log.d(TAG, "下载音频响应: Content-Type=$contentType, Content-Length=$contentLength")
                if (contentLength == 0L) {
                    Log.e(TAG, "下载音频失败: 内容为空")
                    return@withContext null
                }

                // 检查内容类型
                if (contentType != null && !contentType.contains("audio") && !contentType.contains("octet-stream") && !contentType.contains("mpeg")) {
                    Log.e(TAG, "下载的内容不是音频: Content-Type=$contentType")
                    // 尝试读取前100个字符作为文本查看
                    val previewBytes = response.peekBody(200).bytes()
                    val preview = String(previewBytes, Charsets.UTF_8).take(100)
                    Log.e(TAG, "响应内容预览: $preview")
                    return@withContext null
                }

                val tempFile = File(context.cacheDir, "tts_auth_${System.currentTimeMillis()}.mp3")
                response.body?.byteStream()?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // 检查文件是否有效（非空且大小合理）
                if (!tempFile.exists() || tempFile.length() == 0L) {
                    Log.e(TAG, "下载的音频文件无效: 文件不存在或为空")
                    tempFile.delete()
                    return@withContext null
                }

                // 检查文件大小是否合理（至少1KB）
                if (tempFile.length() < 1024) {
                    Log.e(TAG, "下载的音频文件太小: ${tempFile.length()} bytes，可能无效")
                    // 读取小文件内容查看
                    val content = tempFile.readText(Charsets.UTF_8)
                    Log.e(TAG, "小文件内容: $content")
                    tempFile.delete()
                    return@withContext null
                }

                // 检查文件头是否为有效的音频格式
                val header = tempFile.inputStream().use { it.readNBytes(16) }
                val headerHex = header.joinToString(" ") { String.format("%02X", it) }
                Log.d(TAG, "下载文件头 (hex): $headerHex")

                // MP3 文件通常以 ID3 或 FFF/FFE 开头
                // 检查是否为 HTML 文件（<html, <HTML, <!-- 等）
                val headerStr = String(header, Charsets.UTF_8).take(10)
                if (headerStr.contains("<html") || headerStr.contains("<HTML") || headerStr.contains("<!")) {
                    Log.e(TAG, "下载的内容是 HTML 页面而非音频文件")
                    tempFile.delete()
                    return@withContext null
                }

                Log.i(TAG, "音频下载成功: ${tempFile.absolutePath}, 大小: ${tempFile.length()} bytes")

                // 记录临时文件以便后续清理
                synchronized(tempFilesLock) {
                    downloadedTempFiles.add(tempFile)
                    // 如果临时文件过多，触发清理
                    if (downloadedTempFiles.size > MAX_TEMP_FILE_COUNT) {
                        cleanupTempFiles()
                    }
                }

                tempFile
            } catch (e: Exception) {
                Log.e(TAG, "下载音频异常", e)
                null
            }
        }
    }

    /**
     * 替换 URL 中的 localhost 为配置的服务器地址（协程安全版本）
     */
    private suspend fun replaceLocalhostWithServerUrl(originalUrl: String): String {
        val resolvedServerBaseUrl = resolveServerBaseUrl()?.trimEnd('/')

        Log.d(TAG, "replaceLocalhostWithServerUrl: 原始URL=$originalUrl, 解析的服务器地址=$resolvedServerBaseUrl")

        var result = originalUrl.trim()
        if (result.isEmpty()) return result

        if (resolvedServerBaseUrl != null) {
            val replaced = result
                .replace("http://localhost:8080", resolvedServerBaseUrl)
                .replace("http://127.0.0.1:8080", resolvedServerBaseUrl)
                .replace("https://localhost:8080", resolvedServerBaseUrl)
                .replace("https://127.0.0.1:8080", resolvedServerBaseUrl)
            if (replaced != result) {
                Log.i(TAG, "URL 转换: $result -> $replaced")
            }
            result = replaced
        }

        // 处理后端返回相对路径（例如 /uploads/tts/xxx.mp3 或 uploads/tts/xxx.mp3）
        if (!result.startsWith("http://") && !result.startsWith("https://") && resolvedServerBaseUrl != null) {
            result = if (result.startsWith("/")) {
                resolvedServerBaseUrl + result
            } else {
                "$resolvedServerBaseUrl/$result"
            }
            Log.i(TAG, "URL 补全: $originalUrl -> $result")
        }

        Log.d(TAG, "replaceLocalhostWithServerUrl: 最终URL=$result")

        return result
    }

    /**
     * 异步解析服务端基础地址（用于协程环境）
     *
     * 优先级：
     * 1) SettingsDataStore 中用户配置的 serverUrl
     * 2) assets/config.properties 中的 api.baseurl（Retrofit 同源配置）
     * 3) Android 模拟器默认宿主机地址 http://10.0.2.2:8080
     */
    private suspend fun resolveServerBaseUrl(): String? {
        // 1) 用户配置
        try {
            val serverUrl = settingsDataStore.getServerUrl().first().trim()
            if (serverUrl.isNotBlank()) {
                return serverUrl.removeSuffix("/api").removeSuffix("/api/").trimEnd('/')
            }
        } catch (e: Exception) {
            Log.w(TAG, "读取 serverUrl 失败: ${e.message}")
        }

        // 2) assets/config.properties（NetworkModule 也会读）
        return readServerUrlFromAssets()
    }

    /**
     * 同步解析服务端基础地址（用于非协程环境，只读取 assets）
     */
    private fun resolveServerBaseUrlSync(): String? {
        // 直接从 assets 读取，避免阻塞 DataStore
        return readServerUrlFromAssets()
    }

    /**
     * 从 assets/config.properties 读取服务器地址
     */
    private fun readServerUrlFromAssets(): String? {
        try {
            val inputStream = context.assets.open("config.properties")
            val tempFile = File(context.cacheDir, "config.properties")
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()

            val properties = Properties()
            tempFile.inputStream().use { stream ->
                properties.load(stream)
            }
            tempFile.delete()

            val apiBaseUrl = properties.getProperty("api.baseurl")?.trim()
            if (!apiBaseUrl.isNullOrBlank()) {
                val normalized = if (apiBaseUrl.endsWith("/")) apiBaseUrl else "$apiBaseUrl/"
                return normalized
                    .removeSuffix("api/")
                    .removeSuffix("api")
                    .trimEnd('/')
            }
        } catch (e: Exception) {
            // ignore
        }

        // 3) fallback（模拟器访问宿主机）
        return "http://10.0.2.2:8080"
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
            // 安全的状态检查和操作，防止 IllegalStateException
            val canResume = try {
                player.isPlaying == false &&
                (player.currentPosition > 0 || message.pausedPosition > 0)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "MediaPlayer 状态异常，无法恢复播放", e)
                false
            }

            if (!canResume) {
                Log.w(TAG, "MediaPlayer 状态不允许恢复播放，尝试重新播放")
                // 尝试重新播放
                replay(message)
                return
            }

            try {
                val position = message.pausedPosition.toInt().coerceIn(0, player.duration)
                player.seekTo(position)
                player.start()
                updateState(message.id, AudioPlayState.PLAYING, position.toLong())
            } catch (e: Exception) {
                Log.e(TAG, "Resume failed", e)
                updateState(message.id, AudioPlayState.IDLE, 0L, e.message)
            }
        } ?: run {
            // MediaPlayer 为空，尝试重新播放
            Log.w(TAG, "MediaPlayer 为空，尝试重新播放")
            replay(message)
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
            // 安全地检查播放状态
            val isCurrentlyPlaying = try {
                player.isPlaying
            } catch (e: IllegalStateException) {
                Log.w(TAG, "MediaPlayer 状态异常，无法检查播放状态", e)
                false
            }

            if (isCurrentlyPlaying) {
                try {
                    player.pause()
                    val position = try {
                        player.currentPosition.toLong()
                    } catch (e: IllegalStateException) {
                        Log.w(TAG, "无法获取当前位置", e)
                        message.pausedPosition
                    }
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

        // 安全地停止和释放 MediaPlayer
        mediaPlayer?.let { player ->
            try {
                // 无论是否在播放，都尝试停止（某些状态下不会抛出异常）
                val wasPlaying = try {
                    player.isPlaying
                } catch (e: IllegalStateException) {
                    false
                }

                if (wasPlaying) {
                    try {
                        player.stop()
                    } catch (e: IllegalStateException) {
                        // 忽略：可能已经在停止状态
                    }
                }

                player.release()
            } catch (e: Exception) {
                Log.e(TAG, "Stop/Release failed", e)
            }
            mediaPlayer = null
        }

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
        xfyunTtsManager.release()

        // 清理临时文件
        cleanupTempFiles(force = true)
    }

    /**
     * 清理临时文件
     *
     * @param force 是否强制清理所有临时文件（应用退出时使用）
     */
    private fun cleanupTempFiles(force: Boolean = false) {
        synchronized(tempFilesLock) {
            val now = System.currentTimeMillis()
            val iterator = downloadedTempFiles.iterator()
            var deletedCount = 0

            while (iterator.hasNext()) {
                val file = iterator.next()
                val shouldDelete = when {
                    force -> true
                    !file.exists() -> {
                        iterator.remove()
                        false // 已经不存在，只从列表移除
                    }
                    (now - file.lastModified()) > MAX_TEMP_FILE_AGE_MS -> true
                    downloadedTempFiles.size > MAX_TEMP_FILE_COUNT -> true
                    else -> false
                }

                if (shouldDelete) {
                    try {
                        if (file.delete()) {
                            iterator.remove()
                            deletedCount++
                            Log.d(TAG, "清理临时文件: ${file.name}")
                        } else {
                            Log.w(TAG, "无法删除临时文件: ${file.absolutePath}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "清理临时文件失败", e)
                    }
                }
            }

            // 额外扫描并清理 cacheDir 中可能遗留的旧临时文件
            if (force || deletedCount > 0) {
                cleanupOrphanedTempFiles()
            }

            if (deletedCount > 0) {
                Log.i(TAG, "清理完成，删除了 $deletedCount 个临时文件，剩余 ${downloadedTempFiles.size} 个")
            }
        }
    }

    /**
     * 清理 cacheDir 中遗留的孤儿临时文件（没有被记录在 downloadedTempFiles 中的文件）
     */
    private fun cleanupOrphanedTempFiles() {
        try {
            val cacheDir = context.cacheDir
            val orphanedFiles = cacheDir.listFiles { file ->
                file.name.startsWith(TEMP_FILE_PREFIX) &&
                file.name.endsWith(TEMP_FILE_EXTENSION) &&
                file !in downloadedTempFiles
            }

            orphanedFiles?.forEach { file ->
                val age = System.currentTimeMillis() - file.lastModified()
                if (age > MAX_TEMP_FILE_AGE_MS) {
                    try {
                        if (file.delete()) {
                            Log.d(TAG, "清理孤儿临时文件: ${file.name}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "无法删除孤儿临时文件: ${file.name}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理孤儿临时文件失败", e)
        }
    }

    /**
     * 从URL播放音频（带audioUrl更新）
     * 用于TTS生成成功后播放，同时更新UI层消息的audioUrl
     */
    private fun playFromUrlWithAudioUpdate(message: Message, audioUrl: String, startPosition: Long) {
        // 在后台线程替换 localhost 为配置的服务器地址，避免在主线程阻塞
        playerScope.launch {
            val actualUrl = withContext(Dispatchers.IO) {
                replaceLocalhostWithServerUrl(audioUrl)
            }
            proceedWithPlayFromUrlWithAudioUpdate(message, actualUrl, startPosition)
        }
    }

    /**
     * 实际执行带 URL 更新的播放逻辑
     */
    private fun proceedWithPlayFromUrlWithAudioUpdate(message: Message, actualUrl: String, startPosition: Long) {
        updateState(message.id, AudioPlayState.LOADING, startPosition, newAudioUrl = actualUrl)

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )

                setDataSource(actualUrl)

                setOnPreparedListener { player ->
                    try {
                        if (startPosition > 0) {
                            player.seekTo(startPosition.toInt())
                        }
                        player.start()
                        // 播放成功后更新状态，传入audioUrl以更新UI层
                        updateState(message.id, AudioPlayState.PLAYING, startPosition, newAudioUrl = actualUrl)
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
        // 在后台线程替换 localhost 为配置的服务器地址，避免在主线程阻塞
        playerScope.launch {
            val processedUrl = withContext(Dispatchers.IO) {
                replaceLocalhostWithServerUrl(audioUrl)
            }
            proceedWithPlayFromUrl(message, processedUrl, startPosition)
        }
    }

    /**
     * 实际执行播放逻辑（在后台线程处理完 URL 后调用）
     */
    private fun proceedWithPlayFromUrl(message: Message, processedUrl: String, startPosition: Long) {

        // 如果是 GitHub URL，尝试使用本地缓存
        if (isGitHubUrl(processedUrl)) {
            playerScope.launch {
                val cachedFile = gitHubAudioCache.getCachedFile(processedUrl)
                if (cachedFile != null) {
                    Log.i(TAG, "使用 GitHub 缓存播放: ${cachedFile.absolutePath}")
                    playFromLocalFile(message, cachedFile.absolutePath, startPosition)
                    return@launch
                }

                // 没有缓存，下载后播放
                Log.i(TAG, "GitHub 音频无缓存，下载后播放: $processedUrl")
                downloadAndPlayFromGitHub(message, processedUrl, startPosition)
            }
            return
        }

        // 如果是需要认证的 URL（服务端音频），先下载到临时文件再播放
        if (requiresAuth(processedUrl, resolveServerBaseUrlSync())) {
            Log.i(TAG, "服务端音频需要认证，先下载到临时文件: $processedUrl")
            downloadAndPlayWithAuth(message, processedUrl, startPosition)
            return
        }

        // 公开 URL，直接流式播放
        updateState(message.id, AudioPlayState.LOADING, startPosition)

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )

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
     * 使用 OkHttp 下载需要认证的音频并播放
     * 如果下载失败，回退到直接流式播放
     */
    private fun downloadAndPlayWithAuth(message: Message, url: String, startPosition: Long) {
        updateState(message.id, AudioPlayState.LOADING, startPosition)

        playerScope.launch {
            val tempFile = downloadAudioWithAuth(url)
            if (tempFile != null && tempFile.exists()) {
                Log.i(TAG, "认证音频下载成功，播放本地文件: ${tempFile.absolutePath}")
                playFromLocalFile(message, tempFile.absolutePath, startPosition)
            } else {
                Log.w(TAG, "认证音频下载失败，尝试直接流式播放: $url")
                // 下载失败，回退到直接流式播放（像前端那样）
                streamPlayFromUrl(message, url, startPosition)
            }
        }
    }

    /**
     * 从 GitHub 下载音频并播放
     */
    private fun downloadAndPlayFromGitHub(message: Message, url: String, startPosition: Long) {
        updateState(message.id, AudioPlayState.LOADING, startPosition)

        playerScope.launch {
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
     * suspend 函数，必须在协程中调用（避免嵌套 launch 导致 MediaPlayer 竞态崩溃）
     */
    private suspend fun playFromLocalFile(message: Message, localPath: String, startPosition: Long) {
        // 切换到主线程执行（调用方可能在 IO 线程）
        withContext(Dispatchers.Main) {
            updateState(message.id, AudioPlayState.LOADING, startPosition)

            val file = File(localPath)
            if (!file.exists()) {
                Log.e(TAG, "播放本地文件失败: 文件不存在 - $localPath")
                updateState(message.id, AudioPlayState.IDLE, 0L, "音频文件不存在")
                return@withContext
            }
            if (file.length() == 0L) {
                Log.e(TAG, "播放本地文件失败: 文件为空 - $localPath")
                updateState(message.id, AudioPlayState.IDLE, 0L, "音频文件为空")
                return@withContext
            }

            Log.i(TAG, "准备播放本地文件: $localPath, 大小: ${file.length()} bytes")

            try {
                // 先安全释放旧实例
                mediaPlayer?.let { old ->
                    try { old.stop() } catch (_: Exception) {}
                    old.release()
                }
                mediaPlayer = null

                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )

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
                        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra, file=${file.absolutePath}, size=${file.length()}")
                        try {
                            val fis = java.io.FileInputStream(file)
                            val header = ByteArray(16)
                            val read = fis.read(header)
                            fis.close()
                            val headerHex = header.take(read).joinToString(" ") { String.format("%02X", it) }
                            Log.e(TAG, "文件头 (hex): $headerHex")
                        } catch (e: Exception) {
                            Log.e(TAG, "读取文件头失败: ${e.message}")
                        }
                        updateState(message.id, AudioPlayState.IDLE, 0L, "音频格式不支持或文件损坏")
                        true
                    }

                    prepareAsync()
                }
            } catch (e: Exception) {
                Log.e(TAG, "PlayFromLocalFile failed", e)
                updateState(message.id, AudioPlayState.IDLE, 0L, e.message)
            }
        }
    }

    /**
     * 流式播放（下载失败时的回退方案）
     */
    private fun streamPlayFromUrl(message: Message, url: String, startPosition: Long) {
        updateState(message.id, AudioPlayState.LOADING, startPosition)

        playerScope.launch {
            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )

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
                            val audioUrl = response.audioUrl
                            if (response.success && !audioUrl.isNullOrBlank()) {
                                // 更新消息的audioUrl
                                val updatedMessage = message.copy(audioUrl = audioUrl)
                                currentMessage = updatedMessage
                                // 传入新的audioUrl以便更新UI层
                                playFromUrlWithAudioUpdate(updatedMessage, audioUrl, 0L)
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
