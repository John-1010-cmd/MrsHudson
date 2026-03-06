package com.mrshudson.android.ui.components.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * 语音识别结果回调
 * 使用抽象类而非接口，以支持多个回调方法
 */
abstract class VoiceRecognizerCallback {
    /**
     * 识别成功的回调
     * @param text 识别到的文本
     */
    open fun onResult(text: String) {}

    /**
     * 识别出错的回调
     * @param errorMessage 错误信息
     */
    open fun onError(errorMessage: String) {}

    /**
     * 开始录音的回调
     */
    open fun onReadyForSpeech() {}

    /**
     * 结束录音的回调
     */
    open fun onEndOfSpeech() {}

    /**
     * 录音中的回调（用于显示音量等）
     * @param volume 音量大小 (0-100)
     */
    open fun onRecording(volume: Int) {}
}

/**
 * 语音识别器
 * 使用 Android SpeechRecognizer API 进行语音识别
 */
class VoiceRecognizer(
    private val context: Context
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var callback: VoiceRecognizerCallback? = null
    private var isListening = false

    /**
     * 初始化语音识别器
     */
    fun initialize() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createRecognitionListener())
            }
        }
    }

    /**
     * 设置回调
     */
    fun setCallback(callback: VoiceRecognizerCallback) {
        this.callback = callback
    }

    /**
     * 开始语音识别
     */
    fun startListening() {
        if (speechRecognizer == null) {
            callback?.onError("语音识别不可用")
            return
        }

        if (isListening) {
            stopListening()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        isListening = true
        speechRecognizer?.startListening(intent)
        callback?.onReadyForSpeech()
    }

    /**
     * 停止语音识别
     */
    fun stopListening() {
        isListening = false
        speechRecognizer?.stopListening()
        callback?.onEndOfSpeech()
    }

    /**
     * 销毁语音识别器
     */
    fun destroy() {
        isListening = false
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    /**
     * 检查语音识别是否可用
     */
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * 是否正在监听
     */
    fun isListening(): Boolean = isListening

    /**
     * 创建 RecognitionListener
     */
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                // 已在上层处理
            }

            override fun onBeginningOfSpeech() {
                // 开始说话
            }

            override fun onRmsChanged(rmsdB: Float) {
                // 将音量转换为 0-100 的范围
                val volume = ((rmsdB + 10) / 10 * 50).toInt().coerceIn(0, 100)
                callback?.onRecording(volume)
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // 收到音频数据
            }

            override fun onEndOfSpeech() {
                // 已在上层处理
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "音频录制失败"
                    SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
                    SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                    SpeechRecognizer.ERROR_NO_MATCH -> "未识别到语音，请重试"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别服务忙碌"
                    SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "未检测到语音输入"
                    else -> "未知错误"
                }
                isListening = false
                callback?.onError(errorMessage)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    callback?.onResult(text)
                } else {
                    callback?.onError("未识别到语音内容")
                }
                isListening = false
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // 部分结果，可以用于实时显示
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // 特殊事件
            }
        }
    }
}
