# SSE 流式响应 + TTS 异步化方案

**文档版本**: 1.0
**创建日期**: 2026-03-25
**最后更新**: 2026-03-25
**文档状态**: 草稿

---

## 一、概述

### 1.1 文档目的

本文档定义 MrsHudson 项目中 SSE 流式响应与 TTS 语音合成的异步化方案，实现：
- 单 SSE 连接完成全部通信
- `content_done` 标识 AI 文本响应结束
- `audio_done` 标识 TTS 语音合成结束
- TTS 超时保护机制
- 历史会话音频 URL 重获取

### 1.2 核心设计原则

| 原则 | 说明 |
|------|------|
| **单 SSE 连接** | 不引入 WebSocket/双 SSE，仅一条 SSE |
| **content_done** | AI 文本生成完毕，前端可感知内容已全部加载 |
| **audio_done** | TTS 合成完毕，前端展示播放按钮 |
| **content_done 起计时** | 超时从 AI 内容结束开始，TTS 有完整 10s |
| **历史兜底** | 超时不影响，音频已落库，下次加载历史消息获取 |

---

## 二、SSE 事件序列

### 2.1 完整事件序列

```
┌─────────────────────────────────────────────────────────────────┐
│                     SSE 事件序列（正常流程）                        │
└─────────────────────────────────────────────────────────────────┘

data: {"type":"content","text":"今天天气"}\n\n
data: {"type":"content","text":"很不错"}\n\n
data: {"type":"tool_call","toolCall":{...}}\n\n
      ← 工具调用
data: {"type":"tool_result","toolResult":{...}}\n\n
      ← 工具结果
data: {"type":"content","text":"适合出门。"}\n\n
                                                              ↑
                                                    content_done（TTS 开始后台执行）
data: {"type":"content_done"}\n\n

                                                             [TTS 合成中...]
                                                             [2-5秒后完成]

data: {"type":"audio_done","url":"https://..."}\n\n
      ← TTS 完成，展示播放按钮
                                                              ↑
data: {"type":"done"}\n\n
                              ← SSE 连接关闭
```

### 2.2 超时场景

```
data: {"type":"content_done"}\n\n
                                                             [TTS 合成中...]
                                                             [超过 10 秒]

data: {"type":"audio_done","timeout":true}\n\n
      ← 超时不展示按钮
data: {"type":"done"}\n\n
```

### 2.3 失败场景

```
data: {"type":"content_done"}\n\n
                                                             [TTS 合成中...]
                                                             [失败]

data: {"type":"audio_done","error":"TTS_FAILED"}\n\n
      ← 失败，不展示按钮
data: {"type":"done"}\n\n
```

---

## 三、事件类型定义

### 3.1 新增事件类型

| 事件类型 | 说明 | 字段 |
|----------|------|------|
| `content_done` | AI 文本内容结束 | — |
| `audio_done` | TTS 语音合成结束 | `url`: string, `timeout`: boolean, `error`: string |

### 3.2 audio_done 事件字段

```json
// TTS 成功
{"type":"audio_done","url":"https://raw.githubusercontent.com/..."}

// TTS 超时
{"type":"audio_done","timeout":true}

// TTS 失败
{"type":"audio_done","error":"TTS_FAILED"}

// TTS 成功但 URL 为空
{"type":"audio_done","noaudio":true}
```

### 3.3 事件时序说明

| 事件顺序 | type | 说明 |
|---------|------|------|
| 1 | `content` | AI 增量内容，可多次发送 |
| 2 | `tool_call` | 工具调用 |
| 3 | `tool_result` | 工具执行结果 |
| 4 | `content_done` | AI 内容结束，**TTS 后台开始** |
| 5 | `audio_done` | TTS 结束，**前端展示播放按钮** |
| 6 | `done` | SSE 连接关闭 |

---

## 四、后端实现

### 4.1 SseFormatter 新增方法

**文件**: `SseFormatter.java`

```java
public final class SseFormatter {

    // ========== 新增事件 ==========

    /**
     * AI 内容结束
     * 表示 AI 文本已全部生成，TTS 可在后台开始
     */
    public static String contentDone() {
        return "{\"type\":\"content_done\"}";
    }

    /**
     * TTS 语音合成结束
     *
     * @param url 音频 URL，为空表示失败或超时
     * @param timeout 是否超时
     * @param error 错误信息
     */
    public static String audioDone(String url, boolean timeout, String error) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"audio_done\"");
        if (timeout) {
            sb.append(",\"timeout\":true");
        } else if (error != null && !error.isEmpty()) {
            sb.append(",\"error\":\"").append(escapeJson(error)).append("\"");
        } else if (url == null || url.isEmpty()) {
            sb.append(",\"noaudio\":true");
        } else {
            sb.append(",\"url\":\"").append(escapeJson(url)).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    // ========== 保留原有方法 ==========

    public static String content(String text) {
        return "{\"type\":\"content\",\"text\":\"" + escapeJson(text) + "\"}";
    }

    public static String done() {
        return "{\"type\":\"done\"}";
    }

    // ... 其他方法保持不变
}
```

### 4.2 StreamChatService 改造

**文件**: `StreamChatService.java`

```java
// TTS 超时配置（毫秒）
private static final long TTS_TIMEOUT_MS = 10000;

public Flux<String> streamSendMessage(Long userId, SendMessageRequest request) {
    // 1. 缓存命中
    if (cacheResult.isHit()) {
        return Flux.just(SseFormatter.cacheHit(escapeJson(cacheResult.getResponse())));
    }

    // 2. 意图路由直接处理（见第四章）
    if (routeResult.isHandled()) {
        return handleIntentRoute(userId, conversationId, routeResult);
    }

    // 3. LLM 流式响应
    return handleLlmStream(userId, conversationId, request);
}

/**
 * 处理 LLM 流式响应
 */
private Flux<String> handleLlmStream(Long userId, Long conversationId, SendMessageRequest request) {
    AtomicReference<String> finalContent = new AtomicReference("");
    AtomicReference<String> audioUrlRef = new AtomicReference<>();
    Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

    // 订阅 AI 流
    aiStream.subscribe(
        chunk -> {
            finalContent.set(finalContent.get() + chunk);

            if (chunk.startsWith("[TOOL_CALL]")) {
                // 解析并发送工具调用事件
                sink.tryEmitNext(parseToolCall(chunk));
            } else {
                sink.tryEmitNext(SseFormatter.content(escapeJson(chunk)));
            }
        },
        error -> sink.tryEmitError(error),
        () -> {
            // ========== AI 内容流结束 ==========
            log.info("AI 内容流结束，开始处理 TTS");

            // 1. 保存消息（audioUrl 初始为 null）
            String functionCallJson = toolCallInfos.isEmpty() ? null : JSON.toJSONString(toolCallInfos);
            saveAssistantMessage(userId, conversationId,
                finalContent.get(), functionCallJson, null);

            // 2. 发送 content_done
            sink.tryEmitNext(SseFormatter.contentDone());

            // 3. 后台异步执行 TTS（带超时）
            CompletableFuture.runAsync(() -> {
                try {
                    String audioUrl = voiceService.textToSpeech(finalContent.get());
                    audioUrlRef.set(audioUrl);
                } catch (Exception e) {
                    log.error("TTS 合成失败", e);
                    audioUrlRef.set(null);
                }
            }).orTimeout(TTS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
              .thenAccept(result -> {
                  String url = audioUrlRef.get();
                  if (url == null) {
                      // 超时或失败
                      sink.tryEmitNext(SseFormatter.audioDone(null, true, null));
                  } else {
                      // TTS 成功
                      sink.tryEmitNext(SseFormatter.audioDone(url, false, null));
                  }
                  sink.tryEmitNext(SseFormatter.done());
                  sink.tryEmitComplete();
              })
              .exceptionally(ex -> {
                  log.error("TTS 超时或异常", ex);
                  sink.tryEmitNext(SseFormatter.audioDone(null, true, null));
                  sink.tryEmitNext(SseFormatter.done());
                  sink.tryEmitComplete();
                  return null;
              });
        }
    );

    return sink.asFlux();
}

/**
 * 处理意图路由（异步 TTS）
 */
private Flux<String> handleIntentRoute(Long userId, Long conversationId, RouteResult routeResult) {
    String response = routeResult.getResponse();
    saveAssistantMessage(userId, conversationId, response, null, null);

    Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

    // 1. 发送内容
    sink.tryEmitNext(SseFormatter.content(escapeJson(response)));
    sink.tryEmitNext(SseFormatter.contentDone());

    // 2. 后台 TTS（带超时）
    CompletableFuture.runAsync(() -> {
        try {
            String audioUrl = voiceService.textToSpeech(response);
            sink.tryEmitNext(SseFormatter.audioDone(audioUrl, false, null));
        } catch (Exception e) {
            log.error("意图路由 TTS 失败", e);
            sink.tryEmitNext(SseFormatter.audioDone(null, false, "TTS_FAILED"));
        }
        sink.tryEmitNext(SseFormatter.done());
        sink.tryEmitComplete();
    }).orTimeout(TTS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
      .exceptionally(ex -> {
          sink.tryEmitNext(SseFormatter.audioDone(null, true, null));
          sink.tryEmitNext(SseFormatter.done());
          sink.tryEmitComplete();
          return null;
      });

    return sink.asFlux();
}
```

### 4.3 意图路由同步问题

**问题**：原意图路由 TTS 同步阻塞，导致快速响应优势被抵消。

| 阶段 | 同步处理耗时 | 用户感知 |
|------|-------------|---------|
| 意图路由判断 | ~50ms | 快 |
| 生成响应文本 | ~100ms | 快 |
| **TTS 语音合成** | **2-5s** | **慢！** |

**解决方案**：意图路由也走异步 TTS 流程（见上方 `handleIntentRoute` 方法）。

---

## 五、超时机制

### 5.1 计时起点

**从 `content_done` 开始计时**，而非请求开始。

```
用户请求 ──► AI 生成 ──► content_done ──► TTS 合成 ──► audio_done
             │              │              │
             │              │              └─────── 10s 超时保护
             │              │
             │              └─────────────────── 计时起点
             │
             └────────────────────────────────── 不计入 TTS 超时
```

### 5.2 超时容忍度分析

| TTS 提供商 | 典型耗时 | 10s 超时 | 5s 超时 |
|-----------|---------|---------|---------|
| 讯飞 | 2-5s | ✅ 宽松 | ⚠️ 紧张 |
| MiniMax | <250ms | ✅✅ 绰绰有余 | ✅✅ 绰绰有余 |

### 5.3 超时处理流程

```
content_done 发送
       │
       ▼
计时开始（10s）
       │
       ├── TTS 在 10s 内完成 ──► 正常发送 audio_done
       │
       └── TTS 超过 10s ─────► 发送 audio_done(timeout=true)
                                    │
                                    ├── 数据库已有 audioUrl → 下次加载获取
                                    └── 数据库无 audioUrl → 无播放按钮
```

---

## 六、历史会话重获取

### 6.1 重获取机制

即使 SSE 超时，消息已保存到数据库，音频 URL 已写入。下次加载历史消息时可获取。

```
SSE 流程：
content_done ──► TTS 后台合成 ──► audio_done（可能超时）
                                    │
                                    ▼
                            消息已保存到数据库
                            audioUrl 已写入

SSE 超时，但数据库有值：
    │
    ▼
前端下次加载历史消息
    │
    ├── /api/conversations → 获取 audioUrl
    └── /api/messages/{id} → 获取 audioUrl
```

### 6.2 后端新增接口

```java
// MessageController.java
@GetMapping("/messages/{messageId}/audio-url")
public Result<String> getMessageAudioUrl(@PathVariable Long messageId) {
    ChatMessage message = chatMessageMapper.selectById(messageId);
    if (message == null) {
        return Result.fail("消息不存在");
    }
    return Result.success(message.getAudioUrl());
}
```

### 6.3 前端重获取实现

```typescript
// ChatRoom.vue
onDone: () => {
    // 检查最后一条消息是否缺少 audioUrl
    const lastMsg = getLastAssistantMessage()
    if (lastMsg && !lastMsg.audioUrl) {
        // SSE 流程没有拿到 audioUrl，尝试从历史重新获取
        // 注意：这里不需要立即获取，下次加载会话时会自动获取
        console.log('[SSE] done，audioUrl 将在下次加载会话时获取')
    }
}
```

**说明**：前端不需要主动重获取，下次加载会话列表或消息时会自动从数据库获取 audioUrl。

---

## 七、前端实现

### 7.1 SseClient 新增事件

**文件**: `src/utils/sse/SseClient.ts`

```typescript
export interface SseClientOptions {
  // ... 原有选项

  onContentDone?: () => void                    // 新增
  onAudioDone?: (event: AudioDoneEvent) => void // 新增
}

export interface AudioDoneEvent {
  url: string | null     // 音频 URL
  timeout: boolean        // 是否超时
  error: string | null    // 错误信息
}

class SseClient {
  private handleMessage(data: any): void {
    switch (data.type) {
      // ... 其他 case

      case 'content_done':
        this.options.onContentDone?.()
        break

      case 'audio_done':
        this.options.onAudioDone?.({
          url: data.url || null,
          timeout: data.timeout || false,
          error: data.error || null
        })
        break

      case 'done':
        this.options.onDone?.()
        break
    }
  }
}
```

### 7.2 Message 接口更新

```typescript
interface Message {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  audioUrl?: string | null
  ttsStatus: TtsStatus
  createdAt: string
}

type TtsStatus = 'pending' | 'synthesizing' | 'ready' | 'timeout' | 'error'
```

### 7.3 ChatRoom 状态管理

```typescript
// SSE 事件处理
const sseClient = new SseClient({
  url: '/api/chat/stream',
  method: 'POST',

  onContent: (text) => {
    // 增量更新
  },

  onContentDone: () => {
    // AI 内容接收完毕
    const lastMsg = getLastAssistantMessage()
    if (lastMsg) {
      lastMsg.ttsStatus = 'synthesizing'  // 开始合成 TTS
      console.log('[SSE] 内容接收完毕，等待 TTS...')
    }
  },

  onAudioDone: (event: AudioDoneEvent) => {
    const lastMsg = getLastAssistantMessage()
    if (!lastMsg) return

    if (event.timeout) {
      lastMsg.ttsStatus = 'timeout'
      lastMsg.audioUrl = null
      console.log('[SSE] TTS 超时，不展示播放按钮')
    } else if (event.error) {
      lastMsg.ttsStatus = 'error'
      lastMsg.audioUrl = null
      console.log('[SSE] TTS 失败:', event.error)
    } else if (event.url) {
      lastMsg.ttsStatus = 'ready'
      lastMsg.audioUrl = event.url
      console.log('[SSE] TTS 完成:', event.url)
    }
  },

  onDone: () => {
    // 全部完成
  }
})
```

### 7.4 模板实现

```html
<template>
  <div v-for="msg in messages" :key="msg.id">
    <div class="message-bubble" v-html="formatMessage(msg.content)"></div>

    <!-- 语音播放按钮：仅 TTS 完成且有 URL 时显示 -->
    <div v-if="showAudioButton(msg)" class="audio-player">
      <el-button @click="toggleAudio(msg)" circle>
        {{ msg.isPlaying ? '暂停' : '播放' }}
      </el-button>
      <el-button v-if="msg.hasPaused && !msg.isPlaying" @click="resumeAudio(msg)">
        继续
      </el-button>
    </div>

    <!-- TTS 合成中状态提示（可选） -->
    <div v-if="msg.ttsStatus === 'synthesizing'" class="tts-status">
      <span class="tts-loading">🔊 语音合成中...</span>
    </div>
  </div>
</template>

<script setup lang="ts">
const showAudioButton = (msg: Message) => {
  return msg.role === 'assistant' && msg.ttsStatus === 'ready' && msg.audioUrl
}
</script>
```

### 7.5 TTS 状态说明

| ttsStatus | 显示内容 | 说明 |
|-----------|---------|------|
| `pending` | 无 | AI 还在生成内容 |
| `synthesizing` | 🔊 语音合成中... | content_done 已收到，等待 audio_done |
| `ready` | 播放按钮 | audio_done 收到，有 URL |
| `timeout` | 无 | 超时不展示播放按钮 |
| `error` | 无 | 失败不展示播放按钮 |

---

## 八、Android 实现

### 8.1 StreamEvent 新增类型

**文件**: `data/repository/StreamEvent.kt`

```kotlin
sealed class StreamEvent {
    // ... 原有事件

    /** AI 内容结束事件 */
    data class ContentDone(
        override val conversationId: Long?
    ) : StreamEvent()

    /** TTS 语音合成结束事件 */
    data class AudioDone(
        override val conversationId: Long?,
        val url: String?,
        val timeout: Boolean,
        val error: String?
    ) : StreamEvent()
}
```

### 8.2 SSE 解析更新

```kotlin
private fun parseEvent(data: String): StreamEvent? {
    if (data.isBlank()) return null

    val obj = JsonParser.parseString(data).asJsonObject
    val type = obj.get("type")?.asString ?: return null
    val convId: Long? = null

    return when (type) {
        // ... 其他 case

        "content_done" -> StreamEvent.ContentDone(convId)

        "audio_done" -> StreamEvent.AudioDone(
            conversationId = convId,
            url = obj.optString("url").takeIf { it.isNotBlank() },
            timeout = obj.optBoolean("timeout"),
            error = obj.optString("error").takeIf { it.isNotBlank() }
        )

        else -> null
    }
}
```

### 8.3 Message 状态

```kotlin
data class Message(
    val id: Long,
    val role: String,
    val content: String,
    val audioUrl: String? = null,
    val ttsStatus: TtsStatus = TtsStatus.PENDING
)

enum class TtsStatus {
    PENDING,        // AI 还在生成
    SYNTHESIZING,   // content_done 已收到
    READY,          // audio_done 收到，有 URL
    TIMEOUT,        // 超时
    ERROR           // 失败
}
```

### 8.4 ViewModel 处理

```kotlin
@Composable
fun ChatViewModel(
    private val chatRepository: ChatRepository
) {
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    fun onContentDone() {
        val lastMsg = _messages.value.lastOrNull { it.role == "assistant" }
        lastMsg?.let {
            _messages.update { msgs ->
                msgs.map { msg ->
                    if (msg.id == lastMsg.id) msg.copy(ttsStatus = TtsStatus.SYNTHESIZING)
                    else msg
                }
            }
        }
    }

    fun onAudioDone(url: String?, timeout: Boolean, error: String?) {
        val lastMsg = _messages.value.lastOrNull { it.role == "assistant" }
        lastMsg?.let {
            _messages.update { msgs ->
                msgs.map { msg ->
                    if (msg.id == lastMsg.id) {
                        msg.copy(
                            audioUrl = url,
                            ttsStatus = when {
                                timeout -> TtsStatus.TIMEOUT
                                error != null -> TtsStatus.ERROR
                                url != null -> TtsStatus.READY
                                else -> TtsStatus.ERROR
                            }
                        )
                    } else msg
                }
            }
        }
    }
}
```

### 8.5 UI 显示

```kotlin
@Composable
fun AudioPlayButton(message: Message) {
    when (message.ttsStatus) {
        TtsStatus.READY -> {
            // 显示播放按钮
            IconButton(onClick = { /* 播放 */ }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "播放")
            }
        }
        TtsStatus.SYNTHESIZING -> {
            // 可选：显示合成中状态
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("语音合成中...", fontSize = 12.sp)
            }
        }
        TtsStatus.TIMEOUT, TtsStatus.ERROR -> {
            // 不显示任何内容
        }
        else -> { }
    }
}
```

---

## 九、状态机转换

```
                    ┌─────────────────────────────────────────────────────┐
                    │                  AI 内容流                           │
                    └─────────────────────────────────────────────────────┘
                                         │
                                         ▼
                             ┌───────────────────────────────┐
                             │  ttsStatus = PENDING          │
                             │  content 事件持续增量更新       │
                             └───────────────────────────────┘
                                         │
                                         ▼
                              ┌──────────────────────┐
                              │   content_done 收到   │
                              │   ttsStatus =        │
                              │   SYNTHESIZING       │
                              └──────────────────────┘
                                         │
                                         ▼
                         ┌───────────────────────────────┐
                         │      TTS 后台合成中...        │
                         │      等待 audio_done          │
                         │      超时计时：10s            │
                         └───────────────────────────────┘
                              /            |            \
                             /             |             \
                            /              |              \
               ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
               │ audio_done   │  │ audio_done   │  │ audio_done   │
               │ url 有值     │  │ timeout=true │  │ error=xxx    │
               └──────────────┘  └──────────────┘  └──────────────┘
                      │                  │                 │
                      ▼                  ▼                 ▼
               ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
               │ ttsStatus =  │  │ ttsStatus =  │  │ ttsStatus =  │
               │ READY        │  │ TIMEOUT      │  │ ERROR        │
               │ 显示播放按钮  │  │ 不显示按钮    │  │ 不显示按钮    │
               └──────────────┘  └──────────────┘  └──────────────┘
                      │                  │                 │
                      └──────────────────┼─────────────────┘
                                         ▼
                              ┌──────────────────────┐
                              │       done 收到      │
                              │    SSE 连接关闭      │
                              └──────────────────────┘
```

---

## 十、与现有 SSE_STREAM_SPEC.md 的关系

### 10.1 兼容性说明

本文档是 `SSE_STREAM_SPEC.md` 的**增量补充**，而非替换：

| 项目 | 说明 |
|------|------|
| 新增事件 | `content_done`、`audio_done` |
| 事件顺序 | 新增事件在 `done` 之前 |
| 原有事件 | 保持不变 |
| TTS 处理 | 从同步改为异步 |

### 10.2 需要修改的内容

| 文件 | 修改内容 |
|------|---------|
| `SseFormatter.java` | 新增 `contentDone()`、`audioDone()` 方法 |
| `StreamChatService.java` | 使用 `Sinks.Many` 重构，TTS 异步化 |
| `SseClient.ts` | 新增 `onContentDone`、`onAudioDone` 回调 |
| `StreamEvent.kt` | 新增 `ContentDone`、`AudioDone` 类型 |
| 前端/Android | 新增 TTS 状态管理和 UI |

---

## 十一、变更记录

| 日期 | 版本 | 变更内容 |
|------|------|---------|
| 2026-03-25 | 1.0 | 初始版本，SSE + TTS 异步化方案 |

---

**文档版本**: 1.0
**最后更新**: 2026-03-25
