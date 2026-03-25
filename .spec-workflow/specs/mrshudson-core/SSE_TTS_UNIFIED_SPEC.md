# SSE 流式响应 + TTS 语音合成统一规范

**文档版本**: 2.0
**创建日期**: 2026-03-24
**最后更新**: 2026-03-25
**文档状态**: 正式版

---

## 一、概述

### 1.1 文档目的

本文档定义 MrsHudson 项目中 SSE 流式响应与 TTS 语音合成的完整方案，实现：

- 单 SSE 连接完成全部通信，不引入 WebSocket 或双 SSE
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
| **content_done** | AI 文本生成完毕，前端可感知内容已全部加载 |
| **audio_done** | TTS 合成完毕，前端/Android 展示播放按钮 |
| **content_done 起计时** | 超时从 AI 内容结束开始，TTS 有完整 10s |
| **历史兜底** | 超时不影响，音频已落库，下次加载历史消息获取 |
| **后端统一 TTS** | 后端负责 TTS，前端/Android 只负责播放 |
| **无重试机制** | TTS 失败不重试，避免增加 4-10s 延迟 |

### 1.3 技术选型

| 组件 | 技术方案 | 备注 |
|------|---------|------|
| ASR 提供商 | 讯飞语音 SDK (xfyun) | `IatClient` |
| TTS 提供商 | **策略模式切换** | 讯飞 / MiniMax |
| 流式协议 | SSE (Server-Sent Events) | |
| 前端播放 | HTML5 `<audio>` | 只读播放 |
| Android 播放 | MediaPlayer | 可选本地 TTS 降级 |

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
data: {"type":"content","text":"Hello"}\n\n
data: {"type":"content_done"}\n\n
data: {"type":"audio_done","url":"https://..."}\n\n
data: {"type":"done"}\n\n
```

---

## 三、事件类型定义

### 3.1 完整事件类型列表

| 事件类型 | 说明 | 字段 |
|----------|------|------|
| `content` | AI 增量内容 | `text`: string, `conversationId`: number |
| `content_done` | AI 文本内容结束 | `conversationId`: number |
| `audio_done` | TTS 语音合成结束 | `conversationId`: number, `url`: string, `timeout`: boolean, `error`: string |
| `token_usage` | Token 统计 | `inputTokens`, `outputTokens`, `duration`, `model` |
| `tool_call` | 工具调用 | `toolCall`: {name, arguments} |
| `tool_result` | 工具执行结果 | `toolResult`: {name, result} |
| `cache_hit` | 缓存命中 | `content`: string |
| `clarification` | 澄清提示 | `content`: string |
| `error` | 错误信息 | `message`: string |
| `done` | 流式完成 | — |

> **重要**：所有事件必须包含 `conversationId` 字段，以便 Android 端正确区分不同会话的消息。

### 3.2 content_done 事件

```json
{"type":"content_done"}
```

表示 AI 文本已全部生成，TTS 可在后台开始。

### 3.3 audio_done 事件

```json
// TTS 成功
{"type":"audio_done","url":"https://raw.githubusercontent.com/..."}

// TTS 超时（超过 10 秒）
{"type":"audio_done","timeout":true}

// TTS 失败
{"type":"audio_done","error":"TTS_FAILED"}

// TTS 成功但 URL 为空
{"type":"audio_done","noaudio":true}
```

### 3.4 事件时序说明

| 事件顺序 | type | 说明 |
|---------|------|------|
| 1 | `content` | AI 增量内容，可多次发送 |
| 2 | `tool_call` | 工具调用 |
| 3 | `tool_result` | 工具执行结果 |
| 4 | `content_done` | AI 内容结束，**TTS 后台开始** |
| 5 | `audio_done` | TTS 结束，**前端/Android 展示播放按钮** |
| 6 | `done` | SSE 连接关闭 |

---

## 四、SSE 事件序列

### 4.1 完整事件序列（正常流程）

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

### 4.2 超时场景

```
data: {"type":"content_done"}\n\n


                                                             [TTS 合成中...]
                                                             [超过 10 秒]

data: {"type":"audio_done","timeout":true}\n\n
      ← 超时不展示按钮
data: {"type":"done"}\n\n
```

### 4.3 失败场景

```
data: {"type":"content_done"}\n\n


                                                             [TTS 合成中...]
                                                             [失败]

data: {"type":"audio_done","error":"TTS_FAILED"}\n\n
      ← 失败，不展示按钮
data: {"type":"done"}\n\n
```

### 4.4 意图路由场景

意图路由的响应也走异步 TTS 流程，避免同步阻塞。

```
data: {"type":"content","text":"今天天气不错"}\n\n
                                                              ↑
                                                    content_done
data: {"type":"content_done"}\n\n


                                                             [TTS 合成中...]

data: {"type":"audio_done","url":"https://..."}\n\n
data: {"type":"done"}\n\n
```

---

## 五、后端实现

### 5.1 架构设计

后端采用 **Service 层构建事件 → Controller 层统一格式化** 的双层架构：

```
┌─────────────────────────────────────────────────────────────┐
│                     Controller 层                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  transform(SseFormatter::addSsePrefix)                │   │
│  │  统一添加 data: 前缀，将 JSON 转换为 SSE 格式         │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              ↑
                              │
┌─────────────────────────────────────────────────────────────┐
│                     Service 层                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  SseFormatter.content(), SseFormatter.audioDone()    │   │
│  │  构建纯 JSON 字符串，不包含 data: 前缀                │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 核心组件

| 文件 | 职责 |
|------|------|
| `VoiceService.java` | 语音服务接口（抽象层） |
| `VoiceServiceImpl.java` | 讯飞 SDK 实现 |
| `MiniMaxTtsService.java` | MiniMax SDK 实现 |
| `VoiceProperties.java` | 配置属性 |
| `SseFormatter.java` | SSE 事件格式化工具 |
| `StreamChatService.java` | 流式对话服务（含 TTS 调用） |

### 5.3 SseFormatter 完整 API

**文件**: `SseFormatter.java`

```java
public final class SseFormatter {

    // ========== 核心格式化方法 ==========

    /**
     * 格式化 SseEvent 为 SSE 字符串
     */
    public static String format(SseEvent event) { ... }

    /**
     * 格式化 JSON 字符串为 SSE 字符串
     */
    public static String format(String jsonStr) { ... }

    /**
     * Flux JSON → Flux SSE 转换
     */
    public static Flux<String> addSsePrefix(Flux<String> flux) { ... }

    // ========== 事件构建方法 ==========

    /**
     * AI 增量内容
     */
    public static String content(String text) {
        return "{\"type\":\"content\",\"text\":\"" + escapeJson(text) + "\"}";
    }

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

    /**
     * Token 使用统计
     */
    public static String tokenUsage(int input, int output, long duration, String model) {
        return "{\"type\":\"token_usage\",\"inputTokens\":" + input
             + ",\"outputTokens\":" + output
             + ",\"duration\":" + duration
             + ",\"model\":\"" + escapeJson(model) + "\"}";
    }

    /**
     * 工具调用
     */
    public static String toolCall(String name, String arguments) {
        return "{\"type\":\"tool_call\",\"toolCall\":{\"name\":\""
             + escapeJson(name) + "\",\"arguments\":\""
             + escapeJson(arguments) + "\"}}";
    }

    /**
     * 工具执行结果
     */
    public static String toolResult(String name, String result) {
        return "{\"type\":\"tool_result\",\"toolResult\":{\"name\":\""
             + escapeJson(name) + "\",\"result\":\""
             + escapeJson(result) + "\"}}";
    }

    /**
     * 缓存命中
     */
    public static String cacheHit(String content) {
        return "{\"type\":\"cache_hit\",\"content\":\""
             + escapeJson(content) + "\"}";
    }

    /**
     * 澄清提示
     */
    public static String clarification(String content) {
        return "{\"type\":\"clarification\",\"content\":\""
             + escapeJson(content) + "\"}";
    }

    /**
     * 错误信息
     */
    public static String error(String message) {
        return "{\"type\":\"error\",\"message\":\""
             + escapeJson(message) + "\"}";
    }

    /**
     * 流式完成
     */
    public static String done() {
        return "{\"type\":\"done\"}";
    }

    // ========== 工具方法 ==========

    private static String escapeJson(String text) { ... }
}
```

### 5.4 StreamChatService 实现

**文件**: `StreamChatService.java`

#### 5.4.1 核心逻辑

```java
// TTS 超时配置（毫秒）
private static final long TTS_TIMEOUT_MS = 10000;

public Flux<String> streamSendMessage(Long userId, SendMessageRequest request) {
    // 1. 缓存命中
    if (cacheResult.isHit()) {
        return Flux.just(SseFormatter.cacheHit(escapeJson(cacheResult.getResponse())));
    }

    // 2. 意图路由直接处理
    if (routeResult.isHandled()) {
        return handleIntentRoute(userId, conversationId, routeResult);
    }

    // 3. LLM 流式响应
    return handleLlmStream(userId, conversationId, request);
}
```

#### 5.4.2 LLM 流式响应处理

```java
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
                    // 即使 audioUrl 为 null，也记录状态（后续可通过历史会话获取）
                    if (audioUrl != null) {
                        updateMessageAudioUrl(messageId, audioUrl);
                    }
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
```

#### 5.4.3 意图路由处理

```java
/**
 * 处理意图路由（异步 TTS）
 *
 * 注意：意图路由的 TTS 也必须走异步流程，否则同步阻塞会影响快速响应优势
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

### 5.5 Controller 层实现

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

### 5.6 统一入口原则

| 层级 | 职责 | 输出格式 |
|------|------|----------|
| Service | 构建事件 JSON | `{"type":"content","text":"Hello"}` |
| Controller | 添加 SSE 前缀 | `data: {"type":"content","text":"Hello"}\n\n` |

**禁止**：
- Service 层不应添加 `data:` 前缀
- 其他 Controller 不应自行实现 SSE 格式化逻辑

---

## 六、超时机制

### 6.1 计时起点

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

### 6.2 超时容忍度分析

| TTS 提供商 | 典型耗时 | 10s 超时 | 5s 超时 |
|-----------|---------|---------|---------|
| 讯飞 | 2-5s | ✅ 宽松 | ⚠️ 紧张 |
| MiniMax | <250ms | ✅✅ 绰绰有余 | ✅✅ 绰绰有余 |

### 6.3 超时处理流程

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

### 6.4 无重试机制说明

TTS 失败时不重试，原因：
- 重试会增加 4-10s 延迟
- 用户体验反而更差
- 历史会话可兜底，下次加载可获取 audioUrl

---

## 七、历史会话重获取

### 7.1 重获取机制

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

### 7.2 后端新增接口（可选）

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

### 7.3 前端重获取说明

前端不需要主动重获取，下次加载会话列表或消息时会自动从数据库获取 audioUrl。

---

## 八、TTS Provider 策略模式

### 8.1 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                    VoiceServiceFacade                        │
│                   （对外统一接口）                            │
└─────────────────────────────────────────────────────────────┘
                              │
         ┌──────────────────┼──────────────────┐
         ▼                  ▼                  ▼
┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│ XfyunTts   │      │MiniMaxTts   │      │ NoOpTts    │
│ Provider   │      │ Provider    │      │ Provider   │
└─────────────┘      └─────────────┘      └─────────────┘
```

### 8.2 接口定义

```java
/**
 * TTS 提供者接口
 */
public interface TtsProvider {
    /**
     * 提供者名称
     */
    String getName();

    /**
     * 是否已配置（密钥等）
     */
    boolean isConfigured();

    /**
     * 合成语音
     * @param text 文本
     * @return 音频文件路径/URL，失败返回 null
     */
    String synthesize(String text);
}

/**
 * TTS 回调
 */
public interface TtsCallback {
    void onSuccess(String audioUrl);
    void onError(String message);
}
```

### 8.3 配置设计

**文件**: `application.yml`

```yaml
voice:
  # TTS 提供者: xfyun / minimax / none
  tts-provider: ${TTS_PROVIDER:xfyun}

  # 讯飞配置（tts-provider=xfyun 时启用）
  xfyun-app-id: ${XFYUN_APP_ID:}
  xfyun-api-secret: ${XFYUN_API_SECRET:}
  xfyun-api-key: ${XFYUN_API_KEY:}
  xfyun-tts-voice: xiaoyan

  # MiniMax 配置（tts-provider=minimax 时启用）
  minimax-api-key: ${MINIMAX_TTS_API_KEY:}
  minimax-tts-voice: male-qn-qingse

  # 通用配置
  enable-tts: true
  tts-storage-path: uploads/tts/
  tts-base-url: ${TTS_BASE_URL:http://localhost:8080}
  upload-to-github: true
```

### 8.4 待办事项

| 待办 | 优先级 | 状态 |
|------|--------|------|
| TTS Provider 策略模式重构 | **P0** | 待实现 |
| MiniMax TTS 实现 | P1 | 待实现 |
| 长文本智能分块 | P2 | 后续考虑 |

---

## 九、前端实现 (Vue)

### 9.1 核心组件

| 文件 | 职责 |
|------|------|
| `ChatRoom.vue` | 聊天房间，包含消息列表和输入框 |
| `VoiceInputButton.vue` | 语音输入（录音）组件 |
| `SseClient.ts` | SSE 统一客户端 |
| `api/chat.ts` | 聊天 API 定义 |

### 9.2 SseClient 增强接口

**文件**: `src/utils/sse/SseClient.ts`

```typescript
export interface SseClientOptions {
  url: string
  method?: 'GET' | 'POST'
  headers?: Record<string, string>
  body?: any
  timeout?: number  // 超时时间(ms)，默认 60000

  // 状态回调
  onConnecting?: () => void      // 刚发起连接
  onFirstContent?: () => void   // 收到首个 content 事件

  // 事件回调
  onContent?: (text: string) => void
  onContentDone?: () => void                    // 新增
  onAudioDone?: (event: AudioDoneEvent) => void // 新增
  onTokenUsage?: (usage: TokenUsage) => void
  onToolCall?: (tool: ToolCallInfo) => void
  onToolResult?: (result: ToolResultInfo) => void
  onCacheHit?: (content: string) => void
  onClarification?: (content: string) => void
  onError?: (error: string) => void
  onDone?: () => void
  onOpen?: () => void
  onClose?: () => void
}

export interface AudioDoneEvent {
  url: string | null     // 音频 URL
  timeout: boolean      // 是否超时
  error: string | null  // 错误信息
}
```

### 9.3 SSE 解析更新

```typescript
class SseClient {
  private handleEvent(data: any): void {
    switch (data.type) {
      case 'content':
        this.options.onContent?.(data.text || data.content || '')
        break

      case 'content_done':  // 新增
        this.options.onContentDone?.()
        break

      case 'audio_done':     // 新增
        this.options.onAudioDone?.({
          url: data.url || null,
          timeout: data.timeout || false,
          error: data.error || null
        })
        break

      case 'token_usage':
        this.options.onTokenUsage?.({ ... })
        break

      case 'tool_call':
        if (data.toolCall) {
          this.options.onToolCall?.({ ... })
        }
        break

      case 'tool_result':
        if (data.toolResult) {
          this.options.onToolResult?.({ ... })
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
}
```

### 9.4 Message 接口更新

```typescript
interface Message {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  audioUrl?: string | null      // SSE 流中获取的音频 URL
  ttsStatus: TtsStatus          // TTS 状态
  isPlaying?: boolean            // 是否正在播放
  pausedAt?: number             // 暂停位置（秒）
  hasPaused?: boolean           // 是否曾暂停过
  createdAt: string
}

type TtsStatus = 'pending' | 'synthesizing' | 'ready' | 'timeout' | 'error'
```

### 9.5 TTS 状态说明

| ttsStatus | 显示内容 | 说明 |
|-----------|---------|------|
| `pending` | 无 | AI 还在生成内容 |
| `synthesizing` | 🔊 语音合成中... | content_done 已收到，等待 audio_done |
| `ready` | 播放按钮 | audio_done 收到，有 URL |
| `timeout` | 无 | 超时不展示播放按钮 |
| `error` | 无 | 失败不展示播放按钮 |

### 9.6 ChatRoom 状态管理

```typescript
const sseClient = new SseClient({
  url: '/api/chat/stream',
  method: 'POST',
  body: {
    message: content,
    conversationId: conversationId.value || null
  },

  onConnecting: () => {
    chatState.value.status = 'connecting'
  },

  onFirstContent: () => {
    chatState.value.status = 'receiving'
  },

  onContent: (text) => {
    chatState.value.streamingContent += text
  },

  onContentDone: () => {
    // AI 内容接收完毕
    const lastMsg = getLastAssistantMessage()
    if (lastMsg) {
      lastMsg.ttsStatus = 'synthesizing'  // 开始合成 TTS
    }
  },

  onAudioDone: (event: AudioDoneEvent) => {
    const lastMsg = getLastAssistantMessage()
    if (!lastMsg) return

    if (event.timeout) {
      lastMsg.ttsStatus = 'timeout'
      lastMsg.audioUrl = null
    } else if (event.error) {
      lastMsg.ttsStatus = 'error'
      lastMsg.audioUrl = null
    } else if (event.url) {
      lastMsg.ttsStatus = 'ready'
      lastMsg.audioUrl = event.url
    }
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

### 9.7 模板实现

```vue
<template>
  <div v-for="msg in messages" :key="msg.id">
    <div class="message-bubble" v-html="formatMessage(msg.content)"></div>

    <!-- TTS 合成中状态提示 -->
    <div v-if="msg.ttsStatus === 'synthesizing'" class="tts-status">
      <span class="tts-loading">🔊 语音合成中...</span>
    </div>

    <!-- 语音播放按钮：仅 TTS 完成且有 URL 时显示 -->
    <div v-if="showAudioButton(msg)" class="audio-player">
      <el-button @click="toggleAudio(msg)" circle>
        {{ msg.isPlaying ? '暂停' : '播放' }}
      </el-button>
      <el-button v-if="msg.hasPaused && !msg.isPlaying" @click="resumeAudio(msg)">
        继续
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
const showAudioButton = (msg: Message) => {
  return msg.role === 'assistant' && msg.ttsStatus === 'ready' && msg.audioUrl
}
</script>
```

### 9.8 播放控制

| 操作 | 方法 | 说明 |
|------|------|------|
| 从头播放 | `toggleAudio()` | 暂停当前播放，从头开始 |
| 继续播放 | `resumeAudio()` | 从暂停位置继续 |
| 暂停 | `toggleAudio()` | 暂停并记录位置 |

---

## 十、Android 实现

### 10.1 核心组件

| 组件 | 文件 | 职责 |
|------|------|------|
| `AudioPlayer` | `AudioPlayer.kt` | 音频播放管理（单例） |
| `XfyunTtsManager` | `XfyunTtsManager.kt` | 本地讯飞 TTS（可降级） |
| `TtsButton` | `TtsButton.kt` | 播放按钮 UI |
| `MessageBubble` | `MessageBubble.kt` | 消息气泡（含 TTS 按钮） |
| `GitHubAudioCache` | `GitHubAudioCache.kt` | GitHub 音频缓存 |

### 10.2 StreamEvent 新增类型

**文件**: `data/repository/StreamEvent.kt`

```kotlin
sealed class StreamEvent {
    abstract val conversationId: Long?

    /** AI 增量内容事件 */
    data class Content(
        override val conversationId: Long?,
        val text: String
    ) : StreamEvent()

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

### 10.3 SSE 解析更新

```kotlin
private fun parseEvent(data: String): StreamEvent? {
    if (data.isBlank()) return null

    val obj = JsonParser.parseString(data).asJsonObject
    val type = obj.get("type")?.asString ?: return null
    val convId: Long? = null

    return when (type) {
        "content" -> StreamEvent.Content(
            convId,
            obj.get("text")?.asString ?: obj.get("content")?.asString ?: ""
        )

        "content_done" -> StreamEvent.ContentDone(convId)  // 新增

        "audio_done" -> StreamEvent.AudioDone(            // 新增
            conversationId = convId,
            url = obj.optString("url").takeIf { it.isNotBlank() },
            timeout = obj.optBoolean("timeout"),
            error = obj.optString("error").takeIf { it.isNotBlank() }
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
```

### 10.4 Message 状态

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

### 10.5 ViewModel 处理

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

### 10.6 UI 显示

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

### 10.7 语音播放流程

```
AudioPlayer.playOrPause(message)
    │
    ├── message.audioUrl 非空
    │       │
    │       ├─► GitHub URL → 本地缓存或直接播放
    │       ├─► 服务端 URL → 下载（需认证）
    │       └─► 公开 URL → 直接播放
    │
    └── message.audioUrl 为空 → 不显示播放按钮
            （如需离线能力，可选启用本地 TTS 降级）
```

### 10.8 播放控制

| 操作 | 方法 | 说明 |
|------|------|------|
| 从头播放 | `togglePlay()` | 暂停当前播放，从头开始 |
| 继续播放 | `resumePlay()` | 从暂停位置继续 |
| 暂停 | `pausePlay()` | 暂停并记录位置 |

### 10.9 conversationId 处理

#### 10.9.1 问题描述

Android 端 `conversationId` 始终为 `null`，导致无法正确区分不同会话的消息事件。

#### 10.9.2 问题原因

**后端问题**：
- `SseFormatter` 方法（如 `content()`、`contentDone()`、`audioDone()`）未携带 `conversationId` 参数
- 发送的事件 JSON 中缺少 `conversationId` 字段

**Android 端问题**：
- `parseEvent()` 方法中 `convId` 硬编码为 `null`：
  ```kotlin
  val convId: Long? = null  // 始终为 null
  ```

#### 10.9.3 影响范围

| 影响项 | 说明 |
|--------|------|
| 多会话切换 | 切换会话后，新消息的 `conversationId` 仍为 null，无法匹配 |
| 消息关联 | 事件无法正确关联到对应会话 |
| 状态更新 | TTS 状态更新可能作用在错误的会话上 |

#### 10.9.4 解决方案

**后端修改**：

1. 修改 `SseFormatter` 方法签名，添加 `conversationId` 参数：

```java
// SseFormatter.java
public static String content(String text, Long conversationId) {
    return String.format(
        "{\"type\":\"content\",\"text\":\"%s\",\"conversationId\":%d}",
        text, conversationId
    );
}

public static String contentDone(Long conversationId) {
    return String.format(
        "{\"type\":\"content_done\",\"conversationId\":%d}",
        conversationId
    );
}

public static String audioDone(String url, boolean timeout, String error, Long conversationId) {
    return String.format(
        "{\"type\":\"audio_done\",\"url\":\"%s\",\"timeout\":%b,\"error\":%s,\"conversationId\":%d}",
        url != null ? url : "",
        timeout,
        error != null ? "\"" + error + "\"" : "null",
        conversationId
    );
}
```

2. 修改 `StreamChatService` 调用处，传入 `conversationId`：

```java
// handleLlmStream 方法中
sink.tryEmitNext(SseFormatter.content(escapeJson(chunk), conversationId));
sink.tryEmitNext(SseFormatter.contentDone(conversationId));
sink.tryEmitNext(SseFormatter.audioDone(url, false, null, conversationId));
```

**Android 端修改**：

1. 修改 `parseEvent()` 方法，从 JSON 中解析 `conversationId`：

```kotlin
private fun parseEvent(data: String): StreamEvent? {
    if (data.isBlank()) return null

    val obj = JsonParser.parseString(data).asJsonObject
    val type = obj.get("type")?.asString ?: return null

    // 从 JSON 中解析 conversationId
    val convId = obj.get("conversationId")?.asLong

    return when (type) {
        "content" -> StreamEvent.Content(
            convId,
            obj.get("text")?.asString ?: obj.get("content")?.asString ?: ""
        )

        "content_done" -> StreamEvent.ContentDone(convId)

        "audio_done" -> StreamEvent.AudioDone(
            conversationId = convId,
            url = obj.optString("url").takeIf { it.isNotBlank() },
            timeout = obj.optBoolean("timeout"),
            error = obj.optString("error").takeIf { it.isNotBlank() }
        )

        // ... 其他事件类型同样需要传入 convId

        else -> null
    }
}
```

#### 10.9.5 涉及修改的文件清单

| 层级 | 文件 | 修改内容 |
|------|------|----------|
| 后端 | `SseFormatter.java` | 添加 `conversationId` 参数到各方法 |
| 后端 | `StreamChatService.java` | 调用时传入 `conversationId` |
| Android | `StreamEvent.kt` | `parseEvent()` 方法解析 `conversationId` |

#### 10.9.6 验证方法

1. 启动后端和 Android 应用
2. 创建多个会话
3. 在每个会话发送消息，观察 SSE 事件中的 `conversationId` 是否正确
4. 使用 `adb logcat | grep "SSE"` 查看 Android 端解析的 `conversationId` 值

---

## 十一、状态机转换

### 11.1 TTS 状态机

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

### 11.2 连接状态机

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

---

## 十二、通用 UX 规范

### 12.1 连接状态定义

| 状态 | 说明 | 前端 UI | 安卓端 UI |
|------|------|---------|-----------|
| `idle` | 初始状态 | 隐藏消息气泡 | 隐藏消息气泡 |
| `connecting` | 正在连接 | 骨架屏 + "正在思考..." | CircularProgressIndicator |
| `receiving` | 正在接收内容 | 打字动画 + 光标 | 逐字动画 |
| `done` | 完成 | 停止光标 | 停止动画 |
| `error` | 异常 | 错误提示 + 重试按钮 | Error UI + 重试按钮 |

### 12.2 响应时间阈值

| 阶段 | 最大等待时间 | 用户反馈 |
|------|-------------|----------|
| 首字节到达 | 10 秒 | 持续显示加载动画 |
| 10-30 秒 | - | 显示额外提示（如"AI 正在处理中..."） |
| 30-60 秒 | - | 显示警告（如"响应较慢，请稍候..."） |
| >60 秒 | - | 中断连接，显示超时错误 + 重试按钮 |

### 12.3 加载状态文案

| 场景 | 推荐文案 |
|------|----------|
| 连接中 | "正在思考..." |
| 等待首字节 | "正在思考..." |
| 长时间等待 | "AI 正在处理中，可能需要较长时间..." |
| 超时 | "响应超时，请检查网络后重试" |

### 12.4 错误处理文案

| 错误类型 | 推荐文案 |
|----------|----------|
| 网络错误 | "网络连接失败，请检查网络后重试" |
| 服务器错误 | "服务暂时不可用，请稍后重试" |
| 超时 | "响应超时，AI 可能卡住了" |
| 认证失败 | "登录已过期，请重新登录" |

### 12.5 重试策略

| 策略 | 实现 |
|------|------|
| 自动重试 | 网络错误时可自动重试 1 次 |
| 手动重试 | 服务器错误需用户手动点击重试 |
| 放弃重试 | 认证失败需跳转登录页面 |

---

## 十三、数据库存储规范

### 13.1 audio_url 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `audio_url` | VARCHAR(500) | TTS 生成音频的 URL |

### 13.2 保存时机

`audio_url` 应在 AI 响应生成完成后、保存到数据库时一并存储：

```java
// 消息保存时 audioUrl 初始为 null
saveAssistantMessage(userId, conversationId, finalContent, functionCallJson, null);

// TTS 完成后更新
updateMessageAudioUrl(messageId, audioUrl);
```

---

## 十四、调试日志规范

### 14.1 后端日志

```java
log.info("SSE 事件: type={}, data={}", eventType, jsonStr);
log.info("AI 内容流结束，开始处理 TTS");
log.info("TTS 合成完成，audioUrl={}", audioUrl);
log.warn("TTS 超时（{}ms 无响应）", elapsed);
log.error("TTS 合成失败", e);
```

### 14.2 前端日志

```typescript
console.log('[SSE] 连接已打开')
console.log('[SSE] content:', text)
console.log('[SSE] content_done 收到')
console.log('[SSE] audio_done:', event)
console.log('[SSE] done')
console.log('[SSE] 连接已关闭')
console.error('[SSE] error:', error)
```

### 14.3 安卓端日志

```kotlin
Log.d(TAG, "SSE 连接打开")
Log.d(TAG, "SSE 事件: $data")
Log.d(TAG, "content_done 收到")
Log.d(TAG, "audio_done: url=$url, timeout=$timeout, error=$error")
Log.d(TAG, "SSE 连接关闭")
Log.e(TAG, "SSE 错误", e)
```

---

## 十五、常见问题排查

### 15.1 前端显示 "SSE解析跳过"

**原因**：后端发送的事件缺少 `data:` 前缀

**排查**：
1. 检查 Controller 层是否使用 `SseFormatter.addSsePrefix()` 统一格式化
2. 检查 Service 层是否错误地添加了 `data:` 前缀

### 15.2 audio_url 为 null

**原因**：数据库保存时 audioUrl 参数为 null

**排查**：
1. 检查 `voiceService.textToSpeech()` 是否返回 null
2. 检查 `saveAssistantMessage()` 调用时机
3. 检查讯飞 API 配置是否正确

### 15.3 前端解析 JSON 失败

**原因**：后端发送的 JSON 格式不正确

**排查**：
1. 检查 `escapeJson()` 是否正确转义特殊字符
2. 检查是否有双重 `data:` 前缀

### 15.4 TTS 超时但用户看不到播放按钮

**原因**：audio_done 发送超时标记，但前端没有正确处理

**排查**：
1. 检查 `audio_done` 事件是否正确发送
2. 检查前端 `onAudioDone` 回调是否正确处理 `timeout` 字段
3. 确认消息保存时 audioUrl 是否正确写入数据库

---

## 十六、待办事项

### 16.1 后端

| 待办 | 优先级 | 状态 |
|------|--------|------|
| SseFormatter 新增方法 | **P0** | 待实现 |
| SseFormatter 添加 conversationId 参数 | **P0** | 待实现 |
| StreamChatService 重构 | **P0** | 待实现 |
| TTS Provider 策略模式重构 | **P0** | 待实现 |
| MiniMax TTS 实现 | P1 | 待实现 |
| 长文本智能分块 | P2 | 后续考虑 |

### 16.2 前端

| 待办 | 优先级 | 状态 |
|------|--------|------|
| SseClient 新增事件 | **P0** | 待实现 |
| Message 接口更新 | **P0** | 待实现 |
| ChatRoom 状态管理 | **P0** | 待实现 |

### 16.3 Android

| 待办 | 优先级 | 状态 |
|------|--------|------|
| StreamEvent 新增类型 | **P0** | 待实现 |
| SSE 解析更新 | **P0** | 待实现 |
| Message 状态更新 | **P0** | 待实现 |
| ViewModel 处理 | **P0** | 待实现 |
| conversationId 解析修复 | **P0** | 待实现 |

---

## 附录

### A.1 相关文档

| 文档 | 说明 |
|------|------|
| `SSE_STREAM_SPEC.md` | SSE 流式输出基础规范 |
| `SSE_TTS_ASYNC_SPEC.md` | TTS 异步化方案 |
| `VOICE_PLAYBACK_SPEC.md` | 语音播放实现规范 |

---

**文档版本**: 2.0
**最后更新**: 2026-03-25
