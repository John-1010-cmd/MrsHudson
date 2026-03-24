package com.mrshudson.android.data.repository

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * SSE 流式客户端 - 统一处理层
 *
 * 封装 OkHttp EventSource + 事件解析 + 超时保护 + 异常处理
 * 遵循 SSE_STREAM_SPEC.md 规范
 */
class SseClient(
    private val okHttpClient: OkHttpClient,
    private val baseUrl: String
) {
    companion object {
        private const val TAG = "SseClient"
        private const val SSE_TIMEOUT_MS = 60000L // 60秒超时
        private const val TIMEOUT_CHECK_INTERVAL_MS = 5000L // 超时检测间隔
    }

    /**
     * 创建 SSE 流
     *
     * @param endpoint API 端点，如 "/api/chat/stream"
     * @param requestBody 请求体
     * @param headers 请求头
     * @return Flow<StreamEvent> 事件流
     */
    fun stream(
        endpoint: String,
        requestBody: String,
        headers: Map<String, String> = emptyMap()
    ): Flow<StreamEvent> = callbackFlow {
        val url = "${baseUrl.removeSuffix("/")}/$endpoint"

        val requestBuilder = Request.Builder()
            .url(url)
            .post(
                "application/json; charset=utf-8".toMediaType()
                    .let { mediaType -> requestBody.toRequestBody(mediaType) }
            )
            .addHeader("Accept", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")

        headers.forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }

        val request = requestBuilder.build()

        val factory = EventSources.createFactory(okHttpClient)

        val eventSourceListener = object : EventSourceListener() {
            private var closed = false
            private var lastEventTime = System.currentTimeMillis()
            private var timeoutHandler: Handler? = null
            private var timeoutRunnable: Runnable? = null

            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.d(TAG, "SSE 连接打开")
                lastEventTime = System.currentTimeMillis()
                startTimeoutCheck(eventSource)
            }

            private fun startTimeoutCheck(eventSource: EventSource) {
                timeoutHandler = Handler(Looper.getMainLooper())
                timeoutRunnable = object : Runnable {
                    override fun run() {
                        if (closed) return

                        val elapsed = System.currentTimeMillis() - lastEventTime
                        if (elapsed > SSE_TIMEOUT_MS) {
                            Log.w(TAG, "SSE 超时（${elapsed}ms 无数据）")
                            eventSource.cancel()
                            if (!closed) {
                                closed = true
                                trySend(StreamEvent.Error(null, "SSE 读取超时（${elapsed / 1000}秒无响应）"))
                                close()
                            }
                        } else {
                            // 继续检测
                            timeoutHandler?.postDelayed(this, TIMEOUT_CHECK_INTERVAL_MS)
                        }
                    }
                }
                timeoutHandler?.postDelayed(timeoutRunnable!!, TIMEOUT_CHECK_INTERVAL_MS)
            }

            private fun stopTimeoutCheck() {
                timeoutRunnable?.let { timeoutHandler?.removeCallbacks(it) }
                timeoutHandler = null
                timeoutRunnable = null
            }

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                lastEventTime = System.currentTimeMillis()
                Log.d(TAG, "SSE 事件: $data")

                try {
                    val event = parseEvent(data)
                    event?.let { trySend(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "SSE 事件解析失败", e)
                }
            }

            override fun onClosed(eventSource: EventSource) {
                Log.d(TAG, "SSE 连接关闭")
                stopTimeoutCheck()
                if (!closed) {
                    closed = true
                    trySend(StreamEvent.Done(null))
                    close()
                }
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?
            ) {
                val errorMsg = when {
                    t != null -> "SSE 连接失败: ${t.message}"
                    response != null -> "SSE 错误: ${response.code} ${response.message}"
                    else -> "SSE 连接失败"
                }
                Log.e(TAG, errorMsg)
                stopTimeoutCheck()

                if (!closed) {
                    closed = true
                    trySend(StreamEvent.Error(null, errorMsg))
                    close()
                }
            }
        }

        val eventSource = factory.newEventSource(request, eventSourceListener)

        awaitClose {
            Log.d(TAG, "SSE 清理资源")
            eventSourceListener.stopTimeoutCheck()
            if (!eventSourceListener.isClosed) {
                eventSource.cancel()
            }
        }
    }

    private val EventSourceListener.isClosed: Boolean
        get() = try {
            javaClass.getDeclaredField("closed").let { field ->
                field.isAccessible = true
                field.getBoolean(this)
            }
        } catch (e: Exception) {
            false
        }

    private fun EventSourceListener.stopTimeoutCheck() {
        try {
            val handlerField = javaClass.getDeclaredField("timeoutHandler")
            handlerField.isAccessible = true
            (handlerField.get(this) as? Handler)?.let { handler ->
                val runnableField = javaClass.getDeclaredField("timeoutRunnable")
                runnableField.isAccessible = true
                (runnableField.get(this) as? Runnable)?.let { handler.removeCallbacks(it) }
            }
        } catch (e: Exception) {
            // Ignore if fields don't exist
        }
    }

    /**
     * 解析 SSE 事件
     */
    private fun parseEvent(data: String): StreamEvent? {
        if (data.isBlank()) return null

        val jsonElement = JsonParser.parseString(data)
        if (!jsonElement.isJsonObject) return null

        val obj = jsonElement.asJsonObject
        val type = obj.get("type")?.asString ?: return null

        return when (type) {
            "content" -> StreamEvent.Content(
                null,
                obj.get("text")?.asString ?: obj.get("content")?.asString ?: ""
            )

            "audio_url" -> StreamEvent.AudioUrl(
                null,
                obj.get("url")?.asString ?: ""
            )

            "token_usage" -> StreamEvent.TokenUsage(
                null,
                obj.get("inputTokens")?.asInt ?: 0,
                obj.get("outputTokens")?.asInt ?: 0,
                obj.get("duration")?.asLong ?: 0L,
                obj.get("model")?.asString ?: "unknown"
            )

            "tool_call" -> {
                val toolCall = obj.getAsJsonObject("toolCall")
                if (toolCall != null) {
                    StreamEvent.ToolCall(
                        null,
                        toolCall.get("id")?.asString ?: "",
                        toolCall.get("name")?.asString ?: "",
                        toolCall.get("arguments")?.asString ?: ""
                    )
                } else null
            }

            "tool_result" -> {
                val toolResult = obj.getAsJsonObject("toolResult")
                if (toolResult != null) {
                    StreamEvent.ToolResult(
                        null,
                        toolResult.get("id")?.asString ?: "",
                        toolResult.get("name")?.asString ?: "",
                        toolResult.get("result")?.asString ?: ""
                    )
                } else null
            }

            "cache_hit" -> StreamEvent.CacheHit(
                null,
                obj.get("content")?.asString ?: ""
            )

            "clarification" -> StreamEvent.Clarification(
                null,
                obj.get("content")?.asString ?: ""
            )

            "error" -> StreamEvent.Error(
                null,
                obj.get("message")?.asString ?: "Unknown error"
            )

            "done" -> StreamEvent.Done(null)

            else -> null
        }
    }
}
