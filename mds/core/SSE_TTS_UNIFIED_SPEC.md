# SSE 流式响应 + TTS 语音合成统一规范

**文档版本**: 2.4
**创建日期**: 2026-03-24
**最后更新**: 2026-03-28
**文档状态**: 正式版

---

## 一、概述

### 1.1 文档目的

本文档定义 MrsHudson 项目中 SSE 流式响应与 TTS 语音合成的完整方案，实现：

- 单 SSE 连接完成全部通信，不引入 WebSocket 或双 SSE
- `thinking` 事件流式传输 AI 思考推理过程（可选，模型不支持时不发送）
- `content_done` 标识 AI 文本响应结束
- `audio_done` 标识 TTS 语音合成结束
- TTS 超时保护机制（10 秒）
- 历史会话音频 URL 重获取
- 统一语音播放规范（前端/Android 只负责播放）
- 暂停/继续/从头播放控制

### 1.2 核心设计原则

| 原则 | 说明 |
|------|------|
| **单 SSE 连接** | 不引入 WebSocket/双 SSE，仅一条 SSE |
| **thinking 与 content 分离** | 思考过程通过独立事件类型传输，不混入正式回复 |
| **thinking 可选** | 模型不返回 reasoning_content 时不发送 thinking 事件，前端/Android 兼容两种情况 |
| **content_done** | AI 文本生成完毕，前端可感知内容已全部加载 |
| **audio_done** | TTS 合成完毕，前端/Android 展示播放按钮 |
| **content_done 起计时** | 超时从 AI 内容结束开始，TTS 有完整 10s |
| **历史兜底** | 超时不影响，音频已落库，下次加载历史消息获取 |
| **后端统一 TTS** | 后端负责 TTS，前端/Android 只负责播放 |
| **无重试机制** | TTS 失败不重试，避免增加 4-10s 延迟 |

### 1.3 技术选型

| 组件 | 技术方案 | 备注 |
|------|---------|------|
| AI 模型（默认） | MiniMax M2.7 | 支持 `delta.reasoning_content` |
| AI 模型（备选） | Kimi moonshot-v1-8k | 普通模型，不输出思考过程 |
| AI 模型（备选） | Kimi kimi-k2-thinking | 思考模型，支持 `delta.reasoning_content` |
| ASR 提供商 | 讯飞语音 SDK (xfyun) | `IatClient` |
| TTS 提供商 | **策略模式切换** | 讯飞 / MiniMax |
| 流式协议 | SSE (Server-Sent Events) | |
| 前端播放 | HTML5 `<audio>` | 只读播放 |
| Android 播放 | MediaPlayer | 可选本地 TTS 降级 |

### 1.4 多模型兼容说明

后端通过 `ai.provider` 配置切换 AI 提供商，两个客户端遵循相同的输出约定：

| 提供商 | 客户端 | reasoning_content | 流式字段 |
|--------|--------|-------------------|---------|
| MiniMax M2.7 | `MiniMaxClient` | `delta.reasoning_content` | `delta.content` |
| Kimi moonshot-v1-8k | `KimiClient` | 无（普通模型不输出思考） | `delta.content` |
| Kimi kimi-k2-thinking | `KimiClient` | `delta.reasoning_content` | `delta.content` |

两个客户端均将 `reasoning_content` 转为 `[THINKING]` 前缀标记，`StreamChatService` 统一处理，上层逻辑无需感知具体提供商。


---

## 二、SSE 格式标准

### 2.1 基本格式

所有 SSE 事件必须遵循标准格式：

```
data: {"type":"<event_type>","..."}\n\n
```

- 每条消息以 `data: ` 开头
- 消息体为 JSON 对象
- 以两个换行符 `\n\n` 结尾

### 2.2 错误示例

```
{"type":"content","text":"Hello"}  ❌ 缺少 data: 前缀
data:data: {"type":"content",...}  ❌ 双重前缀
data: {"type":"content"} \n      ❌ 单换行符
```

### 2.3 正确示例

```
data: {"type":"thinking","text":"用户问的是...","conversationId":1,"messageId":2}\n\n
data: {"type":"content","text":"Hello","conversationId":1,"messageId":2}\n\n
data: {"type":"content_done","conversationId":1,"messageId":2}\n\n
data: {"type":"audio_done","url":"https://...","conversationId":1,"messageId":2}\n\n
data: {"type":"done"}\n\n
```

---

## 三、事件类型定义

### 3.1 完整事件类型列表

| 事件类型 | 说明 | 字段 |
|----------|------|------|
| `thinking` | AI 思考推理过程（增量） | `text`: string, `conversationId`: number, `messageId`: number |
| `content` | AI 正式回复内容（增量） | `text`: string, `conversationId`: number, `messageId`: number |
| `content_done` | AI 文本内容结束 | `conversationId`: number, `messageId`: number |
| `audio_done` | TTS 语音合成结束 | `conversationId`: number, `messageId`: number, `url`: string\|null, `timeout`: boolean, `error`: string\|null, `noaudio`: boolean |
| `token_usage` | Token 统计 | `inputTokens`: number, `outputTokens`: number, `duration`: number(ms), `model`: string |
| `tool_call` | 工具调用 | `toolCall`: {`name`: string, `arguments`: string(JSON)} |
| `tool_result` | 工具执行结果 | `toolResult`: {`name`: string, `result`: string} |
| `cache_hit` | 缓存命中 | `content`: string |
| `clarification` | 澄清提示 | `content`: string |
| `error` | 错误信息 | `message`: string, `conversationId`: number (可选) |
| `done` | 流式完成 | `conversationId`: number (可选) |

**各事件 JSON 示例**：

```json
// thinking
{"type":"thinking","text":"用户问的是数学问题，直接回答即可","conversationId":1,"messageId":2}

// content
{"type":"content","text":"1+1 = **2**","conversationId":1,"messageId":2}

// content_done
{"type":"content_done","conversationId":1,"messageId":2}

// audio_done（四种状态见 3.4 节）
{"type":"audio_done","url":"https://example.com/tts/abc.mp3","conversationId":1,"messageId":2}

// token_usage（duration 单位：毫秒；model 为模型 ID 字符串）
{"type":"token_usage","inputTokens":128,"outputTokens":64,"duration":1230,"model":"MiniMax-M2.7"}

// tool_call（arguments 为 JSON 字符串）
{"type":"tool_call","toolCall":{"name":"get_weather","arguments":"{\"city\":\"北京\"}"}}

// tool_result
{"type":"tool_result","toolResult":{"name":"get_weather","result":"北京今天晴，25°C"}}

// cache_hit
{"type":"cache_hit","content":"1+1 = **2**"}

// clarification
{"type":"clarification","content":"请问您想查询哪个城市的天气？"}

// error
{"type":"error","message":"AI 服务暂时不可用，请稍后重试"}

// done
{"type":"done"}
```

> **重要**：
> - `thinking` 事件为**可选**，仅当模型返回 `reasoning_content` 字段时后端才发送，前端/Android 必须兼容无 `thinking` 事件的情况
> - `thinking` 内容**不参与 TTS 合成**，只合成 `content` 部分
> - `thinking` 内容**不保存到数据库**，历史消息不含思考过程
> - 所有消息相关事件（`content`、`content_done`、`audio_done`）必须包含 `conversationId` 和 `messageId` 字段
> - 发送期间前端输入框必须 disable，后端串行处理，不支持并发发送（详见 3.6 节）

### 3.6 并发控制规范

**基本约定**：前端/Android 发送期间输入框 disable，同一用户同一时刻只有一条 SSE 流在运行。

#### 客户端取消职责

| 端 | 触发时机 | 实现方式 |
|---|---------|---------|
| 前端 (Vue) | 切换会话、组件卸载（`onUnmounted`） | `sseClient.abort()` → 内部调用 `AbortController.abort()` |
| Android | 切换会话、退出页面（`onCleared` / `onStop`） | 取消 `streamMessage` 的 coroutine Job，关闭 OkHttp 连接 |

> **不能依赖自然结束**：SSE 连接在 TTS 等待期间可能保持数十秒，切换会话后旧连接必须主动中断，否则旧流的事件会继续触发回调，污染新会话的 UI 状态。

#### 后端取消传播

后端使用响应式取消传播，将客户端断开信号编织进流本身。`cancelSink` 与请求生命周期绑定，通过 `doOnCancel` 在 Controller 层触发，无需 userId 映射：

```java
// StreamChatService.streamSendMessage()
public Flux<String> streamSendMessage(Long userId, SendMessageRequest request) {
    // cancelSink 与本次请求绑定，生命周期与 Flux 相同
    Sinks.One<Void> cancelSink = Sinks.one();

    Flux<String> aiStream = kimiClient.streamChatCompletion(messages, tools)
        .takeUntilOther(cancelSink.asMono());  // 收到取消信号时终止 AI 流

    // 将 cancelSink 暴露给 Controller，通过 Flux 附加属性传递
    // 实际实现：将 cancelSink 存入请求作用域对象，或通过闭包在 doOnCancel 中直接引用
    return buildResponseFlux(aiStream, cancelSink, userId, conversationId, messageId, ...);
}

// 推荐实现：在 Service 内部直接构建含 doOnCancel 的 Flux，不暴露 cancelSink
private Flux<String> buildResponseFlux(Flux<String> aiStream,
        Sinks.One<Void> cancelSink, ...) {
    Sinks.Many<String> mainSink = Sinks.many().unicast().onBackpressureBuffer();

    aiStream.subscribe(
        chunk -> handleChunk(chunk, mainSink, ...),
        error -> { mainSink.tryEmitNext(SseFormatter.error(...)); mainSink.tryEmitComplete(); },
        () -> { /* content_done + TTS 流程 */ }
    );

    return mainSink.asFlux()
        .doOnCancel(() -> {
            log.info("客户端断开，取消 AI 流，userId={}", userId);
            cancelSink.tryEmitEmpty();          // 触发 takeUntilOther，终止 AI 流
            handleClientDisconnect(messageId, contentDoneEmitted.get(), accumulatedContent.get());
        });
}
```

> **关键点**：`cancelSink` 是请求级局部变量，通过闭包在 `doOnCancel` 中直接引用，不需要 userId 映射表。Controller 层无需感知 `cancelSink`，只需在返回的 `Flux` 上追加 `doOnCancel` 即可（见 5.6 节）。

#### 取消后的 TTS 处理

客户端断开后，TTS 的处理取决于 `content_done` 是否已发出：

| 状态 | 含义 | TTS 行为 |
|------|------|---------|
| `content_done` **已发出** | AI 内容已完整生成 | TTS 异步任务继续静默执行，完成后写入数据库；下次加载历史消息可获取 audioUrl |
| `content_done` **未发出** | AI 内容不完整 | 保存已累积的部分内容，不启动 TTS |

```java
private void handleClientDisconnect(Long messageId,
        boolean contentDoneEmitted, String accumulatedContent) {
    if (contentDoneEmitted) {
        // content_done 已发出：accumulatedContent 即为完整 AI 回复
        // TTS 静默合成，结果写 DB，供历史消息使用
        updateAssistantMessageContent(messageId, accumulatedContent, null);
        CompletableFuture.runAsync(() -> {
            String audioUrl = voiceService.textToSpeech(accumulatedContent);
            if (audioUrl != null) updateMessageAudioUrl(messageId, audioUrl);
        });
    } else {
        // content_done 未发出：内容不完整，保存已有部分，不启动 TTS
        if (accumulatedContent != null && !accumulatedContent.isEmpty()) {
            updateAssistantMessageContent(messageId, accumulatedContent, null);
        }
    }
}
```

#### 前端切换会话实现要点

```typescript
// ChatRoom.vue
let currentSseClient: SseClient | null = null

async function sendStreamContent(content: string) {
  const sseClient = new SseClient({ ... })
  currentSseClient = sseClient
  await sseClient.stream()
  currentSseClient = null
}

// 切换会话时中断旧连接
watch(() => conversationId?.value, (newId, oldId) => {
  if (oldId !== newId && currentSseClient) {
    currentSseClient.abort()
    currentSseClient = null
    loading.value = false
    inputDisabled.value = false
  }
  if (newId) loadConversationMessages(newId)
})

// 组件卸载时中断
onUnmounted(() => {
  currentSseClient?.abort()
})
```

#### Android 切换会话实现要点

```kotlin
// ChatViewModel
private var streamJob: Job? = null

fun sendMessageStream(content: String) {
    streamJob?.cancel()  // 取消上一个流（如有）
    streamJob = viewModelScope.launch {
        chatRepository.streamMessage(content, currentConversationId)
            .collect { event -> /* 处理事件 */ }
    }
}

fun selectConversation(conversationId: Long) {
    streamJob?.cancel()  // 切换会话时取消当前流
    streamJob = null
    _uiState.update { it.copy(isSending = false) }
    loadHistory(conversationId)
}

override fun onCleared() {
    super.onCleared()
    streamJob?.cancel()
    audioPlayer.release()
}
```

### 3.2 thinking 事件

```json
{"type":"thinking","text":"用户问的是一个简单的数学问题...","conversationId":123,"messageId":456}
```

- 在 `content` 事件之前发送
- 可多次发送（增量流式）
- 模型不支持思考模式时不发送此事件
- 前端/Android 收到后折叠展示，不影响正式回复气泡

### 3.3 content_done 事件

```json
{"type":"content_done","conversationId":123,"messageId":456}
```

表示 AI 正式回复文本已全部生成，TTS 可在后台开始。

### 3.4 audio_done 事件

```json
// TTS 成功
{"type":"audio_done","conversationId":123,"messageId":456,"url":"https://example.com/audio/xxx.mp3"}

// TTS 超时（超过 10 秒）
{"type":"audio_done","conversationId":123,"messageId":456,"timeout":true}

// TTS 失败
{"type":"audio_done","conversationId":123,"messageId":456,"error":"TTS_FAILED"}

// TTS 成功但返回 URL 为空
{"type":"audio_done","conversationId":123,"messageId":456,"noaudio":true}
```

**各状态处理规则**：

| 字段 | 含义 | 展示行为 | 数据库 audioUrl |
|------|------|----------|----------------|
| `url` 有值 | TTS 成功 | 显示播放按钮 | 已写入 |
| `timeout: true` | 超时，后台继续合成 | 静默，不显示按钮 | 合成完成后异步写入 |
| `error` 有值 | 合成异常 | 静默，不显示按钮 | null |
| `noaudio: true` | 提供商返回空 | 静默，不显示按钮 | null |

> **产品决策**：`timeout`、`error`、`noaudio` 三种状态均**静默处理**，不向用户展示错误提示，不影响文字回复的正常显示。语音是增强功能，失败不应打断对话体验。
>
> `timeout` 与 `noaudio` 的区别：`timeout` 表示后台合成仍在进行，历史消息可能有 URL；`noaudio` 表示合成已完成但无结果，历史消息也不会有 URL。

### 3.5 事件时序说明

正确的事件顺序如下（工具调用发生在正式回复**之前**）：

| 阶段 | type | 说明 | 是否必须 |
|------|------|------|---------|
| **阶段一：思考** | `thinking` | AI 思考推理过程，可多次 | 可选 |
| **阶段二：工具调用**（可多轮） | `tool_call` | 工具调用请求 | 可选 |
| | `tool_result` | 工具执行结果，与 tool_call 一一对应 | 可选 |
| **阶段三：正式回复** | `content` | AI 正式回复，可多次 | 必须（至少一次） |
| **阶段四：结束** | `content_done` | AI 内容结束，**TTS 后台开始**，仅一次 | 必须 |
| | `audio_done` | TTS 结束，**展示播放按钮**，仅一次 | 必须 |
| | `done` | SSE 连接关闭，仅一次 | 必须 |

> 各阶段说明：
> - thinking 可**多次出现**：无工具调用时在阶段一单独出现；有工具调用时可在每轮 tool_call 之前各自出现（即阶段二内每轮工具调用前均可能有 thinking）
> - 无工具调用时：thinking（可选）→ content → content_done → audio_done → done
> - 有工具调用时：[thinking（可选）→ tool_call → tool_result] × N → thinking（可选）→ content → content_done → audio_done → done


---

## 四、SSE 事件序列

### 4.1 完整事件序列（含思考过程）

```
data: {"type":"thinking","text":"用户问的是数学问题...","conversationId":1,"messageId":2}\n\n
data: {"type":"thinking","text":"1+1=2，直接回答即可","conversationId":1,"messageId":2}\n\n
data: {"type":"content","text":"1+1 = **2**","conversationId":1,"messageId":2}\n\n
data: {"type":"content_done","conversationId":1,"messageId":2}\n\n

                                                             [TTS 合成中...]

data: {"type":"audio_done","url":"https://...","conversationId":1,"messageId":2}\n\n
data: {"type":"done"}\n\n
```

### 4.2 不含思考过程（模型不支持时）

```
data: {"type":"content","text":"今天天气","conversationId":1,"messageId":2}\n\n
data: {"type":"content","text":"很不错","conversationId":1,"messageId":2}\n\n
data: {"type":"content_done","conversationId":1,"messageId":2}\n\n

                                                             [TTS 合成中...]

data: {"type":"audio_done","url":"https://...","conversationId":1,"messageId":2}\n\n
data: {"type":"done"}\n\n
```

### 4.3 含工具调用的完整序列

```
// 第一轮：thinking → tool_call → tool_result
data: {"type":"thinking","text":"用户要查天气，需要调用 get_weather 工具","conversationId":1,"messageId":2}\n\n
data: {"type":"tool_call","toolCall":{"name":"get_weather","arguments":"{\"city\":\"北京\"}"}}\n\n
data: {"type":"tool_result","toolResult":{"name":"get_weather","result":"北京今天晴，25°C"}}\n\n

// 第二轮：AI 拿到工具结果后输出正式回复（可能再次 thinking）
data: {"type":"thinking","text":"工具返回了天气数据，整理后回复用户","conversationId":1,"messageId":2}\n\n
data: {"type":"content","text":"北京今天晴天，气温 25°C，适合出门。","conversationId":1,"messageId":2}\n\n
data: {"type":"content_done","conversationId":1,"messageId":2}\n\n
data: {"type":"audio_done","url":"https://...","conversationId":1,"messageId":2}\n\n
data: {"type":"done"}\n\n
```

### 4.4 超时场景

```
data: {"type":"content_done","conversationId":1,"messageId":2}\n\n

                                                             [TTS 超过 10 秒]

data: {"type":"audio_done","timeout":true,"conversationId":1,"messageId":2}\n\n
data: {"type":"done"}\n\n
```

### 4.5 意图路由场景

```
data: {"type":"content","text":"今天天气不错","conversationId":1,"messageId":2}\n\n
data: {"type":"content_done","conversationId":1,"messageId":2}\n\n
data: {"type":"audio_done","url":"https://...","conversationId":1,"messageId":2}\n\n
data: {"type":"done"}\n\n
```

---

## 五、后端实现

### 5.1 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                     Controller 层                            │
│  transform(SseFormatter::addSsePrefix)                       │
│  统一添加 data: 前缀                                          │
└─────────────────────────────────────────────────────────────┘
                              ↑
┌─────────────────────────────────────────────────────────────┐
│                     Service 层                              │
│  SseFormatter.thinking() / .content() / .audioDone()        │
│  构建纯 JSON 字符串，不含 data: 前缀                          │
└─────────────────────────────────────────────────────────────┘
                              ↑
┌─────────────────────────────────────────────────────────────┐
│                  MiniMaxClient / KimiClient                  │
│  parseSseData() 分离 reasoning_content 与 content            │
│  reasoning_content → [THINKING] 前缀标记                     │
│  content → 普通文本                                          │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 核心组件

| 文件 | 职责 |
|------|------|
| `MiniMaxClient.java` | 解析 `delta.reasoning_content` 与 `delta.content`，分别标记 |
| `KimiClient.java` | 同上，改用 `delta` 字段解析（原用 `message` 字段，已修复），兼容思考模型 |
| `SseFormatter.java` | 新增 `thinking()` 方法 |
| `StreamChatService.java` | 识别 `[THINKING]` 标记，发送 thinking 事件 |
| `VoiceService.java` | TTS 只合成 content，不合成 thinking |

### 5.3 AI 客户端解析规则

两个客户端遵循相同的解析约定，输出统一的标记格式供 `StreamChatService` 消费。

**MiniMaxClient.parseSseData()**：

```java
var delta = choice.getJSONObject("delta");
if (delta != null) {
    // 思考过程：加 [THINKING] 前缀
    String reasoning = delta.getString("reasoning_content");
    if (reasoning != null && !reasoning.isEmpty()) {
        return Flux.just("[THINKING]" + reasoning);
    }
    // 正式回复
    String content = delta.getString("content");
    if (content != null && !content.isEmpty()) {
        return Flux.just(content);
    }
}
```

**KimiClient.parseSseData()**（已从 `message` 字段迁移到 `delta` 字段）：

```java
var delta = choice.getJSONObject("delta");
if (delta != null) {
    // 思考过程（kimi-k2-thinking 等思考模型）：加 [THINKING] 前缀
    String reasoning = delta.getString("reasoning_content");
    if (reasoning != null && !reasoning.isEmpty()) {
        return Flux.just("[THINKING]" + reasoning);
    }
    // 正式回复
    String content = delta.getString("content");
    if (content != null && !content.isEmpty()) {
        return Flux.just(content);
    }
    // 工具调用（delta.tool_calls）
    var toolCallsArr = delta.getJSONArray("tool_calls");
    if (toolCallsArr != null && !toolCallsArr.isEmpty()) {
        // 解析并返回 [TOOL_CALL]id:name:arguments
    }
}
// 降级：读 message 字段（非流式兼容）
```

> **注意**：`moonshot-v1-8k` 是普通模型，不输出 `reasoning_content`，`[THINKING]` 标记不会产生，前端/Android 不会显示思考折叠块，行为与无思考模型完全一致。

### 5.4 SseFormatter 新增方法

```java
/**
 * AI 思考推理过程（增量）
 * 仅当模型返回 reasoning_content 时调用
 */
public static String thinking(String text, Long conversationId, Long messageId) {
    return toJson(SseEvent.thinking(text, conversationId, messageId));
}
```

### 5.5 StreamChatService 处理逻辑

主流程和工具调用后的继续流程（`continueAiStream`）均需处理 `[THINKING]` 标记：

```java
// 通用 chunk 处理逻辑（主流程和 continueAiStream 共用）
private void handleChunk(String chunk, Sinks.Many<String> sink,
        AtomicReference<String> aiFinalContent, Long conversationId, Long messageId) {
    if (chunk.startsWith("[TOOL_CALL]")) {
        // 工具调用
        sink.tryEmitNext(SseFormatter.toolCall(...));
    } else if (chunk.startsWith("[THINKING]")) {
        // 思考过程：发送 thinking 事件，不累加到 aiFinalContent
        String thinkingText = chunk.substring("[THINKING]".length());
        // escapeJson 在 Service 层调用，SseFormatter 接收已转义的字符串直接拼接 JSON
        // 职责说明：SseFormatter 负责 JSON 结构拼接，转义由调用方在传入前完成
        sink.tryEmitNext(SseFormatter.thinking(escapeJson(thinkingText), conversationId, messageId));
    } else {
        // 正式回复：累加到 aiFinalContent，发送 content 事件
        aiFinalContent.set(aiFinalContent.get() + chunk);
        sink.tryEmitNext(SseFormatter.content(escapeJson(chunk), conversationId, messageId));
    }
}
```

> **escapeJson 职责说明**：`SseFormatter` 的各方法接收已转义的字符串，直接拼入 JSON 字符串。转义由 Service 层在调用前完成，这是当前的实现约定。如后续重构，可将转义内聚到 `SseFormatter` 内部，调用方传入原始字符串即可。
>
> `aiFinalContent` 只累加正式 `content`，TTS 合成和数据库存储均使用此值，思考过程不参与。`continueAiStream` 中同样调用此方法，确保工具调用后的第二轮思考也被正确路由。

### 5.6 Controller 层实现

```java
@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> streamSendMessage(@Valid @RequestBody SendMessageRequest request) {
    Long userId = authService.getCurrentUser().getId();
    return streamChatService.streamSendMessage(userId, request)
            .transform(SseFormatter::addSsePrefix)
            .doOnSubscribe(s -> log.info("SSE 流式响应开始，userId={}", userId))
            .doOnComplete(() -> log.info("SSE 流式响应完成，userId={}", userId))
            .doOnCancel(() -> log.info("SSE 客户端断开，userId={}（取消传播已由 Service 层 Flux 内部处理）", userId))
            .doOnError(e -> log.error("SSE 流式响应异常，userId={}", userId, e));
}
```

> `doOnCancel` 在 Controller 层仅做日志记录。实际的取消传播（终止 AI 流、TTS 静默兜底）由 `StreamChatService` 内部构建的 `Flux` 通过 `doOnCancel` + `cancelSink.tryEmitEmpty()` 完成，Controller 层无需感知 `cancelSink`（见 3.6 节）。


---

## 六、超时机制

### 6.1 计时起点

**从 `content_done` 开始计时**，而非请求开始。thinking 阶段不计入 TTS 超时。

```
用户请求 ──► thinking 流 ──► content 流 ──► content_done ──► TTS 合成 ──► audio_done
                                               │              │
                                               │              └─── 10s 超时保护
                                               └─────────────────── 计时起点
```

### 6.2 超时容忍度分析

| TTS 提供商 | 典型耗时 | 10s 超时 |
|-----------|---------|---------|
| 讯飞 | 2-5s | ✅ 宽松 |
| MiniMax | <250ms | ✅✅ 绰绰有余 |

### 6.3 无重试机制说明

TTS 失败时不重试，重试会增加 4-10s 延迟，历史会话可兜底。

---

## 七、历史会话重获取

历史消息不含思考过程（`thinking` 内容不入库），只有正式回复内容和 `audioUrl`。

### 7.1 音频文件存储说明

TTS 音频文件存储于**本服务器本地磁盘**（`voice.tts-storage-path` 配置项），通过 `voice.tts-base-url` 拼接为可访问 URL。

- URL 无过期问题，历史消息直接使用 `audioUrl` 字段即可
- 无需重新请求 TTS，无需鉴权（静态文件直接访问）
- 如启用 GitHub 上传（`voice.upload-to-github=true`），URL 为 GitHub Raw URL，同样无过期

### 7.2 历史消息加载规范

```typescript
function mapHistoryMessage(msg: MessageVO): Message {
  return {
    id: String(msg.id),
    role: msg.role,
    content: msg.content,        // 只有正式回复，无思考过程
    thinkingContent: undefined,  // 历史消息无思考过程
    audioUrl: msg.audioUrl || null,
    ttsStatus: msg.audioUrl ? 'ready' : 'no_audio',
    isThinkingExpanded: false,
    isPlaying: false,
    pausedAt: 0,
    hasPaused: false,
    createdAt: msg.createdAt
  }
}
```

---

## 八、TTS Provider 策略模式

### 8.1 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                    VoiceServiceFacade                        │
└─────────────────────────────────────────────────────────────┘
         ┌──────────────────┼──────────────────┐
         ▼                  ▼                  ▼
┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│ XfyunTts   │      │MiniMaxTts   │      │ NoOpTts    │
│ Provider   │      │ Provider    │      │ Provider   │
└─────────────┘      └─────────────┘      └─────────────┘
```

### 8.2 配置设计

```yaml
voice:
  tts-provider: ${TTS_PROVIDER:xfyun}
  xfyun-app-id: ${XFYUN_APP_ID:}
  xfyun-api-secret: ${XFYUN_API_SECRET:}
  xfyun-api-key: ${XFYUN_API_KEY:}
  xfyun-tts-voice: xiaoyan
  minimax-api-key: ${MINIMAX_TTS_API_KEY:}
  minimax-tts-voice: male-qn-qingse
  enable-tts: true
  tts-storage-path: uploads/tts/
  tts-base-url: ${TTS_BASE_URL:http://localhost:8080}
```

---

## 九、前端实现 (Vue)

### 9.1 核心组件

| 文件 | 职责 |
|------|------|
| `ChatRoom.vue` | 聊天房间，含思考过程折叠展示 |
| `SseClient.ts` | SSE 统一客户端，新增 `onThinking` 回调 |
| `api/chat.ts` | 聊天 API 定义 |

### 9.2 Message 接口更新

```typescript
interface Message {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string                    // 正式回复内容
  thinkingContent?: string           // 思考推理过程（可选，模型不支持时为 undefined）
  isThinkingExpanded: boolean        // 思考过程是否展开
  audioUrl?: string | null
  ttsStatus: TtsStatus
  isPlaying: boolean
  pausedAt: number
  hasPaused: boolean
  createdAt: string
}

type TtsStatus =
  | 'pending'      // AI 还在生成内容
  | 'synthesizing' // content_done 已收到，等待 audio_done
  | 'ready'        // 有 URL，可播放
  | 'timeout'      // 超时，后台继续合成
  | 'error'        // 合成异常
  | 'noaudio'      // 提供商返回空
  | 'no_audio'     // 历史消息专用：无音频
```

### 9.3 SseClient 新增回调

```typescript
export interface SseClientOptions {
  // ... 原有回调 ...

  // 新增：AI 思考推理过程（增量）
  // 模型不支持思考模式时不会触发此回调
  onThinking?: (text: string, conversationId: number | null, messageId: number | null) => void
}
```

### 9.4 SseClient 事件处理

```typescript
private handleEvent(data: any): void {
  const conversationId: number | null = data.conversationId ?? null
  const messageId: number | null = data.messageId ?? null

  switch (data.type) {
    case 'thinking':
      // 思考过程增量，可选回调
      this.options.onThinking?.(data.text || '', conversationId, messageId)
      break

    case 'content':
      if (!this._firstContentReceived) {
        this._firstContentReceived = true
        this.options.onFirstContent?.()
      }
      this.options.onContent?.(data.text || '', conversationId, messageId)
      break

    case 'content_done':
      this.options.onContentDone?.(conversationId, messageId)
      break

    // ... 其余事件处理不变 ...
  }
}
```

### 9.5 ChatRoom 状态管理

```typescript
// 发送流式消息
async function sendStreamContent(content: string) {
  // 创建 AI 消息占位符，thinkingContent 初始为 undefined
  const aiMsg: Message = {
    id: 'stream-' + Date.now(),
    role: 'assistant',
    content: '',
    thinkingContent: undefined,
    isThinkingExpanded: true,  // 流式接收时默认展开
    ttsStatus: 'pending',
    isPlaying: false,
    pausedAt: 0,
    hasPaused: false,
    createdAt: new Date().toISOString()
  }

  const sseClient = new SseClient({
    // ...

    onThinking: (text, conversationId, messageId) => {
      // 追加思考内容
      if (aiMsg.thinkingContent === undefined) {
        aiMsg.thinkingContent = ''
      }
      aiMsg.thinkingContent += text
      scrollToBottom()
    },

    onContent: (text, conversationId, messageId) => {
      fullContent += text
      aiMsg.content = fullContent
      scrollToBottom()
    },

    // ... 其余回调不变 ...
  })
}
```

### 9.6 思考过程 UI 模板

```vue
<template>
  <div v-for="msg in messages" :key="msg.id" :class="['message', msg.role === 'user' ? 'user-message' : 'ai-message']">
    <div class="message-content">

      <!-- 思考过程折叠块（仅 assistant 且有 thinkingContent 时显示） -->
      <div v-if="msg.role === 'assistant' && msg.thinkingContent" class="thinking-block">
        <div class="thinking-header" @click="msg.isThinkingExpanded = !msg.isThinkingExpanded">
          <span class="thinking-icon">🧠</span>
          <span class="thinking-label">思考过程</span>
          <span class="thinking-toggle">{{ msg.isThinkingExpanded ? '▲ 收起' : '▼ 展开' }}</span>
        </div>
        <div v-show="msg.isThinkingExpanded" class="thinking-content">
          {{ msg.thinkingContent }}
        </div>
      </div>

      <!-- 正式回复内容 -->
      <div class="message-bubble" v-html="formatMessage(msg.content)"></div>

      <!-- TTS 合成中 -->
      <div v-if="msg.role === 'assistant' && msg.ttsStatus === 'synthesizing'" class="tts-status">
        <span class="tts-loading">🔊 语音合成中...</span>
      </div>

      <!-- 语音播放按钮 -->
      <div v-if="msg.role === 'assistant' && msg.ttsStatus === 'ready' && msg.audioUrl" class="audio-player">
        <!-- 播放控制按钮（略） -->
      </div>

      <div class="message-time">{{ formatTime(msg.createdAt) }}</div>
    </div>
  </div>
</template>
```

### 9.7 思考过程样式

```css
.thinking-block {
  margin-bottom: 8px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
  background: #fafafa;
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  cursor: pointer;
  user-select: none;
  font-size: 13px;
  color: #909399;
  background: #f5f7fa;
}

.thinking-header:hover {
  background: #ecf5ff;
}

.thinking-toggle {
  margin-left: auto;
  font-size: 12px;
}

.thinking-content {
  padding: 10px 12px;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 300px;
  overflow-y: auto;
  border-top: 1px solid #e4e7ed;
}
```

### 9.8 TTS 状态说明

| ttsStatus | 来源 | 显示内容 | audioUrl |
|-----------|------|---------|----------|
| `pending` | 实时流 | 无 | — |
| `synthesizing` | 实时流 | 🔊 语音合成中... | — |
| `ready` | 实时流 / 历史 | 播放按钮 | 有值 |
| `timeout` | 实时流 | 无 | null |
| `error` | 实时流 | 无 | null |
| `noaudio` | 实时流 | 无 | null |
| `no_audio` | 历史消息 | 无 | null |


---

## 十、Android 实现

### 10.1 核心组件

| 组件 | 文件 | 职责 |
|------|------|------|
| `AudioPlayer` | `AudioPlayer.kt` | 音频播放管理 |
| `MessageBubble` | `MessageBubble.kt` | 消息气泡（含思考折叠块和 TTS 按钮） |
| `StreamEvent` | `StreamEvent.kt` | 新增 `Thinking` 事件类型 |

### 10.2 StreamEvent 新增类型

```kotlin
sealed class StreamEvent {
    abstract val conversationId: Long?

    /** AI 思考推理过程（增量，可选） */
    data class Thinking(
        override val conversationId: Long?,
        val messageId: Long?,
        val text: String
    ) : StreamEvent()

    /** AI 正式回复内容（增量） */
    data class Content(
        override val conversationId: Long?,
        val messageId: Long?,
        val text: String
    ) : StreamEvent()

    data class ContentDone(override val conversationId: Long?, val messageId: Long?) : StreamEvent()
    data class AudioDone(
        override val conversationId: Long?,
        val messageId: Long?,
        val url: String?,
        val timeout: Boolean,
        val error: String?,
        val noaudio: Boolean
    ) : StreamEvent()

    data class TokenUsage(override val conversationId: Long?, val inputTokens: Int,
        val outputTokens: Int, val duration: Long, val model: String) : StreamEvent()
    data class ToolCall(override val conversationId: Long?, val id: String,
        val name: String, val arguments: String) : StreamEvent()
    data class ToolResult(override val conversationId: Long?, val id: String,
        val name: String, val result: String) : StreamEvent()
    data class CacheHit(override val conversationId: Long?, val content: String) : StreamEvent()
    data class Clarification(override val conversationId: Long?, val content: String) : StreamEvent()
    data class Done(override val conversationId: Long?) : StreamEvent()
    data class Error(override val conversationId: Long?, val message: String) : StreamEvent()
    data class AudioUrl(override val conversationId: Long?, val url: String) : StreamEvent()
}
```

### 10.3 SSE 解析更新

```kotlin
return when (type) {
    "thinking" -> StreamEvent.Thinking(
        resolvedConvId,
        msgId,
        obj.get("text")?.asString ?: ""
    )
    "content" -> StreamEvent.Content(
        resolvedConvId,
        msgId,
        obj.get("text")?.asString ?: obj.get("content")?.asString ?: ""
    )
    // ... 其余类型不变 ...
}
```

### 10.4 Message 状态

```kotlin
data class Message(
    val id: Long,
    val role: String,
    val content: String,
    val thinkingContent: String? = null,   // 思考过程，null 表示模型不支持或历史消息
    val isThinkingExpanded: Boolean = true, // 流式时默认展开，历史消息默认折叠
    val audioUrl: String? = null,
    val ttsStatus: TtsStatus = TtsStatus.PENDING,
    val isPlaying: Boolean = false,
    val pausedAt: Float = 0f,
    val hasPaused: Boolean = false
)

enum class TtsStatus {
    PENDING, SYNTHESIZING, READY, TIMEOUT, ERROR, NOAUDIO, NO_AUDIO
}
```

### 10.5 ViewModel 处理

```kotlin
fun sendMessageStream(content: String) {
    viewModelScope.launch {
        val aiMessageId = System.currentTimeMillis() + 1
        var currentThinking = StringBuilder()
        var currentContent = StringBuilder()

        // 添加 AI 消息占位符
        _uiState.update { state ->
            state.copy(
                messages = state.messages + Message(
                    id = aiMessageId,
                    role = MessageRole.ASSISTANT,
                    content = "",
                    thinkingContent = null,
                    isThinkingExpanded = true,
                    createdAt = LocalDateTime.now()
                ),
                isSending = true
            )
        }

        chatRepository.streamMessage(content, _uiState.value.currentConversationId)
            .collect { event ->
                when (event) {
                    is StreamEvent.Thinking -> {
                        currentThinking.append(event.text)
                        _uiState.update { state ->
                            state.copy(messages = state.messages.map { msg ->
                                if (msg.id == aiMessageId)
                                    msg.copy(thinkingContent = currentThinking.toString())
                                else msg
                            })
                        }
                    }
                    is StreamEvent.Content -> {
                        currentContent.append(event.text)
                        _uiState.update { state ->
                            state.copy(messages = state.messages.map { msg ->
                                if (msg.id == aiMessageId)
                                    msg.copy(content = currentContent.toString(), ttsStatus = TtsStatus.PENDING)
                                else msg
                            })
                        }
                    }
                    is StreamEvent.ContentDone -> {
                        _uiState.update { state ->
                            state.copy(messages = state.messages.map { msg ->
                                if (msg.id == aiMessageId) msg.copy(ttsStatus = TtsStatus.SYNTHESIZING) else msg
                            })
                        }
                    }
                    is StreamEvent.Done -> {
                        _uiState.update { it.copy(isSending = false) }
                    }
                    // ... 其余事件处理不变 ...
                    else -> {}
                }
            }
    }
}
```

### 10.6 UI 显示

```kotlin
@Composable
fun MessageBubble(message: Message, ...) {
    Column {
        // 思考过程折叠块（仅 assistant 且有 thinkingContent 时显示）
        if (message.role == MessageRole.ASSISTANT && message.thinkingContent != null) {
            ThinkingBlock(
                content = message.thinkingContent,
                isExpanded = message.isThinkingExpanded,
                onToggle = { viewModel.toggleThinking(message.id) }
            )
        }

        // 正式回复气泡
        MessageContent(message = message)

        // TTS 状态
        when (message.ttsStatus) {
            TtsStatus.SYNTHESIZING -> TtsSynthesizingIndicator()  // 🔊 语音合成中...
            TtsStatus.READY -> AudioPlayButton(message = message, ...)
            else -> {}
        }
    }
}

@Composable
fun ThinkingBlock(content: String, isExpanded: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            // 标题栏（可点击展开/折叠）
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🧠 思考过程", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            // 内容（可折叠）
            AnimatedVisibility(visible = isExpanded) {
                Text(
                    text = content,
                    modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```


---

## 十一、状态机转换

### 11.1 TTS 状态机（含思考过程）

```
用户发送消息
       │
       ▼
┌─────────────────────────────────────────────────────┐
│  thinking 事件（可选，模型支持时流式输出）              │
│  → 前端/Android 追加到 thinkingContent，折叠展示      │
└─────────────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────┐
│  content 事件（正式回复，流式输出）                    │
│  ttsStatus = PENDING                                 │
└─────────────────────────────────────────────────────┘
       │
       ▼
┌──────────────────────┐
│   content_done 收到   │
│   ttsStatus =        │
│   SYNTHESIZING       │
└──────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────┐
│  TTS 后台合成（只合成 content，不含 thinking）         │
└─────────────────────────────────────────────────────┘
    /        |          |          \
   ▼         ▼          ▼           ▼
READY    TIMEOUT     ERROR       NOAUDIO
播放按钮  不显示      不显示       不显示
       │
       ▼
┌──────────────────────┐
│       done 收到      │
│    SSE 连接关闭      │
└──────────────────────┘
```

### 11.2 思考过程兼容性

| 场景 | thinking 事件 | 前端/Android 行为 |
|------|--------------|-----------------|
| 模型返回 reasoning_content | 有 | 显示思考折叠块 |
| 模型不返回 reasoning_content | 无 | 不显示思考折叠块，正常显示回复 |
| 历史消息 | 无 | 不显示思考折叠块（thinking 不入库） |

---

## 十二、通用 UX 规范

### 12.1 连接状态定义

| 状态 | 说明 | 前端 UI | Android UI |
|------|------|---------|-----------|
| `idle` | 初始状态 | 隐藏消息气泡 | 隐藏消息气泡 |
| `connecting` | 正在连接 | 打字动画 | CircularProgressIndicator |
| `thinking` | 收到 thinking 事件 | 思考折叠块（展开） | ThinkingBlock（展开） |
| `receiving` | 收到 content 事件 | 正式回复气泡逐字显示 | 逐字动画 |
| `done` | 完成 | 停止动画 | 停止动画 |
| `error` | 异常 | 错误提示 + 重试按钮 | Error UI + 重试按钮 |

### 12.2 响应时间阈值

以下时间均为**累计等待时间**（从用户发送消息起计）：

| 累计等待时间 | 触发条件 | 用户反馈 |
|------------|---------|----------|
| 0 - 10s | 等待首字节 | 持续显示打字动画 |
| 10s - 30s | 首字节仍未到达 | 额外显示"AI 正在处理中..." |
| 30s - 60s | 首字节仍未到达 | 显示"响应较慢，请稍候..." |
| > 60s | 超时 | 中断连接，显示超时错误 + 重试按钮 |

> 首字节到达后（开始收到 `thinking` 或 `content` 事件），超时计时重置，不再显示等待提示。

### 12.3 思考过程 UX 规范

| 规则 | 说明 |
|------|------|
| 流式接收时默认展开 | 让用户感知 AI 正在思考 |
| 历史消息默认折叠 | 减少视觉干扰 |
| 可手动展开/折叠 | 用户自主控制 |
| 思考内容不参与 TTS | 只朗读正式回复 |
| 思考内容不入库 | 历史消息不含思考过程 |

---

## 十三、数据库存储规范

### 13.1 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `content` | TEXT | 正式回复内容，不含思考过程 |
| `audio_url` | VARCHAR(500) | TTS 生成音频的 URL |

### 13.2 保存时机

```java
// thinking 内容不保存
// aiFinalContent 只累加 content 事件的文本
saveAssistantMessage(userId, conversationId, aiFinalContent.get(), functionCallJson, null);

// TTS 完成后更新
updateMessageAudioUrl(messageId, audioUrl);
```

---

## 十四、调试日志规范

### 14.1 后端日志

```java
log.debug("AI 思考过程: {}", thinkingText.substring(0, Math.min(50, thinkingText.length())));
log.info("AI 内容流结束，开始处理 TTS");
log.info("TTS 合成完成，audioUrl={}", audioUrl);
log.warn("TTS 超时（{}ms），后台继续合成", TTS_TIMEOUT_MS);
log.error("TTS 合成失败", e);
```

### 14.2 前端日志

```typescript
console.log('[SSE] thinking:', text.substring(0, 50))
console.log('[SSE] content:', text)
console.log('[SSE] content_done 收到')
console.log('[SSE] audio_done:', event)
console.log('[SSE] done')
```

### 14.3 Android 日志

```kotlin
Log.d(TAG, "SSE thinking: ${text.take(50)}")
Log.d(TAG, "SSE content: $text")
Log.d(TAG, "content_done 收到")
Log.d(TAG, "audio_done: url=$url, timeout=$timeout")
Log.d(TAG, "SSE done")
```

---

## 十五、常见问题排查

### 15.1 思考过程混入正式回复

**原因**：AI 客户端 `parseSseData()` 未区分 `reasoning_content` 和 `content`，将两者都作为普通文本返回。

**排查**：
- 检查 `MiniMaxClient.parseSseData()` 是否对 `reasoning_content` 加了 `[THINKING]` 前缀
- 检查 `KimiClient.parseSseData()` 是否已从 `message` 字段迁移到 `delta` 字段，并同样处理 `reasoning_content`
- 检查 `StreamChatService` 是否正确识别 `[THINKING]` 标记并路由到 `thinking` 事件

### 15.2 前端不显示思考折叠块

**原因**：`thinkingContent` 为 `undefined` 或 `onThinking` 回调未注册。

**排查**：检查 `SseClient` 是否注册了 `onThinking` 回调，检查 `aiMsg.thinkingContent` 初始化逻辑。

### 15.3 前端显示 "SSE解析跳过"

**原因**：后端发送的事件缺少 `data:` 前缀。

**排查**：检查 Controller 层是否使用 `SseFormatter.addSsePrefix()` 统一格式化。

### 15.4 audio_url 为 null

**原因**：TTS 合成失败或配置错误。

**排查**：检查 `voiceService.textToSpeech()` 返回值，检查讯飞/MiniMax API 配置。

---

## 十六、待办事项

### 16.1 后端

| 待办 | 优先级 | 状态 |
|------|--------|------|
| MiniMaxClient 分离 reasoning_content，加 [THINKING] 前缀 | **P0** | ✅ 已完成 |
| KimiClient 改用 delta 字段解析，支持 reasoning_content | **P0** | ✅ 已完成 |
| SseFormatter 新增 thinking() 方法 | **P0** | ✅ 已完成（见 5.4 节） |
| StreamChatService 识别 [THINKING] 标记，发送 thinking 事件 | **P0** | 待实现 |
| StreamChatService doOnCancel 取消传播 + TTS 静默兜底 | **P0** | 待实现 |
| 缓存命中路径补全 content_done / audio_done / done 事件 | **P0** | ✅ 已完成 |
| MiniMax TTS 实现 | P1 | 待实现 |

### 16.2 前端

| 待办 | 优先级 | 状态 |
|------|--------|------|
| SseClient 新增 onThinking 回调 | **P0** | ✅ 已完成 |
| Message 接口新增 thinkingContent / isThinkingExpanded | **P0** | ✅ 已完成 |
| ChatRoom 思考折叠块 UI | **P0** | ✅ 已完成 |
| ChatRoom 切换会话/卸载时 abort SSE 连接 | **P0** | ✅ 已完成 |
| 加载指示器优化（有内容前显示打字动画） | **P0** | ✅ 已完成 |

### 16.3 Android

| 待办 | 优先级 | 状态 |
|------|--------|------|
| StreamEvent 新增 Thinking 类型 | **P0** | ✅ 已完成 |
| SSE 解析新增 thinking 事件处理 | **P0** | ✅ 已完成 |
| Message 新增 thinkingContent / isThinkingExpanded | **P0** | ✅ 已完成 |
| ViewModel 处理 Thinking 事件 | **P0** | ✅ 已完成 |
| ThinkingBlock Composable | **P0** | ✅ 已完成 |
| ViewModel 切换会话/onCleared 时 cancel streamJob | **P0** | ✅ 已完成 |
| 响应时间阈值分阶段提示（10s/30s/60s） | **P1** | ✅ 已完成 |
| SSE 错误重试按钮 | **P1** | ✅ 已完成 |
| content_done/audio_done 专项调试日志 | **P1** | ✅ 已完成 |

---

## 附录

### A.1 相关文档

| 文档 | 路径 | 说明 |
|------|------|------|
| `SSE_STREAM_SPEC.md` | `mds/SSE_STREAM_SPEC.md` | SSE 流式输出基础规范 |
| `SSE_TTS_ASYNC_SPEC.md` | `mds/SSE_TTS_ASYNC_SPEC.md` | TTS 异步化方案 |
| `VOICE_PLAYBACK_SPEC.md` | `mds/VOICE_PLAYBACK_SPEC.md` | 语音播放实现规范 |

---

**文档版本**: 2.4
**最后更新**: 2026-03-29
**变更说明**: 修正事件时序表；补全所有事件 JSON 示例；完善并发控制规范（3.6 节，含前端 abort、Android cancel、后端 takeUntil 取消传播、TTS 静默兜底）；修正 Android TtsSynthesizingIndicator 命名；响应时间阈值改为累计等待时间；说明 escapeJson 职责约定；修正待办状态；更新前端待办为已完成（16.2 节）；后端 StreamChatService 相关待办需人工确认状态
