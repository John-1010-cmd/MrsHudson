package com.mrshudson.android.ui.components.chat

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import com.mrshudson.android.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 客户端 TTS 管理类
 * 实现语音合成，并支持音频缓存
 *
 * 优先使用 Android 系统 TextToSpeech，在讯飞 API 可用时使用讯飞
 *
 * @param context 上下文
 */
class XfyunTtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "XfyunTtsManager"

        // 讯飞 TTS API 地址
        private const val TTS_API_URL = "https://tts-api.xfyun.cn/v2/tts"

        // 缓存配置
        private const val CACHE_DIR = "tts_cache"
        private const val MAX_CACHE_SIZE_MB = 100L // 最大缓存 100MB

        // 默认发音人
        private const val VOICE_NAME = "xiaoyan"

        // 系统 TTS 标记
        const val SYSTEM_TTS_PREFIX = "system_tts:"
    }

    private var isInitialized = false
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private var ttsInitError: String? = null

    // 缓存目录
    val cacheDir: File by lazy {
        File(context.cacheDir, CACHE_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    // 讯飞 API 配置
    private val appId = BuildConfig.XFYUN_APP_ID
    private val apiKey = BuildConfig.XFYUN_API_KEY
    private val apiSecret = BuildConfig.XFYUN_API_SECRET

    // 协程作用域
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * 初始化 TTS 引擎
     */
    fun initialize() {
        // 初始化 Android 系统 TTS
        textToSpeech = TextToSpeech(context, this)
        Log.i(TAG, "正在初始化系统 TTS...")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // 设置中文语言
            val result = textToSpeech?.setLanguage(Locale.CHINA)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "系统 TTS 不支持中文")
                ttsInitError = "系统 TTS 不支持中文"
            } else {
                isTtsReady = true
                Log.i(TAG, "系统 TTS 初始化成功，可以使用")
            }
        } else {
            Log.e(TAG, "系统 TTS 初始化失败: $status")
            ttsInitError = "系统 TTS 初始化失败"
        }

        // 检查讯飞配置
        if (appId.isNotEmpty() && apiKey.isNotEmpty() && apiSecret.isNotEmpty()) {
            isInitialized = true
            Log.i(TAG, "讯飞 TTS 配置完整，可以使用")
        } else {
            Log.w(TAG, "讯飞 API 密钥未配置，将使用系统 TTS")
        }
    }

    /**
     * 检查是否可以使用
     */
    fun isReady(): Boolean = isTtsReady || isInitialized

    /**
     * 获取错误信息
     */
    fun getError(): String? = ttsInitError

    /**
     * 检查是否有缓存的音频文件
     */
    fun getCachedAudio(text: String): File? {
        val cacheKey = getCacheKey(text)
        val cachedFile = File(cacheDir, "$cacheKey.mp3")
        return if (cachedFile.exists() && cachedFile.length() > 100) cachedFile else null
    }

    /**
     * 合成语音（带缓存）
     * 优先使用缓存，然后使用讯飞 API，最后使用系统 TTS
     *
     * @param text 要合成文本
     * @param onComplete 合成完成回调，返回音频文件路径或特殊标记
     */
    fun synthesize(text: String, onComplete: (String?) -> Unit) {
        // 检查缓存
        val cachedFile = getCachedAudio(text)
        if (cachedFile != null) {
            Log.i(TAG, "使用缓存音频: ${cachedFile.absolutePath}, 大小: ${cachedFile.length()} bytes")
            onComplete(cachedFile.absolutePath)
            return
        }

        // 优先使用讯飞 API
        if (isInitialized) {
            synthesizeByXfyun(text, onComplete)
            return
        }

        // 使用系统 TTS（返回特殊标记让 AudioPlayer 处理）
        synthesizeBySystemTts(text, onComplete)
    }

    /**
     * 使用讯飞 API 合成
     */
    private fun synthesizeByXfyun(text: String, onComplete: (String?) -> Unit) {
        val cacheKey = getCacheKey(text)
        val outputFile = File(cacheDir, "$cacheKey.mp3")

        cleanCacheIfNeeded()

        coroutineScope.launch {
            try {
                Log.i(TAG, "使用讯飞 API 合成语音: ${text.take(50)}...")

                val audioData = synthesizeByXfyunApi(text)

                if (audioData != null && audioData.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        FileOutputStream(outputFile).use { fos ->
                            fos.write(audioData)
                        }
                    }
                    Log.i(TAG, "讯飞 TTS 成功，保存到: ${outputFile.absolutePath}, 大小: ${outputFile.length()} bytes")
                    withContext(Dispatchers.Main) {
                        onComplete(outputFile.absolutePath)
                    }
                } else {
                    // 讯飞失败，回退到系统 TTS
                    Log.w(TAG, "讯飞 TTS 失败，回退到系统 TTS")
                    withContext(Dispatchers.Main) {
                        synthesizeBySystemTts(text, onComplete)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "讯飞 TTS 异常", e)
                withContext(Dispatchers.Main) {
                    synthesizeBySystemTts(text, onComplete)
                }
            }
        }
    }

    /**
     * 使用系统 TTS 合成
     */
    private fun synthesizeBySystemTts(text: String, onComplete: (String?) -> Unit) {
        if (!isTtsReady) {
            Log.e(TAG, "系统 TTS 未就绪，无法播放")
            onComplete(null)
            return
        }

        // 清理无效缓存文件
        val cacheKey = getCacheKey(text)
        val cachedFile = File(cacheDir, "$cacheKey.mp3")
        if (cachedFile.exists() && cachedFile.length() <= 100) {
            cachedFile.delete()
        }

        cleanCacheIfNeeded()

        Log.i(TAG, "使用系统 TTS 播放: ${text.take(50)}...")

        // 标记为使用系统 TTS，让 AudioPlayer 直接调用 speak
        onComplete(SYSTEM_TTS_PREFIX + text)
    }

    /**
     * 使用系统 TTS 直接播放
     */
    fun speakWithSystemTts(text: String) {
        if (!isTtsReady) {
            Log.e(TAG, "系统 TTS 未就绪，无法播放")
            return
        }

        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        Log.i(TAG, "系统 TTS 正在播放: ${text.take(50)}...")
    }

    /**
     * 停止系统 TTS
     */
    fun stopSystemTts() {
        textToSpeech?.stop()
    }

    /**
     * 调用讯飞 TTS API
     */
    private fun synthesizeByXfyunApi(text: String): ByteArray? {
        try {
            // 生成鉴权 URL
            val authUrl = generateAuthUrl(TTS_API_URL, apiKey, apiSecret)

            // 构建请求体
            val requestBody = buildTtsRequestBody(text)

            // 发送请求
            val url = java.net.URL(authUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer; $authUrl")
            connection.doOutput = true

            // 写入请求体
            connection.outputStream.use { os ->
                os.write(requestBody.toByteArray(Charsets.UTF_8))
            }

            // 读取响应
            val responseCode = connection.responseCode
            Log.i(TAG, "讯飞 TTS API 响应码: $responseCode")

            if (responseCode == 200) {
                return connection.inputStream.use { it.readBytes() }
            } else {
                Log.e(TAG, "讯飞 TTS API 错误: ${connection.responseMessage}")
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "调用讯飞 TTS API 失败", e)
            return null
        }
    }

    /**
     * 构建 TTS 请求体
     */
    private fun buildTtsRequestBody(text: String): String {
        val encodedText = URLEncoder.encode(text, "utf-8")

        return """
            {
                "common": {
                    "app_id": "$appId"
                },
                "business": {
                    "aue": "lame",
                    "auf": "audio/L16;rate=16000",
                    "vcn": "$VOICE_NAME",
                    "speed": 50,
                    "volume": 50,
                    "pitch": 50,
                    "tte": "utf8"
                },
                "data": {
                    "status": 2,
                    "text": "$encodedText"
                }
            }
        """.trimIndent()
    }

    /**
     * 生成鉴权 URL
     */
    private fun generateAuthUrl(host: String, apiKey: String, apiSecret: String): String {
        val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }.format(Date())

        val signatureOrigin = "host: $host\ndate: $date\nGET /v2/tts HTTP/1.1"

        val signatureSha = hmacSha256(signatureOrigin, apiSecret)
        val signatureShaBase64 = Base64.encodeToString(signatureSha, Base64.NO_WRAP)

        val authorizationOrigin = "api_key=\"$apiKey\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"$signatureShaBase64\""
        val authorization = Base64.encodeToString(authorizationOrigin.toByteArray(), Base64.NO_WRAP)

        return "$host?authorization=$authorization&date=${URLEncoder.encode(date, "utf-8")}&host=$host"
    }

    private fun hmacSha256(data: String, key: String): ByteArray {
        val secretKey = SecretKeySpec(key.toByteArray(), "HmacSHA256")
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(secretKey)
        return mac.doFinal(data.toByteArray())
    }

    private fun getCacheKey(text: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(text.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            text.hashCode().toString()
        }
    }

    private fun cleanCacheIfNeeded() {
        try {
            val files = cacheDir.listFiles() ?: return
            var totalSize = files.sumOf { it.length() }
            val maxSize = MAX_CACHE_SIZE_MB * 1024 * 1024

            if (totalSize > maxSize) {
                val sortedFiles = files.sortedBy { it.lastModified() }
                for (file in sortedFiles) {
                    if (totalSize <= maxSize * 0.8) break
                    val size = file.length()
                    if (file.delete()) {
                        totalSize -= size
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理缓存失败", e)
        }
    }

    fun clearCache() {
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
            Log.i(TAG, "清理所有 TTS 缓存")
        } catch (e: Exception) {
            Log.e(TAG, "清理缓存失败", e)
        }
    }

    fun getCacheSize(): Long {
        return cacheDir.listFiles()?.sumOf { it.length() }?.div(1024 * 1024) ?: 0
    }

    /**
     * 获取缓存目录的绝对路径
     */
    fun getCachePath(): String = cacheDir.absolutePath

    fun release() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isTtsReady = false
        isInitialized = false
        Log.i(TAG, "TTS 已释放")
    }
}
