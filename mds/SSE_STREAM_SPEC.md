# SSE 流式输出规范

## 概述

本文档定义 MrsHudson 项目中 SSE (Server-Sent Events) 流式输出的统一格式，确保后端、前端、安卓端三方解析逻辑一致。

## SSE 格式标准

### 基本格式

所有 SSE 事件必须遵循标准格式：

```
data: {"type":"<event_type>","..."}\n\n
```

- 每条消息以 `data: ` 开头
- 消息体为 JSON 对象
- 以两个换行符 `\n\n` 结尾

### 错误示例

```
{"type":"content","text":"Hello"}  ❌ 缺少 data: 前缀
data:data: {"type":"content",...}  ❌ 双重前缀
data: {"type":"content"} \n      ❌ 单换行符
```

### 正确示例

```
data: {"type":"content","text":"Hello"}\n\n
data: {"type":"audio_url","url":"http://example.com/audio.mp3"}\n\n
data: {"type":"done"}\n\n
```

## 事件类型定义

| 事件类型 | 说明 | 字段 |
|----------|------|------|
| `content` | AI 增量内容 | `text`: string |
| `token_usage` | Token 统计 | `inputTokens`, `outputTokens`, `duration`, `model` |
| `audio_url` | 语音合成完成 | `url`: string |
| `tool_call` | 工具调用 | `toolCall`: {name, arguments} |
| `tool_result` | 工具执行结果 | `toolResult`: {name, result} |
| `cache_hit` | 缓存命中 | `content`: string |
| `clarification` | 澄清提示 | `content`: string |
| `error` | 错误信息 | `message`: string |
| `done` | 流式完成 | - |

---

## 后端实现规范

### 架构设计

后端采用 **Service 层构建事件 → Controller 层统一格式化** 的双层架构：

```
┌─────────────────────────────────────────────────────────────┐
│                     Controller 层                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  transform(SseFormatter::addSsePrefix)              │   │
│  │  统一添加 data: 前缀，将 JSON 转换为 SSE 格式         │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              ↑
                              │
┌─────────────────────────────────────────────────────────────┐
│                     Service 层                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  SseFormatter.content(), SseFormatter.audioUrl()     │   │
│  │  构建纯 JSON 字符串，不包含 data: 前缀                 │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 核心组件

#### 1. SseEvent - 事件数据结构

```java
@Data
@Builder
public class SseEvent {
    private String type;
    private String text;
    private String content;
    private String url;
    private String message;
    private ToolCallInfo toolCall;
    private ToolResultInfo toolResult;
    private TokenUsageInfo tokenUsage;

    // 工厂方法
    public static SseEvent content(String text) { ... }
    public static SseEvent audioUrl(String url) { ... }
    public static SseEvent tokenUsage(int input, int output, long duration, String model) { ... }
    // ... 其他事件类型
}
```

#### 2. SseFormatter - 格式化工具类

```java
public final class SseFormatter {
    // 格式化方法
    public static String format(SseEvent event) { ... }
    public static String format(String jsonStr) { ... }

    // Flux 转换方法
    public static Flux<String> addSsePrefix(Flux<String> flux) { ... }

    // 便捷构建方法
    public static String content(String text) { ... }
    public static String audioUrl(String url) { ... }
    public static String tokenUsage(int input, int output, long duration, String model) { ... }
    // ... 其他事件类型
}
```

### Controller 层实现

**文件**: `StreamChatController.java`

```java
@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> streamSendMessage(@Valid @RequestBody SendMessageRequest request) {
    Long userId = authService.getCurrentUser().getId();

    return streamChatService.streamSendMessage(userId, request)
            .transform(SseFormatter::addSsePrefix)  // 统一添加 SSE 前缀
            .doOnSubscribe(s -> log.info("SSE 流式响应开始，用户ID: {}", userId))
            .doOnComplete(() -> log.info("SSE 流式响应完成，用户ID: {}", userId))
            .doOnError(e -> log.error("SSE 流式响应异常，用户ID: {}, 错误: {}", userId, e.getMessage(), e));
}
```

**要点**：
- Controller 是 SSE 格式化的**唯一入口**
- 使用 `transform(SseFormatter::addSsePrefix)` 统一处理
- Service 层返回纯 JSON 字符串，不包含 `data:` 前缀

### Service 层实现

**文件**: `StreamChatService.java`

```java
public Flux<String> streamSendMessage(Long userId, SendMessageRequest request) {
    // 1. 缓存命中
    if (cacheResult.isHit()) {
        return Flux.just(SseFormatter.cacheHit(escapeJson(cacheResult.getResponse())));
    }

    // 2. 意图路由直接处理
    if (routeResult.isHandled()) {
        if (audioUrl != null) {
            return Flux.concat(
                Flux.just(SseFormatter.content(escapeJson(response))),
                Flux.just(SseFormatter.audioUrl(escapeJson(audioUrl)))
            );
        }
        return Flux.just(SseFormatter.content(escapeJson(response)));
    }

    // 3. AI 流式响应
    return aiStream
        .flatMap(event -> Flux.just(SseFormatter.content(escapeJson(event))))
        .concatWith(Flux.just(SseFormatter.tokenUsage(...)))       // Token 统计
        .concatWith(Flux.just(SseFormatter.audioUrl(...)))          // 音频 URL
        .concatWith(Flux.just(SseFormatter.done()));                // 完成标记
}
```

### 统一入口原则

| 层级 | 职责 | 输出格式 |
|------|------|----------|
| Service | 构建事件 JSON | `{"type":"content","text":"Hello"}` |
| Controller | 添加 SSE 前缀 | `data: {"type":"content","text":"Hello"}\n\n` |

**禁止**：
- Service 层不应添加 `data:` 前缀
- 其他 Controller 不应自行实现 SSE 格式化逻辑

### 新增 SSE 接口规范

如果需要新增 SSE 流式接口，必须：

1. **使用 SseFormatter** 构建事件
2. **通过 Controller 统一格式化**，不要在 Service 或 Controller 中硬编码 SSE 格式

---

## 前端统一 SSE 处理层

### 架构设计

前端采用 **SseClient 封装 fetch + 事件解析 + 异常处理** 的统一架构：

```
┌─────────────────────────────────────────────────────────────┐
│                      SseClient (统一入口)                      │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  - fetch + ReadableStream 封装                      │   │
│  │  - SSE 数据解析 (extractSseData)                     │   │
│  │  - 超时保护 (safeRead + Promise.race)                │   │
│  │  - 异常处理 (done/doneReceived 标志)                  │   │
│  │  - 事件回调 (onContent, onAudioUrl, onDone, ...)      │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              ↓
                    ┌───────────────────────┐
                    │    业务层调用示例        │
                    │  SseClient.streamChat() │
                    └───────────────────────┘
```

### 核心组件

#### 1. SseClient - SSE 统一客户端

**文件**: `src/utils/sse/SseClient.ts`

```typescript
export interface SseClientOptions {
  url: string
  method?: 'GET' | 'POST'
  headers?: Record<string, string>
  body?: any
  timeout?: number  // 超时时间(ms)，默认 60000
  onContent?: (text: string) => void        // content 事件
  onAudioUrl?: (url: string) => void       // audio_url 事件
  onTokenUsage?: (usage: TokenUsage) => void // token_usage 事件
  onToolCall?: (tool: ToolCallInfo) => void // tool_call 事件
  onToolResult?: (result: ToolResultInfo) => void // tool_result 事件
  onCacheHit?: (content: string) => void    // cache_hit 事件
  onClarification?: (content: string) => void // clarification 事件
  onError?: (error: string) => void        // error 事件
  onDone?: () => void                       // done 事件
  onOpen?: () => void                       // 连接打开
  onClose?: () => void                     // 连接关闭
}

export interface TokenUsage {
  inputTokens: number
  outputTokens: number
  duration: number
  model: string
}

export interface ToolCallInfo {
  name: string
  arguments: string
}

export interface ToolResultInfo {
  name: string
  result: string
}

export class SseClient {
  private abortController: AbortController
  private timeoutId?: number

  constructor(private options: SseClientOptions) {
    this.abortController = new AbortController()
  }

  async stream(): Promise<void> {
    const { timeout = 60000 } = this.options
    const startTime = Date.now()
    let lastEventTime = startTime

    const response = await fetch(this.options.url, {
      method: this.options.method || 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
        'Cache-Control': 'no-cache',
        ...this.options.headers
      },
      body: JSON.stringify(this.options.body),
      signal: this.abortController.signal
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const reader = response.body?.getReader()
    if (!reader) throw new Error('No response body')

    this.options.onOpen?.()

    const decoder = new TextDecoder()
    let buffer = ''
    let doneReceived = false

    try {
      while (true) {
        // 超时保护：60秒无数据则中断
        const { done, value } = await Promise.race([
          reader.read(),
          new Promise<{ done: true; value: undefined }>((_, reject) => {
            this.timeoutId = window.setTimeout(() => reject(new Error('SSE_TIMEOUT')), timeout)
          })
        ])

        if (this.timeoutId) {
          clearTimeout(this.timeoutId)
          this.timeoutId = undefined
        }

        lastEventTime = Date.now()

        if (done) {
          if (!doneReceived) {
            console.warn('[SSE] 连接关闭但未收到 done 事件')
          }
          break
        }

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          const dataStr = this.extractSseData(line)
          if (!dataStr) continue

          try {
            const data = JSON.parse(dataStr)
            this.handleEvent(data)
          } catch (e) {
            console.error('[SSE] JSON 解析失败:', e)
          }
        }
      }
    } finally {
      this.options.onClose?.()
    }
  }

  private extractSseData(line: string): string | null {
    const trimmed = line.trim()
    if (!trimmed) return null
    if (trimmed.startsWith('data:')) {
      const dataContent = trimmed.slice(5).trim()
      if (dataContent.startsWith('{') && dataContent.endsWith('}')) {
        return dataContent
      }
    }
    return null
  }

  private handleEvent(data: any): void {
    switch (data.type) {
      case 'content':
        this.options.onContent?.(data.text || data.content || '')
        break
      case 'audio_url':
        this.options.onAudioUrl?.(data.url)
        break
      case 'token_usage':
        this.options.onTokenUsage?.({
          inputTokens: data.inputTokens || 0,
          outputTokens: data.outputTokens || 0,
          duration: data.duration || 0,
          model: data.model || 'unknown'
        })
        break
      case 'tool_call':
        if (data.toolCall) {
          this.options.onToolCall?.({
            name: data.toolCall.name || '',
            arguments: data.toolCall.arguments || ''
          })
        }
        break
      case 'tool_result':
        if (data.toolResult) {
          this.options.onToolResult?.({
            name: data.toolResult.name || '',
            result: data.toolResult.result || ''
          })
        }
        break
      case 'cache_hit':
        this.options.onCacheHit?.(data.content || '')
        break
      case 'clarification':
        this.options.onClarification?.(data.content || '')
        break
      case 'error':
        this.options.onError?.(data.message || 'Unknown error')
        break
      case 'done':
        this.options.onDone?.()
        break
    }
  }

  abort(): void {
    this.abortController.abort()
    if (this.timeoutId) {
      clearTimeout(this.timeoutId)
    }
  }
}
```

#### 2. 使用示例

```typescript
import { SseClient } from '@/utils/sse/SseClient'

// 创建 SSE 客户端
const sseClient = new SseClient({
  url: '/api/chat/stream',
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`
  },
  body: {
    message: content,
    conversationId: conversationId.value || null
  },
  timeout: 60000,

  onOpen: () => {
    console.log('[SSE] 连接已打开')
  },

  onContent: (text: string) => {
    fullContent += text
    aiMsg.content = fullContent
    scrollToBottom()
  },

  onAudioUrl: (url: string) => {
    aiMsg.streamAudioUrl = url
    console.log('[SSE] audio_url:', url)
  },

  onTokenUsage: (usage) => {
    console.log('[SSE] token_usage:', usage)
  },

  onError: (error: string) => {
    console.error('[SSE] error:', error)
    ElMessage.error(error)
  },

  onDone: () => {
    console.log('[SSE] done')
    doneReceived = true
  },

  onClose: () => {
    console.log('[SSE] 连接已关闭')
    loading.value = false
  }
})

// 开始流式接收
await sseClient.stream()

// 如需中断，可以调用
// sseClient.abort()
```

### 统一入口原则

| 层级 | 职责 | 输出 |
|------|------|------|
| SseClient | fetch 封装、解析、超时、异常处理 | 回调事件 |
| 业务层 | 实现回调逻辑 | 处理业务 |

**禁止**：
- 业务代码不应直接使用 `fetch` + `ReadableStream`
- 应通过 `SseClient` 统一处理所有 SSE 流

### UX 实现规范

#### 1. SseClient 增强接口

```typescript
// 连接状态类型
export type ConnectionStatus = 'idle' | 'connecting' | 'receiving' | 'done' | 'error'

export interface SseClientOptions {
  // ... 现有回调

  // 新增状态回调
  onConnecting?: () => void      // 刚发起连接（开始显示加载）
  onFirstContent?: () => void   // 收到首个 content 事件（关闭骨架屏）
}

export class SseClient {
  private status: ConnectionStatus = 'idle'

  getStatus(): ConnectionStatus {
    return this.status
  }
}
```

#### 2. 业务层状态管理

```typescript
const chatState = ref({
  status: 'idle' as ConnectionStatus,
  streamingContent: '',
  errorMessage: ''
})

const sseClient = new SseClient({
  url: '/api/chat/stream',
  // ...

  onConnecting: () => {
    chatState.value.status = 'connecting'
    chatState.value.streamingContent = ''
    chatState.value.errorMessage = ''
  },

  onFirstContent: () => {
    chatState.value.status = 'receiving'
  },

  onContent: (text: string) => {
    chatState.value.streamingContent += text
  },

  onDone: () => {
    chatState.value.status = 'done'
  },

  onError: (error: string) => {
    chatState.value.status = 'error'
    chatState.value.errorMessage = error
  }
})
```

#### 3. 模板实现

```vue
<div class="message-bubble">
  <!-- connecting: 骨架屏 -->
  <template v-if="chatState.status === 'connecting'">
    <div class="skeleton-loader">
      <el-skeleton :rows="3" animated />
      <span class="thinking-hint">正在思考...</span>
    </div>
  </template>

  <!-- receiving: 流式内容 + 光标 -->
  <template v-else-if="chatState.status === 'receiving'">
    <div class="streaming-content">
      {{ chatState.streamingContent }}
      <span class="typing-cursor">|</span>
    </div>
  </template>

  <!-- done: 最终内容 -->
  <template v-else-if="chatState.status === 'done'">
    <div class="final-content">
      {{ chatState.streamingContent }}
    </div>
  </template>

  <!-- error: 错误提示 -->
  <template v-else-if="chatState.status === 'error'">
    <div class="error-state">
      <el-alert type="error" :title="chatState.errorMessage" show-icon />
      <el-button @click="retry" type="primary" size="small">重试</el-button>
    </div>
  </template>
</div>
```

#### 4. CSS 动画

```css
.typing-cursor {
  animation: blink 1s infinite;
  color: #409eff;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.thinking-hint {
  color: #909399;
  font-size: 12px;
  margin-top: 8px;
}

.skeleton-loader {
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}
```

---

## 安卓端统一 SSE 处理层

### 架构设计

安卓端采用 **SseClient 封装 EventSourceListener + 事件解析 + 异常处理** 的统一架构：

```
┌─────────────────────────────────────────────────────────────┐
│                    SseClient (统一入口)                       │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  - OkHttp EventSource 封装                            │   │
│  │  - EventSourceListener 统一实现                       │   │
│  │  - 事件解析 (parseEvent)                              │   │
│  │  - 异常处理 (onFailure, onClosed)                     │   │
│  │  - Flow 发射 (trySend)                               │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              ↓
                    ┌───────────────────────┐
                    │    业务层调用示例        │
                    │  ChatRepository.stream() │
                    └───────────────────────┘
```

### 核心组件

#### 1. StreamEvent - 统一事件封装

**文件**: `data/repository/StreamEvent.kt`

```kotlin
package com.mrshudson.android.data.repository

/**
 * SSE 流式事件统一封装
 */
sealed class StreamEvent {
    abstract val conversationId: Long?

    /** AI 增量内容事件 */
    data class Content(
        override val conversationId: Long?,
        val text: String
    ) : StreamEvent()

    /** 语音合成完成事件 */
    data class AudioUrl(
        override val conversationId: Long?,
        val url: String
    ) : StreamEvent()

    /** Token 使用统计事件 */
    data class TokenUsage(
        override val conversationId: Long?,
        val inputTokens: Int,
        val outputTokens: Int,
        val duration: Long,
        val model: String
    ) : StreamEvent()

    /** 工具调用事件 */
    data class ToolCall(
        override val conversationId: Long?,
        val id: String,
        val name: String,
        val arguments: String
    ) : StreamEvent()

    /** 工具执行结果事件 */
    data class ToolResult(
        override val conversationId: Long?,
        val id: String,
        val name: String,
        val result: String
    ) : StreamEvent()

    /** 缓存命中事件 */
    data class CacheHit(
        override val conversationId: Long?,
        val content: String
    ) : StreamEvent()

    /** 澄清提示事件 */
    data class Clarification(
        override val conversationId: Long?,
        val content: String
    ) : StreamEvent()

    /** 流式完成事件 */
    data class Done(
        override val conversationId: Long?
    ) : StreamEvent()

    /** 错误事件 */
    data class Error(
        override val conversationId: Long?,
        val message: String
    ) : StreamEvent()
}
```

#### 2. SseClient - SSE 统一客户端

**文件**: `data/repository/SseClient.kt`

```kotlin
package com.mrshudson.android.data.repository

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.*

/**
 * SSE 流式客户端
 *
 * 统一处理所有 SSE 流式请求，包含：
 * - EventSource 封装
 * - 事件解析
 * - 异常处理
 * - 超时保护
 */
class SseClient(
    private val okHttpClient: OkHttpClient,
    private val baseUrl: String
) {
    companion object {
        private const val TAG = "SseClient"
        private const val SSE_TIMEOUT_MS = 60000L // 60秒超时
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
                RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    requestBody
                )
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

            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.d(TAG, "SSE 连接打开")
                lastEventTime = System.currentTimeMillis()
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

                if (!closed) {
                    closed = true
                    trySend(StreamEvent.Error(null, errorMsg))
                    close()
                }
            }
        }

        val eventSource = factory.newEventSource(request, eventSourceListener)

        // 启动超时检测
        val timeoutCheckRunnable = object : Runnable {
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
                    eventSource咖啡?.let {
                        android.os.Handler(it.looper).postDelayed(this, 5000)
                    }
                }
            }
        }

        // 使用主线程 Handler 调度超时检测
        android.os.Handler(android.os.Looper.getMainLooper())
            .postDelayed(timeoutCheckRunnable, 5000)

        awaitClose {
            Log.d(TAG, "SSE 清理资源")
            if (!closed) {
                closed = true
                eventSource.cancel()
            }
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

        // 注意：conversationId 需要从外部传入，这里简化处理
        val convId: Long? = null

        return when (type) {
            "content" -> StreamEvent.Content(
                convId,
                obj.get("text")?.asString ?: obj.get("content")?.asString ?: ""
            )

            "audio_url" -> StreamEvent.AudioUrl(
                convId,
                obj.get("url")?.asString ?: ""
            )

            "token_usage" -> StreamEvent.TokenUsage(
                convId,
                obj.get("inputTokens")?.asInt ?: 0,
                obj.get("outputTokens")?.asInt ?: 0,
                obj.get("duration")?.asLong ?: 0L,
                obj.get("model")?.asString ?: "unknown"
            )

            "tool_call" -> {
                val toolCall = obj.getAsJsonObject("toolCall")
                if (toolCall != null) {
                    StreamEvent.ToolCall(
                        convId,
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
                        convId,
                        toolResult.get("id")?.asString ?: "",
                        toolResult.get("name")?.asString ?: "",
                        toolResult.get("result")?.asString ?: ""
                    )
                } else null
            }

            "cache_hit" -> StreamEvent.CacheHit(
                convId,
                obj.get("content")?.asString ?: ""
            )

            "clarification" -> StreamEvent.Clarification(
                convId,
                obj.get("content")?.asString ?: ""
            )

            "error" -> StreamEvent.Error(
                convId,
                obj.get("message")?.asString ?: "Unknown error"
            )

            "done" -> StreamEvent.Done(convId)

            else -> null
        }
    }
}
```

#### 3. 使用示例

**文件**: `data/repository/ChatRepositoryImpl.kt`

```kotlin
// 之前
private fun createEventSourceListener(): EventSourceListener {
    return object : EventSourceListener() {
        // ... 大量重复代码
    }
}

// 之后：使用 SseClient
fun streamMessage(message: String, conversationId: Long?): Flow<StreamEvent> {
    val requestBody = Gson().toJson(
        SendMessageRequest(message = message, conversationId = conversationId)
    )

    return sseClient.stream(
        endpoint = "chat/stream",
        requestBody = requestBody,
        headers = mapOf("Authorization" to "Bearer $token")
    )
}
```

### 统一入口原则

| 层级 | 职责 | 输出 |
|------|------|------|
| SseClient | EventSource 封装、解析、超时、异常处理 | Flow<StreamEvent> |
| 业务层 | 调用 sseClient.stream() | 处理事件 |

**禁止**：
- 业务代码不应直接实现 `EventSourceListener`
- 应通过 `SseClient.stream()` 统一处理所有 SSE 流

### 异常处理规范

| 异常场景 | 处理方式 | 事件 |
|----------|----------|------|
| 正常完成 | 收到 `done` 事件 | `StreamEvent.Done` |
| 连接正常关闭但无 done | 发送警告，发送 `Done` | `StreamEvent.Done` |
| 超时（60秒无数据） | 取消连接，发送 Error | `StreamEvent.Error` |
| 网络错误 | `onFailure` 回调 | `StreamEvent.Error` |
| JSON 解析失败 | 跳过该事件 | - |

### UX 实现规范

#### 1. 连接状态 Sealed Class

```kotlin
sealed class ConnectionState {
    object Idle : ConnectionState()
    object Connecting : ConnectionState()
    data class Receiving(val content: String = "") : ConnectionState()
    data class Done(val content: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
```

#### 2. ViewModel 集成

```kotlin
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    fun sendMessage(message: String) {
        _connectionState.value = ConnectionState.Connecting

        viewModelScope.launch {
            chatRepository.streamMessage(message, conversationId)
                .onEach { event ->
                    when (event) {
                        is StreamEvent.Content -> {
                            val current = (_connectionState.value as? ConnectionState.Receiving)?.content ?: ""
                            _connectionState.value = ConnectionState.Receiving(current + event.text)
                        }
                        is StreamEvent.Done -> {
                            val content = (_connectionState.value as? ConnectionState.Receiving)?.content ?: ""
                            _connectionState.value = ConnectionState.Done(content)
                        }
                        is StreamEvent.Error -> {
                            _connectionState.value = ConnectionState.Error(event.message)
                        }
                        else -> { /* 忽略其他事件 */ }
                    }
                }
                .catch { e ->
                    _connectionState.value = ConnectionState.Error(e.message ?: "未知错误")
                }
                .collect()
        }
    }
}
```

#### 3. Compose UI

```kotlin
@Composable
fun ChatScreen(viewModel: ChatViewModel = hiltViewModel()) {
    val connectionState by viewModel.connectionState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = connectionState) {
            is ConnectionState.Connecting -> {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在思考...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            is ConnectionState.Receiving -> {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = state.content, style = MaterialTheme.typography.bodyLarge)
                    Text("|", color = MaterialTheme.colorScheme.primary)
                }
            }

            is ConnectionState.Done -> {
                Text(text = state.content, style = MaterialTheme.typography.bodyLarge)
            }

            is ConnectionState.Error -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )) {
                        Text(text = state.message, modifier = Modifier.padding(16.dp))
                    }
                    Button(onClick = { viewModel.retry() }) { Text("重试") }
                }
            }

            else -> { /* Idle */ }
        }
    }
}
```

---

## 数据库存储规范

### audio_url 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `audio_url` | VARCHAR(500) | TTS 生成音频的 URL |

### 保存时机

`audio_url` 应在 AI 响应生成完成后、保存到数据库时一并存储：

```java
String audioUrl = voiceService.textToSpeech(finalContent);
saveAssistantMessage(userId, conversationId, finalContent, functionCallJson, audioUrl);
```

---

## 通用 UX 规范

### 连接状态定义

#### 状态枚举

| 状态 | 说明 | 前端 UI | 安卓端 UI |
|------|------|---------|-----------|
| `idle` | 初始状态 | 隐藏消息气泡 | 隐藏消息气泡 |
| `connecting` | 正在连接 | 骨架屏 + "正在思考..." | CircularProgressIndicator |
| `receiving` | 正在接收内容 | 打字动画 + 光标 | 逐字动画 |
| `done` | 完成 | 停止光标 | 停止动画 |
| `error` | 异常 | 错误提示 + 重试按钮 | Error UI + 重试按钮 |

#### 状态转换图

```
┌─────────────────────────────────────────────────────────────────┐
│                         状态转换流程                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   ┌──────┐    send()     ┌────────────┐   首字节    ┌──────────┐ │
│   │ idle │──────────────▶│ connecting │────────────▶│ receiving│ │
│   └──────┘               └────────────┘             └────┬─────┘ │
│       ▲                         │                        │       │
│       │                         │ 超时/错误              │ done  │
│       │                         ▼                        ▼       │
│       │                   ┌──────────┐             ┌─────────┐   │
│       └───────────────────│  error   │◀────────────│   done  │   │
│                           └──────────┘   异常关闭   └─────────┘   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 响应时间阈值

| 阶段 | 最大等待时间 | 用户反馈 |
|------|-------------|----------|
| 首字节到达 | 10 秒 | 持续显示加载动画 |
| 10-30 秒 | - | 显示额外提示（如"AI 正在处理中..."） |
| 30-60 秒 | - | 显示警告（如"响应较慢，请稍候..."） |
| >60 秒 | - | 中断连接，显示超时错误 + 重试按钮 |

### 加载状态文案

| 场景 | 推荐文案 |
|------|----------|
| 连接中 | "正在思考..." |
| 等待首字节 | "正在思考..." |
| 长时间等待 | "AI 正在处理中，可能需要较长时间..." |
| 超时 | "响应超时，请检查网络后重试" |

### 错误处理文案

| 错误类型 | 推荐文案 |
|----------|----------|
| 网络错误 | "网络连接失败，请检查网络后重试" |
| 服务器错误 | "服务暂时不可用，请稍后重试" |
| 超时 | "响应超时，AI 可能卡住了" |
| 认证失败 | "登录已过期，请重新登录" |

### 重试策略

| 策略 | 实现 |
|------|------|
| 自动重试 | 网络错误时可自动重试 1 次 |
| 手动重试 | 服务器错误需用户手动点击重试 |
| 放弃重试 | 认证失败需跳转登录页面 |

### 无障碍（Accessibility）规范

| 要求 | 前端实现 | 安卓端实现 |
|------|---------|-----------|
| 屏幕阅读器 | `aria-live="polite"` 区域 | `contentDescription` 标记 |
| 焦点管理 | 响应完成后聚焦到新消息 | `FocusRequester` 聚焦 |
| 动画控制 | 尊重 `prefers-reduced-motion` | `AnimationSpec` 可配置 |
| 颜色对比度 | WCAG AA 标准（4.5:1） | Material 主题默认支持 |

---

## 调试日志规范

### 后端日志

```java
log.info("SSE 事件: type={}, data={}", eventType, jsonStr);
```

### 前端日志

```typescript
console.log('[DEBUG] SSE事件: type=', data.type, 'data=', data);
```

### 安卓端日志

```kotlin
android.util.Log.d(TAG, "SSE 事件: $data")
```

---

## 常见问题排查

### 问题：前端显示 "SSE解析跳过"

**原因**：后端发送的事件缺少 `data:` 前缀

**排查**：
1. 检查 Controller 层是否使用 `SseFormatter.addSsePrefix()` 统一格式化
2. 检查 Service 层是否错误地添加了 `data:` 前缀

### 问题：audio_url 为 null

**原因**：数据库保存时 audioUrl 参数为 null

**排查**：
1. 检查 `voiceService.textToSpeech()` 是否返回 null
2. 检查 `saveAssistantMessage()` 调用时机
3. 检查讯飞 API 配置是否正确

### 问题：前端解析 JSON 失败

**原因**：后端发送的 JSON 格式不正确

**排查**：
1. 检查 `escapeJson()` 是否正确转义特殊字符
2. 检查是否有双重 `data:` 前缀

---

## 规范变更记录

| 日期 | 版本 | 变更内容 |
|------|------|----------|
| 2026-03-24 | 1.0 | 初始版本，统一 SSE 流式输出格式 |
| 2026-03-24 | 1.1 | 新增后端统一 SSE 格式化组件 `SseFormatter` 和 `SseEvent` |
| 2026-03-25 | 1.2 | 新增用户体验（UX）规范，定义连接状态、加载动画、流式显示标准 |

---

## 附录：后端组件 API

### SseFormatter

```java
public final class SseFormatter {
    // 格式化
    static String format(SseEvent event)           // SseEvent → SSE 字符串
    static String format(String jsonStr)            // JSON → SSE 字符串（添加前缀）
    static String toJson(SseEvent event)           // SseEvent → JSON 字符串

    // Flux 转换
    static Flux<String> addSsePrefix(Flux<String>)  // Flux JSON → Flux SSE

    // 便捷构建方法（返回 SSE 字符串）
    static String content(String text)
    static String audioUrl(String url)
    static String tokenUsage(int input, int output, long duration, String model)
    static String toolCall(String name, String arguments)
    static String toolResult(String name, String result)
    static String cacheHit(String content)
    static String clarification(String content)
    static String error(String message)
    static String done()
}
```

### SseEvent

```java
@Data @Builder
public class SseEvent {
    String type;
    String text;
    String content;
    String url;
    String message;
    ToolCallInfo toolCall;
    ToolResultInfo toolResult;
    TokenUsageInfo tokenUsage;

    // 工厂方法
    static SseEvent content(String text)
    static SseEvent audioUrl(String url)
    static SseEvent tokenUsage(int input, int output, long duration, String model)
    // ...
}
```

---

**文档版本**: 1.2
**最后更新**: 2026-03-25
