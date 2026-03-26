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
| `content` | AI 增量内容 | `text`: string, `conversationId`: number, `messageId`: number |
| `content_done` | AI 文本内容结束 | `conversationId`: number, `messageId`: number |
| `audio_done` | TTS 语音合成结束 | `conversationId`: number, `messageId`: number, `url`: string, `timeout`: boolean, `error`: string, `noaudio`: boolean |
| `token_usage` | Token 统计 | `inputTokens`, `outputTokens`, `duration`, `model` |
| `tool_call` | 工具调用 | `toolCall`: {name, arguments} |
| `tool_result` | 工具执行结果 | `toolResult`: {name, result} |
| `cache_hit` | 缓存命中 | `content`: string |
| `clarification` | 澄清提示 | `content`: string |
| `error` | 错误信息 | `message`: string, `conversationId`: number (可选) |
| `done` | 流式完成 | `conversationId`: number (可选) |

> **重要**：
> - 所有消息相关事件（`content`、`content_done`、`audio_done`）必须包含 `conversationId` 和 `messageId` 字段
> - `messageId` 用于将 SSE 事件精确关联到对应的消息气泡（前端 disable 期间不会有新消息，但 messageId 仍是最可靠的关联方式）
> - 发送期间前端输入框必须 disable，后端串行处理，不支持并发发送
> - `error` 和 `done` 事件的 `conversationId` 为可选，如果为 null 则关联到当前活跃会话

### 3.2 content_done 事件

```json
{"type":"content_done","conversationId":123,"messageId":456}
```

表示 AI 文本已全部生成，TTS 可在后台开始。

### 3.3 audio_done 事件

```json
// TTS 成功
{"type":"audio_done","conversationId":123,"messageId":456,"url":"https://example.com/audio/xxx.mp3"}

// TTS 超时（超过 10 秒）
// 超时后后端继续合成，url 将异步写入数据库，下次加载历史消息可获取
{"type":"audio_done","conversationId":123,"messageId":456,"timeout":true}

// TTS 失败（合成过程中抛出异常）
{"type":"audio_done","conversationId":123,"messageId":456,"error":"TTS_FAILED"}

// TTS 成功但返回 URL 为空（提供商返回空结果）
{"type":"audio_done","conversationId":123,"messageId":456,"noaudio":true}
```

**各状态前端/Android 处理规则**：

| 字段 | 含义 | 展示行为 | 数据库 audioUrl |
|------|------|----------|----------------|
| `url` 有值 | TTS 成功 | 显示播放按钮 | 已写入 |
| `timeout: true` | 超时，后台继续合成 | 不显示按钮，audioUrl 置空 | 合成完成后异步写入 |
| `error` 有值 | 合成异常 | 不显示按钮，audioUrl 置空 | null |
| `noaudio: true` | 提供商返回空 | 不显示按钮，audioUrl 置空 | null |

> `timeout` 与 `noaudio` 的区别：`timeout` 表示后台合成仍在进行，历史消息可能有 URL；`noaudio` 表示合成已完成但无结果，历史消息也不会有 URL。

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

data: {"type":"content","text":"今天天气","conversationId":1,"messageId":2}\n\n
data: {"type":"content","text":"很不错","conversationId":1,"messageId":2}\n\n
data: {"type":"tool_call","toolCall":{...}}\n\n
      ← 工具调用
data: {"type":"tool_result","toolResult":{...}}\n\n
      ← 工具结果
data: {"type":"content","text":"适合出门。","conversationId":1,"messageId":2}\n\n
                                                              ↑
                                                    content_done（TTS 开始后台执行）
data: {"type":"content_done","conversationId":1,"messageId":2}\n\n


                                                             [TTS 合成中...]
                                                             [2-5秒后完成]

data: {"type":"audio_done","url":"https://...","conversationId":1,"messageId":2}\n\n
      ← TTS 完成，展示播放按钮
data: {"type":"done"}\n\n
                              ← SSE 连接关闭
```

### 4.2 超时场景

```
data: {"type":"content_done","conversationId":1,"messageId":2}\n\n


                                                             [TTS 合成中...]
                                                             [超过 10 秒]

data: {"type":"audio_done","timeout":true,"conversationId":1,"messageId":2}\n\n
      ← 超时不展示按钮，后台继续合成
data: {"type":"done"}\n\n
```

### 4.3 失败场景

```
data: {"type":"content_done","conversationId":1,"messageId":2}\n\n


                                                             [TTS 合成中...]
                                                             [失败]

data: {"type":"audio_done","error":"TTS_FAILED","conversationId":1,"messageId":2}\n\n
      ← 失败，不展示按钮
data: {"type":"done"}\n\n
```

### 4.4 意图路由场景

意图路由的响应也走异步 TTS 流程，避免同步阻塞。

```
data: {"type":"content","text":"今天天气不错","conversationId":1,"messageId":2}\n\n
data: {"type":"content_done","conversationId":1,"messageId":2}\n\n


                                                             [TTS 合成中...]

data: {"type":"audio_done","url":"https://...","conversationId":1,"messageId":2}\n\n
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
     * 示例输入: SseEvent(type="content", text="Hello", conversationId=1, messageId=2)
     * 示例输出: data: {"type":"content","text":"Hello","conversationId":1,"messageId":2}\n\n
     */
    public static String format(SseEvent event) { ... }

    /**
     * 格式化 JSON 字符串为 SSE 字符串
     * 示例输入: {"type":"done"}
     * 示例输出: data: {"type":"done"}\n\n
     */
    public static String format(String jsonStr) {
        return "data: " + jsonStr + "\n\n";
    }

    /**
     * Flux JSON → Flux SSE 转换（Controller 层统一调用）
     * 示例: flux.transform(SseFormatter::addSsePrefix)
     */
    public static Flux<String> addSsePrefix(Flux<String> flux) {
        return flux.map(SseFormatter::format);
    }

    // ========== 事件构建方法 ==========

    /**
     * AI 增量内容
     * @param text 增量文本片段
     * @param conversationId 会话 ID
     * @param messageId 消息 ID（用于将 SSE 事件精确关联到对应消息气泡）
     */
    public static String content(String text, Long conversationId, Long messageId) {
        return "{\"type\":\"content\",\"text\":\"" + escapeJson(text) + "\""
             + ",\"conversationId\":" + conversationId
             + ",\"messageId\":" + messageId + "}";
    }

    /**
     * AI 内容结束
     * 表示 AI 文本已全部生成，TTS 可在后台开始
     * @param conversationId 会话 ID
     * @param messageId 消息 ID
     */
    public static String contentDone(Long conversationId, Long messageId) {
        return "{\"type\":\"content_done\""
             + ",\"conversationId\":" + conversationId
             + ",\"messageId\":" + messageId + "}";
    }

    /**
     * TTS 语音合成结束
     *
     * @param url     音频 URL；超时/失败/noaudio 时传 null
     * @param timeout 是否超时（超时后后台继续合成，历史消息可能有 URL）
     * @param error   异常信息；超时时传 null
     * @param conversationId 会话 ID
     * @param messageId 消息 ID
     */
    public static String audioDone(String url, boolean timeout, String error,
                                   Long conversationId, Long messageId) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"audio_done\"");
        sb.append(",\"conversationId\":").append(conversationId);
        sb.append(",\"messageId\":").append(messageId);
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
    Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

    // 订阅 AI 流
    aiStream.subscribe(
        chunk -> {
            finalContent.set(finalContent.get() + chunk);

            if (chunk.startsWith("[TOOL_CALL]")) {
                sink.tryEmitNext(parseToolCall(chunk));
            } else {
                sink.tryEmitNext(SseFormatter.content(escapeJson(chunk), conversationId, messageId));
            }
        },
        error -> sink.tryEmitError(error),
        () -> {
            // ========== AI 内容流结束 ==========
            log.info("AI 内容流结束，开始处理 TTS");

            // 1. 保存消息（audioUrl 初始为 null）
            String functionCallJson = toolCallInfos.isEmpty() ? null : JSON.toJSONString(toolCallInfos);
            Long messageId = saveAssistantMessage(userId, conversationId,
                finalContent.get(), functionCallJson, null);

            // 2. 发送 content_done
            sink.tryEmitNext(SseFormatter.contentDone(conversationId, messageId));

            // 3. 后台异步执行 TTS（带超时）
            // 注意：orTimeout 从 runAsync 提交时开始计时，线程池繁忙时实际 TTS 执行时间可能少于 10s
            CompletableFuture<String> ttsFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return voiceService.textToSpeech(finalContent.get());
                } catch (Exception e) {
                    log.error("TTS 合成失败", e);
                    return null;
                }
            });

            ttsFuture.orTimeout(TTS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
              .thenAccept(audioUrl -> {
                  // thenAccept 在正常完成时触发（包括 TTS 返回 null 的情况）
                  if (audioUrl != null && !audioUrl.isEmpty()) {
                      // TTS 成功，更新数据库并通知前端
                      updateMessageAudioUrl(messageId, audioUrl);
                      sink.tryEmitNext(SseFormatter.audioDone(audioUrl, false, null, conversationId, messageId));
                  } else {
                      // TTS 返回空（noaudio），不写入数据库
                      sink.tryEmitNext(SseFormatter.audioDone(null, false, null, conversationId, messageId));
                  }
                  sink.tryEmitNext(SseFormatter.done());
                  sink.tryEmitComplete();
              })
              .exceptionally(ex -> {
                  if (ex.getCause() instanceof TimeoutException) {
                      // 超时：立即返回 timeout，后台继续合成并写入数据库
                      log.warn("TTS 超时（{}ms），后台继续合成", TTS_TIMEOUT_MS);
                      sink.tryEmitNext(SseFormatter.audioDone(null, true, null, conversationId, messageId));
                      sink.tryEmitNext(SseFormatter.done());
                      sink.tryEmitComplete();
                      // 超时后继续等待 TTS 完成，将 url 写入数据库供历史消息使用
                      ttsFuture.thenAccept(audioUrl -> {
                          if (audioUrl != null && !audioUrl.isEmpty()) {
                              updateMessageAudioUrl(messageId, audioUrl);
                              log.info("TTS 超时后完成，audioUrl 已写入数据库，messageId={}", messageId);
                          }
                      });
                  } else {
                      // 其他异常（非超时）
                      log.error("TTS 异常", ex);
                      sink.tryEmitNext(SseFormatter.audioDone(null, false, "TTS_FAILED", conversationId, messageId));
                      sink.tryEmitNext(SseFormatter.done());
                      sink.tryEmitComplete();
                  }
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
    Long messageId = saveAssistantMessage(userId, conversationId, response, null, null);

    Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

    // 1. 发送内容
    sink.tryEmitNext(SseFormatter.content(escapeJson(response), conversationId, messageId));
    sink.tryEmitNext(SseFormatter.contentDone(conversationId, messageId));

    // 2. 后台 TTS（带超时）
    CompletableFuture<String> ttsFuture = CompletableFuture.supplyAsync(() -> {
        try {
            return voiceService.textToSpeech(response);
        } catch (Exception e) {
            log.error("意图路由 TTS 失败", e);
            return null;
        }
    });

    ttsFuture.orTimeout(TTS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
      .thenAccept(audioUrl -> {
          if (audioUrl != null && !audioUrl.isEmpty()) {
              updateMessageAudioUrl(messageId, audioUrl);
              sink.tryEmitNext(SseFormatter.audioDone(audioUrl, false, null, conversationId, messageId));
          } else {
              sink.tryEmitNext(SseFormatter.audioDone(null, false, null, conversationId, messageId));
          }
          sink.tryEmitNext(SseFormatter.done());
          sink.tryEmitComplete();
      })
      .exceptionally(ex -> {
          if (ex.getCause() instanceof TimeoutException) {
              log.warn("意图路由 TTS 超时，后台继续合成");
              sink.tryEmitNext(SseFormatter.audioDone(null, true, null, conversationId, messageId));
              sink.tryEmitNext(SseFormatter.done());
              sink.tryEmitComplete();
              ttsFuture.thenAccept(audioUrl -> {
                  if (audioUrl != null && !audioUrl.isEmpty()) {
                      updateMessageAudioUrl(messageId, audioUrl);
                      log.info("意图路由 TTS 超时后完成，audioUrl 已写入数据库，messageId={}", messageId);
                  }
              });
          } else {
              log.error("意图路由 TTS 异常", ex);
              sink.tryEmitNext(SseFormatter.audioDone(null, false, "TTS_FAILED", conversationId, messageId));
              sink.tryEmitNext(SseFormatter.done());
              sink.tryEmitComplete();
          }
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
计时开始（10s，从 runAsync 提交时起）
       │
       ├── TTS 在 10s 内完成
       │       ├── url 有值 ──► audio_done(url)，写入数据库
       │       └── url 为空 ──► audio_done(noaudio)，不写入数据库
       │
       └── TTS 超过 10s ─────► audio_done(timeout=true)，立即返回 done
                                    │
                                    └── 后台继续合成
                                            ├── 完成 → 写入数据库，历史消息可获取
                                            └── 失败 → 不写入，历史消息无 URL
```

### 6.4 无重试机制说明

TTS 失败时不重试，原因：
- 重试会增加 4-10s 延迟
- 用户体验反而更差
- 历史会话可兜底，下次加载可获取 audioUrl

---

## 七、历史会话重获取

### 7.1 重获取机制

SSE 超时时，消息已保存到数据库（audioUrl 初始为 null），后台 TTS 继续合成，完成后异步写入 audioUrl。下次加载历史消息时前端可获取。

```
SSE 流程（超时场景）：
content_done ──► TTS 后台合成 ──► audio_done(timeout=true) ──► done
                      │
                      └── 后台继续合成...
                              │
                              ▼
                      TTS 完成 → updateMessageAudioUrl(messageId, url)
                              │
                              ▼
                      数据库 audioUrl 已写入

前端下次加载历史消息：
    │
    ├── GET /api/conversations/{id}/messages → 返回含 audioUrl 的消息列表
    └── 前端根据 audioUrl 是否为 null 决定是否显示播放按钮
```

### 7.2 历史消息接口规范

历史消息接口必须返回 `audioUrl` 字段，前端根据该字段设置 `ttsStatus`：

```java
// MessageController.java
@GetMapping("/conversations/{conversationId}/messages")
public Result<List<MessageVO>> getMessages(@PathVariable Long conversationId) {
    // 返回的 MessageVO 包含 audioUrl 字段
}

// MessageVO.java
public class MessageVO {
    private Long id;
    private String role;
    private String content;
    private String audioUrl;   // null 表示无音频
    private LocalDateTime createdAt;
}
```

### 7.3 前端历史消息加载规范

加载历史消息时，根据 `audioUrl` 初始化 `ttsStatus`：

```typescript
// 加载历史消息后，根据 audioUrl 设置 ttsStatus
function mapHistoryMessage(msg: MessageVO): Message {
  return {
    id: String(msg.id),
    role: msg.role,
    content: msg.content,
    audioUrl: msg.audioUrl || null,
    // 历史消息：有 audioUrl 则 ready，否则置空不显示按钮
    ttsStatus: msg.audioUrl ? 'ready' : 'no_audio',
    isPlaying: false,
    pausedAt: 0,
    hasPaused: false,
    createdAt: msg.createdAt
  }
}
```

**历史消息 ttsStatus 规则**：

| audioUrl | ttsStatus | 说明 |
|----------|-----------|------|
| 有值 | `ready` | 显示播放按钮 |
| null | `no_audio` | 不显示按钮（超时后合成失败，或 noaudio） |

> `no_audio` 是历史消息专用状态，区别于实时流中的 `timeout`/`error`/`noaudio`，统一表示"无可播放音频"。

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
```

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
  onFirstContent?: () => void    // 收到首个 content 事件

  // 事件回调
  onContent?: (text: string, conversationId: number, messageId: number) => void
  onContentDone?: (conversationId: number, messageId: number) => void
  onAudioDone?: (event: AudioDoneEvent) => void
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
  conversationId: number | null
  messageId: number | null
  url: string | null      // 音频 URL，仅 TTS 成功时有值
  timeout: boolean        // 超时（后台继续合成，历史消息可能有 URL）
  error: string | null    // 合成异常信息
  noaudio: boolean        // 提供商返回空（历史消息也不会有 URL）
}
```

### 9.3 SSE 解析更新

```typescript
class SseClient {
  private handleEvent(data: any): void {
    const conversationId: number | null = data.conversationId ?? null
    const messageId: number | null = data.messageId ?? null

    switch (data.type) {
      case 'content':
        this.options.onContent?.(data.text || data.content || '', conversationId!, messageId!)
        break

      case 'content_done':
        this.options.onContentDone?.(conversationId!, messageId!)
        break

      case 'audio_done':
        this.options.onAudioDone?.({
          conversationId,
          messageId,
          url: data.url || null,
          timeout: data.timeout || false,
          error: data.error || null,
          noaudio: data.noaudio || false
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
  id: string                    // 对应后端 messageId
  role: 'user' | 'assistant' | 'system'
  content: string
  audioUrl?: string | null      // SSE 流或历史消息中获取的音频 URL
  ttsStatus: TtsStatus          // TTS 状态
  isPlaying: boolean            // 是否正在播放
  pausedAt: number              // 暂停位置（秒），0 表示未暂停
  hasPaused: boolean            // 是否曾暂停过（用于显示"继续"按钮）
  createdAt: string
}

type TtsStatus =
  | 'pending'      // AI 还在生成内容
  | 'synthesizing' // content_done 已收到，等待 audio_done
  | 'ready'        // audio_done 收到，有 URL，可播放
  | 'timeout'      // 超时，后台继续合成，audioUrl 置空
  | 'error'        // 合成异常，audioUrl 置空
  | 'noaudio'      // 提供商返回空，audioUrl 置空
  | 'no_audio'     // 历史消息专用：audioUrl 为 null，不显示按钮
```

### 9.5 TTS 状态说明

| ttsStatus | 来源 | 显示内容 | audioUrl | 说明 |
|-----------|------|---------|----------|------|
| `pending` | 实时流 | 无 | — | AI 还在生成内容 |
| `synthesizing` | 实时流 | 🔊 语音合成中... | — | content_done 已收到，等待 audio_done |
| `ready` | 实时流 / 历史 | 播放按钮 | 有值 | 可播放 |
| `timeout` | 实时流 | 无 | null | 超时，后台继续合成，历史消息可能有 URL |
| `error` | 实时流 | 无 | null | 合成异常 |
| `noaudio` | 实时流 | 无 | null | 提供商返回空，历史消息也不会有 URL |
| `no_audio` | 历史消息 | 无 | null | 历史消息无音频，统一不显示按钮 |

### 9.6 ChatRoom 状态管理

发送期间输入框必须 disable，SSE 完成（`done` 事件收到）后才恢复可用。后端串行处理，不支持并发发送。

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
    inputDisabled.value = true   // 发送开始，禁用输入框
  },

  onFirstContent: () => {
    chatState.value.status = 'receiving'
  },

  onContent: (text, conversationId, messageId) => {
    // 通过 messageId 精确定位消息气泡
    const msg = getMessageById(String(messageId))
    if (msg) {
      msg.content += text
    }
  },

  onContentDone: (conversationId, messageId) => {
    const msg = getMessageById(String(messageId))
    if (msg) {
      msg.ttsStatus = 'synthesizing'
    }
  },

  onAudioDone: (event: AudioDoneEvent) => {
    const msg = event.messageId ? getMessageById(String(event.messageId)) : null
    if (!msg) return

    if (event.timeout) {
      msg.ttsStatus = 'timeout'
      msg.audioUrl = null
    } else if (event.error) {
      msg.ttsStatus = 'error'
      msg.audioUrl = null
    } else if (event.noaudio) {
      msg.ttsStatus = 'noaudio'
      msg.audioUrl = null
    } else if (event.url) {
      msg.ttsStatus = 'ready'
      msg.audioUrl = event.url
    }
  },

  onDone: () => {
    chatState.value.status = 'done'
    inputDisabled.value = false  // SSE 完成，恢复输入框
  },

  onError: (error: string) => {
    chatState.value.status = 'error'
    chatState.value.errorMessage = error
    inputDisabled.value = false  // 出错也要恢复输入框
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
      <!-- 未播放过 或 已播放完毕：显示"播放"按钮 -->
      <el-button v-if="!msg.isPlaying && !msg.hasPaused" @click="playAudio(msg)" circle>
        播放
      </el-button>
      <!-- 正在播放：显示"暂停"按钮 -->
      <el-button v-if="msg.isPlaying" @click="pauseAudio(msg)" circle>
        暂停
      </el-button>
      <!-- 已暂停：显示"继续"和"从头播放"按钮 -->
      <template v-if="msg.hasPaused && !msg.isPlaying">
        <el-button @click="resumeAudio(msg)">继续</el-button>
        <el-button @click="replayAudio(msg)">从头播放</el-button>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
const showAudioButton = (msg: Message) => {
  return msg.role === 'assistant' && msg.ttsStatus === 'ready' && msg.audioUrl
}

// 从头播放
function playAudio(msg: Message) {
  audioElement.currentTime = 0
  audioElement.src = msg.audioUrl!
  audioElement.play()
  msg.isPlaying = true
  msg.hasPaused = false
  msg.pausedAt = 0
}

// 暂停，记录位置
function pauseAudio(msg: Message) {
  msg.pausedAt = audioElement.currentTime
  audioElement.pause()
  msg.isPlaying = false
  msg.hasPaused = true
}

// 从暂停位置继续
function resumeAudio(msg: Message) {
  audioElement.currentTime = msg.pausedAt
  audioElement.play()
  msg.isPlaying = true
}

// 从头重新播放
function replayAudio(msg: Message) {
  msg.hasPaused = false
  msg.pausedAt = 0
  playAudio(msg)
}
</script>
```

### 9.8 播放控制

| 操作 | 方法 | 触发条件 | 说明 |
|------|------|---------|------|
| 从头播放 | `playAudio()` | 未播放过 | 设置 `currentTime=0`，开始播放 |
| 暂停 | `pauseAudio()` | 正在播放 | 记录 `pausedAt`，设置 `hasPaused=true` |
| 继续 | `resumeAudio()` | 已暂停 | 从 `pausedAt` 位置继续 |
| 从头重播 | `replayAudio()` | 已暂停 | 重置 `pausedAt=0`，`hasPaused=false`，重新播放 |

---

## 十、Android 实现

### 10.1 核心组件

| 组件 | 文件 | 职责 |
|------|------|------|
| `AudioPlayer` | `AudioPlayer.kt` | 音频播放管理（单例） |
| `XfyunTtsManager` | `XfyunTtsManager.kt` | 本地讯飞 TTS（可降级） |
| `TtsButton` | `TtsButton.kt` | 播放按钮 UI |
| `MessageBubble` | `MessageBubble.kt` | 消息气泡（含 TTS 按钮） |

### 10.2 StreamEvent 新增类型

**文件**: `data/repository/StreamEvent.kt`

```kotlin
sealed class StreamEvent {
    abstract val conversationId: Long?

    /** AI 增量内容事件 */
    data class Content(
        override val conversationId: Long?,
        val messageId: Long?,
        val text: String
    ) : StreamEvent()

    /** AI 内容结束事件 */
    data class ContentDone(
        override val conversationId: Long?,
        val messageId: Long?
    ) : StreamEvent()

    /** TTS 语音合成结束事件 */
    data class AudioDone(
        override val conversationId: Long?,
        val messageId: Long?,
        val url: String?,
        val timeout: Boolean,
        val error: String?,
        val noaudio: Boolean
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

    // 从 JSON 中解析 conversationId 和 messageId
    val convId = obj.get("conversationId")?.takeIf { !it.isJsonNull }?.asLong
    val msgId = obj.get("messageId")?.takeIf { !it.isJsonNull }?.asLong

    // conversationId 为 null 时，关联到当前活跃会话
    val resolvedConvId = convId ?: activeConversationId

    return when (type) {
        "content" -> StreamEvent.Content(
            resolvedConvId,
            msgId,
            obj.get("text")?.asString ?: obj.get("content")?.asString ?: ""
        )

        "content_done" -> StreamEvent.ContentDone(resolvedConvId, msgId)

        "audio_done" -> StreamEvent.AudioDone(
            conversationId = resolvedConvId,
            messageId = msgId,
            url = obj.get("url")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() },
            timeout = obj.get("timeout")?.asBoolean ?: false,
            error = obj.get("error")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() },
            noaudio = obj.get("noaudio")?.asBoolean ?: false
        )

        "token_usage" -> StreamEvent.TokenUsage(
            resolvedConvId,
            obj.get("inputTokens")?.asInt ?: 0,
            obj.get("outputTokens")?.asInt ?: 0,
            obj.get("duration")?.asLong ?: 0L,
            obj.get("model")?.asString ?: "unknown"
        )

        "tool_call" -> {
            val toolCall = obj.getAsJsonObject("toolCall")
            if (toolCall != null) {
                StreamEvent.ToolCall(
                    resolvedConvId,
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
                    resolvedConvId,
                    toolResult.get("id")?.asString ?: "",
                    toolResult.get("name")?.asString ?: "",
                    toolResult.get("result")?.asString ?: ""
                )
            } else null
        }

        "cache_hit" -> StreamEvent.CacheHit(resolvedConvId, obj.get("content")?.asString ?: "")
        "clarification" -> StreamEvent.Clarification(resolvedConvId, obj.get("content")?.asString ?: "")
        "error" -> StreamEvent.Error(resolvedConvId, obj.get("message")?.asString ?: "Unknown error")
        "done" -> StreamEvent.Done(resolvedConvId)

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
    val ttsStatus: TtsStatus = TtsStatus.PENDING,
    val isPlaying: Boolean = false,
    val pausedAt: Float = 0f,      // 暂停位置（秒）
    val hasPaused: Boolean = false  // 是否曾暂停过（用于显示"继续"按钮）
)

enum class TtsStatus {
    PENDING,        // AI 还在生成
    SYNTHESIZING,   // content_done 已收到，等待 audio_done
    READY,          // audio_done 收到，有 URL，可播放
    TIMEOUT,        // 超时，后台继续合成，audioUrl 置空
    ERROR,          // 合成异常，audioUrl 置空
    NOAUDIO,        // 提供商返回空，audioUrl 置空
    NO_AUDIO        // 历史消息专用：audioUrl 为 null，不显示按钮
}
```

### 10.5 ViewModel 处理

```kotlin
class ChatViewModel(
    private val chatRepository: ChatRepository
) : ViewModel() {
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    fun onContentDone(messageId: Long?) {
        updateMessage(messageId) { it.copy(ttsStatus = TtsStatus.SYNTHESIZING) }
    }

    fun onAudioDone(event: StreamEvent.AudioDone) {
        updateMessage(event.messageId) { msg ->
            msg.copy(
                audioUrl = event.url,
                ttsStatus = when {
                    event.timeout -> TtsStatus.TIMEOUT
                    event.error != null -> TtsStatus.ERROR
                    event.noaudio -> TtsStatus.NOAUDIO
                    event.url != null -> TtsStatus.READY
                    else -> TtsStatus.ERROR
                }
            )
        }
    }

    // 播放控制
    fun playAudio(messageId: Long) {
        updateMessage(messageId) { it.copy(isPlaying = true, hasPaused = false, pausedAt = 0f) }
    }

    fun pauseAudio(messageId: Long, position: Float) {
        updateMessage(messageId) { it.copy(isPlaying = false, hasPaused = true, pausedAt = position) }
    }

    fun resumeAudio(messageId: Long) {
        updateMessage(messageId) { it.copy(isPlaying = true) }
    }

    fun replayAudio(messageId: Long) {
        updateMessage(messageId) { it.copy(isPlaying = true, hasPaused = false, pausedAt = 0f) }
    }

    private fun updateMessage(messageId: Long?, transform: (Message) -> Message) {
        if (messageId == null) return
        _messages.update { msgs ->
            msgs.map { if (it.id == messageId) transform(it) else it }
        }
    }
}
```

### 10.6 UI 显示

```kotlin
@Composable
fun AudioPlayButton(message: Message, viewModel: ChatViewModel) {
    when (message.ttsStatus) {
        TtsStatus.READY -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    message.isPlaying -> {
                        // 正在播放：显示暂停按钮
                        IconButton(onClick = { viewModel.pauseAudio(message.id, audioPlayer.currentPosition) }) {
                            Icon(Icons.Default.Pause, contentDescription = "暂停")
                        }
                    }
                    message.hasPaused -> {
                        // 已暂停：显示继续和从头播放
                        IconButton(onClick = { viewModel.resumeAudio(message.id) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "继续")
                        }
                        TextButton(onClick = { viewModel.replayAudio(message.id) }) {
                            Text("从头播放", fontSize = 12.sp)
                        }
                    }
                    else -> {
                        // 未播放：显示播放按钮
                        IconButton(onClick = { viewModel.playAudio(message.id) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "播放")
                        }
                    }
                }
            }
        }
        TtsStatus.SYNTHESIZING -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(4.dp))
                Text("语音合成中...", fontSize = 12.sp)
            }
        }
        // TIMEOUT / ERROR / NOAUDIO / NO_AUDIO：不显示任何内容
        else -> { }
    }
}
```

### 10.7 语音播放流程

```
AudioPlayer.play(message)
    │
    ├── message.audioUrl 非空
    │       │
    │       ├─► 服务端 URL → 下载（需认证）
    │       └─► 公开 URL → 直接播放
    │
    └── message.audioUrl 为空 → 不显示播放按钮
            （如需离线能力，可选启用本地 TTS 降级）
```

### 10.8 播放控制

| 操作 | 方法 | 触发条件 | 说明 |
|------|------|---------|------|
| 从头播放 | `playAudio()` | 未播放过 | 重置位置，开始播放 |
| 暂停 | `pauseAudio()` | 正在播放 | 记录 `pausedAt`，设置 `hasPaused=true` |
| 继续 | `resumeAudio()` | 已暂停 | 从 `pausedAt` 位置继续 |
| 从头重播 | `replayAudio()` | 已暂停 | 重置 `pausedAt=0`，`hasPaused=false`，重新播放 |

### 10.9 conversationId 与 messageId 处理规范

所有消息相关事件（`content`、`content_done`、`audio_done`）均携带 `conversationId` 和 `messageId`。

**串行发送约定**：前端发送期间输入框 disable，后端不支持并发处理，同一时刻只有一条 SSE 流在运行。

**Android 端处理规则**：

| 场景 | 处理方式 |
|------|---------|
| `conversationId` 有值 | 精确匹配对应会话 |
| `conversationId` 为 null | 关联到当前活跃会话（`activeConversationId`） |
| `messageId` 有值 | 精确定位消息气泡 |
| `messageId` 为 null | 降级为关联当前会话最后一条 assistant 消息 |

**验证方法**：
1. 创建多个会话，在每个会话发送消息
2. 观察 SSE 事件中的 `conversationId` 和 `messageId` 是否正确
3. 使用 `adb logcat | grep "SSE"` 查看 Android 端解析结果

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
                    /          |           |           \
                   /           |           |            \
                  /            |           |             \
    ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
    │ audio_done   │  │ audio_done   │  │ audio_done   │  │ audio_done   │
    │ url 有值     │  │ timeout=true │  │ error=xxx    │  │ noaudio=true │
    └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘
           │                  │                 │                 │
           ▼                  ▼                 ▼                 ▼
    ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
    │ READY        │  │ TIMEOUT      │  │ ERROR        │  │ NOAUDIO      │
    │ 显示播放按钮  │  │ 不显示按钮    │  │ 不显示按钮    │  │ 不显示按钮    │
    │ audioUrl有值 │  │ audioUrl=null│  │ audioUrl=null│  │ audioUrl=null│
    │              │  │ 后台继续合成  │  │              │  │ 历史也无URL  │
    └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘
           │                  │                 │                 │
           └──────────────────┼─────────────────┴─────────────────┘
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
| 首字节到达后 10-30 秒 | 20 秒 | 显示额外提示（如"AI 正在处理中..."） |
| 首字节到达后 30-60 秒 | 30 秒 | 显示警告（如"响应较慢，请稍候..."） |
| 首字节到达后 >60 秒 | 60 秒 | 中断连接，显示超时错误 + 重试按钮 |

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
| SseFormatter 新增方法（含 conversationId、messageId 参数） | **P0** | 待实现 |
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
| 发送期间输入框 disable / SSE 完成后恢复 | **P0** | 待实现 |

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

| 文档 | 路径 | 说明 |
|------|------|------|
| `SSE_STREAM_SPEC.md` | `mds/SSE_STREAM_SPEC.md` | SSE 流式输出基础规范 |
| `SSE_TTS_ASYNC_SPEC.md` | `mds/SSE_TTS_ASYNC_SPEC.md` | TTS 异步化方案 |
| `VOICE_PLAYBACK_SPEC.md` | `mds/VOICE_PLAYBACK_SPEC.md` | 语音播放实现规范 |

---

**文档版本**: 2.0
**最后更新**: 2026-03-25
