# 语音播放实现规范

**文档版本**: 1.3
**创建日期**: 2026-03-24
**最后更新**: 2026-03-25
**文档状态**: 草稿

---

## 一、概述

### 1.1 文档目的

本文档详细描述 MrsHudson 项目中语音播报功能在各端（后端、前端、Android）的实现方案，用于：
- 记录当前实现状态
- 追踪各端语音播放的开发、修复和迭代
- 为优化和重构提供参考依据
- 确保多端一致性

### 1.2 语音功能范围

| 功能 | 说明 | 涉及端 |
|------|------|--------|
| 语音识别 (ASR) | 将用户语音转换为文本 | 后端（主要）+ Android（录音）|
| 语音合成 (TTS) | 将 AI 回复文本转换为音频 | 后端 |
| 语音播放 | 播放 TTS 生成的音频 | 前端 + Android |

### 1.3 核心设计原则

```
┌─────────────────────────────────────────────────────────────┐
│                        后端统一提供                           │
│                    audioUrl 统一返回                          │
│                 前端/Android 只负责播放                      │
└─────────────────────────────────────────────────────────────┘
                              │
           ┌──────────────────┴──────────────────┐
           ▼                                     ▼
    ┌─────────────┐                      ┌─────────────┐
    │  前端 Vue    │                      │ Android     │
    │ 只播放 URL   │                      │ 只播放 URL   │
    │ 无本地 TTS   │                      │ 可选本地 TTS │
    └─────────────┘                      └─────────────┘
```

### 1.4 技术选型

| 组件 | 技术方案 | 备注 |
|------|---------|------|
| ASR 提供商 | 讯飞语音 SDK (xfyun) | `IatClient` |
| TTS 提供商 | **策略模式切换** | 讯飞 / MiniMax |
| 流式协议 | SSE (Server-Sent Events) | |
| 前端播放 | HTML5 `<audio>` | 只读播放 |
| Android 播放 | MediaPlayer | 可选本地 TTS 降级 |

---

## 二、后端实现

### 2.1 核心组件

| 文件 | 职责 |
|------|------|
| `VoiceService.java` | 语音服务接口（抽象层） |
| `VoiceServiceImpl.java` | 讯飞 SDK 实现 |
| `MiniMaxTtsService.java` | MiniMax SDK 实现（待新增） |
| `VoiceProperties.java` | 配置属性 |
| `SseFormatter.java` | SSE 事件格式化工具 |
| `StreamChatService.java` | 流式对话服务（含 TTS 调用） |

### 2.2 TTS 提供者抽象

#### 接口定义

**文件**: `VoiceService.java`

```java
public interface VoiceService {
    // 语音识别（保持不变）
    String speechToText(MultipartFile audioFile);
    String speechToText(MultipartFile audioFile, String format, Integer sampleRate);

    // 语音合成
    String textToSpeech(String text);

    // 获取当前 TTS 提供者名称（用于日志）
    String getProviderName();
}
```

#### 提供者枚举

```java
public enum TtsProvider {
    XFYUN("xfyun", "讯飞语音"),
    MINIMAX("minimax", "MiniMax语音"),
    NONE("none", "无提供者");
}
```

#### 工厂选择逻辑

```java
@Service
@RequiredArgsConstructor
public class TtsProviderFactory {
    private final VoiceProperties voiceProperties;

    public VoiceService getTtsService() {
        String provider = voiceProperties.getTtsProvider();

        if ("minimax".equalsIgnoreCase(provider)) {
            return new MiniMaxTtsService(...);  // MiniMax 实现
        } else if ("xfyun".equalsIgnoreCase(provider) || "none".equalsIgnoreCase(provider)) {
            return new VoiceServiceImpl(...);   // 讯飞实现（或空实现）
        }

        // 默认讯飞
        return new VoiceServiceImpl(...);
    }
}
```

### 2.3 配置设计

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

### 2.4 SSE 事件序列（统一规范）

后端发送的 SSE 事件序列：

#### 当前实现（同步 TTS）

```
data: {"type":"content","text":"Hello"}\n\n           ← AI 增量内容
data: {"type":"tool_call","toolCall":{...}}\n\n      ← 工具调用
data: {"type":"tool_result","toolResult":{...}}\n\n ← 工具结果
data: {"type":"content","text":"今天天气不错"}\n\n  ← AI 最终回复
data: {"type":"token_usage","..."}\n\n           ← Token 统计
data: {"type":"audio_url","url":"..."}\n\n       ← TTS 音频 URL（doOnComplete 后同步生成）
data: {"type":"done"}\n\n                       ← 完成标记
```

**问题**：TTS 在 `doOnComplete` 中同步执行，会阻塞 SSE 流。

#### 目标实现（异步 TTS - P1-A）

```
data: {"type":"content","text":"Hello"}\n\n           ← AI 增量内容
data: {"type":"tool_call","toolCall":{...}}\n\n      ← 工具调用
data: {"type":"tool_result","toolResult":{...}}\n\n ← 工具结果
data: {"type":"content","text":"今天天气不错"}\n\n  ← AI 最终回复
data: {"type":"token_usage","..."}\n\n           ← Token 统计
data: {"type":"done"}\n\n                       ← 完成标记（TTS 前置）

[延迟推送 via Redis Pub/Sub]
event: audio_url
data: {"type":"audio_url","url":"..."}\n\n  ← TTS 异步生成后推送
```

前端需额外监听 `audio_url` 事件通道。

### 2.5 后端问题与解决方案

| # | 问题 | 严重程度 | 解决方案 | 状态 |
|---|------|---------|---------|------|
| P1 | TTS 同步阻塞 SSE 流 | **高** | 方案1: TTS 完全异步化，done 先发<br>方案2: TTS 预生成，并行合成 | 待实现 |
| P2 | TTS Provider 硬编码 | **高** | 实现策略模式，支持讯飞/MiniMax 切换 | 待实现 |
| P3 | 文本超 1000 字符直接截断 | **中** | 方案1: 智能语义分块 TTS<br>方案2: 长文本流式合成 | **已解答详述** |
| P4 | ~~TTS 失败无重试机制~~ | — | **已移除**：重试会增加 4-10s 延迟，意义不大 | 不采纳 |
| P5 | 意图路由的 TTS 也在主流程 | **低** | 统一异步化处理（P1-A 方案） | **已解答** |

### 2.6 后端解决方案详述

#### 方案 P1-A: TTS 完全异步化（推荐）

**核心思路**：`done` 先发，TTS 在后台异步生成，通过 Redis Pub/Sub 推送 `audio_url` 事件。

```java
// StreamChatService.java 改造
public Flux<String> streamSendMessage(Long userId, SendMessageRequest request) {
    AtomicReference<String> finalContent = new AtomicReference("");

    return aiStream
        .doOnNext(chunk -> finalContent.set(finalContent.get() + chunk))
        .doOnComplete(() -> {
            // 1. 立即保存消息（audioUrl 初始为 null）
            saveAssistantMessage(userId, conversationId,
                finalContent.get(), functionCallJson, null);
        })
        // 2. 先发 done
        .concatWith(Flux.just(SseFormatter.done()))
        // 3. TTS 在后台异步生成，通过 Redis Pub/Sub 推送
        .thenMany(Flux.defer(() -> {
            CompletableFuture.runAsync(() -> {
                String audioUrl = voiceService.textToSpeech(finalContent.get());
                if (audioUrl != null && !audioUrl.isEmpty()) {
                    // 更新数据库中的 audioUrl
                    updateMessageAudioUrl(conversationId, audioUrl);
                    // 通过 Redis Pub/Sub 推送 audio_url 事件到前端
                    redisPublisher.publish("chat:" + conversationId + ":audio_url", audioUrl);
                }
            });
            return Flux.empty();
        }));
}
```

**前端适配**：需监听 Redis Pub/Sub 通道或使用 WebSocket 接收延迟的 `audio_url` 事件。

#### 方案 P1-B: TTS 预生成（备选）

**核心思路**：AI 生成内容时，并行预触发 TTS。

```java
// 在 AI 生成内容时并行触发 TTS
AtomicReference<String> latestContent = new AtomicReference("");
AtomicReference<String> preAudioUrl = new AtomicReference<>();

aiStream.subscribe(chunk -> {
    latestContent.set(latestContent.get() + chunk);
    // 每积累 500 字符，预触发 TTS
    if (latestContent.get().length() > 500) {
        CompletableFuture.runAsync(() -> {
            String url = voiceService.textToSpeech(latestContent.get());
            if (url != null) preAudioUrl.set(url);
        });
    }
});

// 最终完成后，使用预合成的 audioUrl
.concatWith(Flux.defer(() -> {
    String audioUrl = preAudioUrl.get();
    if (audioUrl != null && !audioUrl.isEmpty()) {
        return Flux.just(SseFormatter.audioUrl(escapeJson(audioUrl)));
    }
    return Flux.empty();
}))
```

#### 方案 P3-A: 智能语义分块 TTS（推荐）

**现状问题**（[VoiceServiceImpl.java:161-163](mrshudson-backend/src/main/java/com/mrshudson/service/impl/VoiceServiceImpl.java#L161-L163)）：
```java
if (text.length() > 1000) {
    truncatedText = text.substring(0, 1000) + "..."; // 简单截断，丢失语义
}
```

**解决方案**：按句子/段落分块，每块不超过 900 字符，合成后拼接。

```java
/**
 * 智能分块：按句子边界分割，每块不超过 maxChars 字符
 */
private List<String> splitIntoSentences(String text, int maxChars) {
    List<String> chunks = new ArrayList<>();
    StringBuilder current = new StringBuilder();

    // 按中文标点分割
    String[] sentences = text.split("[。！？.!?\n]");

    for (String sentence : sentences) {
        if (sentence.trim().isEmpty()) continue;

        if (current.length() + sentence.length() <= maxChars) {
            current.append(sentence.trim()).append("。");
        } else {
            if (current.length() > 0) {
                chunks.add(current.toString());
                current = new StringBuilder();
            }
            // 单句超过限制，按字数再分割
            if (sentence.length() > maxChars) {
                for (int i = 0; i < sentence.length(); i += maxChars - 50) {
                    int end = Math.min(i + maxChars - 50, sentence.length());
                    chunks.add(sentence.substring(i, end) + "...");
                }
            } else {
                current.append(sentence.trim()).append("。");
            }
        }
    }
    if (current.length() > 0) {
        chunks.add(current.toString());
    }
    return chunks;
}

/**
 * 分块合成后拼接
 */
public String textToSpeech(String text) {
    if (text == null || text.isEmpty()) return null;

    // 单块直接合成
    if (text.length() <= 1000) {
        return synthesize(text);
    }

    List<String> chunks = splitIntoSentences(text, 900);
    List<String> audioFiles = new ArrayList<>();

    for (int i = 0; i < chunks.size(); i++) {
        String audioPath = synthesize(chunks.get(i));
        if (audioPath == null) {
            log.error("TTS 分块 {} 合成失败", i + 1);
            return null;
        }
        audioFiles.add(audioPath);
        log.info("TTS 分块 {}/{} 完成", i + 1, chunks.size());
    }

    return mergeAudioFiles(audioFiles); // 合并多个音频文件
}
```

#### 方案 P2: TTS Provider 策略模式

```java
public interface TtsProvider {
    String synthesize(String text);
    String getProviderName();
    boolean isConfigured();
}

@Service
public class XfyunTtsProvider implements TtsProvider { ... }
@Service
public class MiniMaxTtsProvider implements TtsProvider { ... }

@Service
@RequiredArgsConstructor
public class CompositeTtsService implements VoiceService {
    private final List<TtsProvider> providers;
    private final VoiceProperties properties;

    @Override
    public String textToSpeech(String text) {
        String provider = properties.getTtsProvider();
        TtsProvider selected = providers.stream()
            .filter(p -> p.getProviderName().equalsIgnoreCase(provider))
            .findFirst()
            .orElse(noOpProvider());

        if (!selected.isConfigured()) {
            log.warn("TTS Provider {} 未配置或配置不完整", provider);
            return null;
        }

        return selected.synthesize(text);
    }
}
```

---

## 三、前端实现 (Vue)

### 3.1 核心组件

| 文件 | 职责 |
|------|------|
| `ChatRoom.vue` | 聊天房间，包含消息列表和输入框 |
| `VoiceInputButton.vue` | 语音输入（录音）组件 |
| `SseClient.ts` | SSE 统一客户端 |
| `api/chat.ts` | 聊天 API 定义 |

### 3.2 语音播放实现（统一规范）

前端**只负责播放**，不负责 TTS 生成。

#### 数据结构

```typescript
interface Message {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  audioUrl?: string          // SSE 流中获取的音频 URL（后端返回）
  isPlaying?: boolean
  pausedAt?: number          // 暂停位置（秒）
  hasPaused?: boolean         // 是否曾暂停过
  // ✅ 已移除 streamAudioUrl，统一使用 audioUrl
}
```

#### 播放按钮显示条件

```html
<!-- 规则：有 audioUrl 才显示播放按钮，无则不显示 -->
<div v-if="msg.role === 'assistant' && msg.audioUrl" class="audio-player">
  <!-- 主按钮：从头播放 -->
  <el-button @click="toggleAudio(msg)" circle>
    {{ msg.isPlaying ? '暂停' : '从头播放' }}
  </el-button>
  <!-- 继续播放按钮：暂停后显示 -->
  <el-button v-if="msg.hasPaused && !msg.isPlaying" @click="resumeAudio(msg)">
    继续
  </el-button>
</div>
```

#### 播放控制（已完成 ✅）

| 操作 | 方法 | 说明 |
|------|------|------|
| 从头播放 | `toggleAudio()` | 暂停当前播放，从头开始 |
| 继续播放 | `resumeAudio()` | 从暂停位置继续 |
| 暂停 | `toggleAudio()` | 暂停并记录位置 |

#### 播放流程

```
用户点击播放
    │
    ├── msg.audioUrl 存在 → 直接播放
    └── msg.audioUrl 为空 → 不显示按钮（前端无 TTS 能力）
```

### 3.3 前端问题与解决方案

| # | 问题 | 严重程度 | 解决方案 | 状态 |
|---|------|---------|---------|------|
| F1 | 前端无 TTS 能力 | **高** | 后端统一提供（推荐） | 进行中 |
| F2 | `streamAudioUrl` 与 `audioUrl` 分离容易混淆 | **中** | 统一使用 `audioUrl` | 待实现 |
| F3 | 流式响应结束后重新加载消息 | **中** | TTS 异步化后，`audio_url` 通过独立事件通道推送，无需 reload | **已解答** |
| F4 | 暂停/继续/从头播放 | — | **✅ 已实现**（[ChatRoom.vue:581-643](mrshudson-frontend/src/views/ChatRoom.vue#L581-L643)） | 完成 |

#### F3 问题详解

**现状**（[ChatRoom.vue:420-441](mrshudson-frontend/src/views/ChatRoom.vue#L420-L441)）：
```javascript
onDone: () => {
    doneReceived = true
},
onClose: () => {
    // SSE 结束后，重新加载消息列表以获取最新数据
    await loadConversationMessages(conversationId.value)
    // 恢复 streamAudioUrl...
}
```

**原因**：
1. SSE 流中 AI 消息是增量更新的占位符（`id: 'stream-' + timestamp`）
2. 消息需要持久化到数据库，获得真实 ID
3. 重新加载是为了获取**服务端落库的消息**（含 `audioUrl`）

**问题**：如果后端 TTS 异步化（`done` 时 audioUrl 尚未生成），重新加载也拿不到 audioUrl。

**解决方案**：TTS 异步化后，`audio_url` 事件通过 **Redis Pub/Sub / WebSocket** 独立通道推送，前端直接更新对应消息的 `audioUrl`，无需重新加载消息列表。

#### 方案 F1: 后端统一提供（推荐）

```
后端职责:
  - AI 回复生成后，立即调用 TTS（或异步生成后推送）
  - 通过 SSE audio_url 事件返回（或通过 Pub/Sub 推送）
  - 无 URL → 前端不显示播放按钮

前端职责:
  - 只负责播放后端返回的 URL
  - 不关心 TTS 来源（讯飞/MiniMax/其他）
```

---

## 四、Android 实现

### 4.1 核心组件

| 组件 | 文件 | 职责 |
|------|------|------|
| `AudioPlayer` | `AudioPlayer.kt` | 音频播放管理（单例） |
| `XfyunTtsManager` | `XfyunTtsManager.kt` | 本地讯飞 TTS（可降级） |
| `TtsButton` | `TtsButton.kt` | 播放按钮 UI |
| `MessageBubble` | `MessageBubble.kt` | 消息气泡（含 TTS 按钮） |
| `GitHubAudioCache` | `GitHubAudioCache.kt` | GitHub 音频缓存 |

### 4.2 语音播放流程（简化版）

Android 端**优先播放后端 URL**，本地 TTS 作为**降级方案**。

```
AudioPlayer.playOrPause(message)
    │
    ├── message.audioUrl 存在
    │       │
    │       ├─► GitHub URL → 本地缓存或下载
    │       ├─► 服务端 URL → 下载（需认证）
    │       └─► 公开 URL → 直接播放
    │
    └── message.audioUrl 为空
            │
            └─► 本地 TTS 降级（可选功能）
                    ├─► 讯飞 API → synthesizeByXfyunApi()
                    └─► 系统 TTS → speakWithSystemTts()
```

### 4.3 Android 问题与解决方案

| # | 问题 | 严重程度 | 解决方案 | 状态 |
|---|------|---------|---------|------|
| A1 | 认证 URL 需先下载再播放 | **中** | 方案1: 后端统一返回公开 URL（推荐）<br>方案2: 保留下载逻辑 | 进行中 |
| A2 | 两个独立缓存目录 | **低** | 合并为 `voice_cache/` | 待实现 |
| A3 | 系统 TTS 不支持暂停/继续 | **中** | 记录播放进度，重新播放 | ✅ 已实现 |
| A4 | 本地 TTS 与后端 TTS 混杂 | **中** | 明确分层：后端为主，本地为降级 | **已解答** |
| A5 | 暂停/继续/从头播放 | — | **✅ 已实现**（[AudioPlayer.kt:283-352](mrshudson-android/.../AudioPlayer.kt#L283-L352)） | 完成 |

#### 方案 A1: 后端统一返回公开 URL（推荐）

**播放按钮显示规则**：
```
backendUrl 非空 → 显示播放按钮
backendUrl 为空 → 不显示播放按钮
  （无需本地 TTS 作为降级，依赖后端稳定性）
```

**Android 保留本地 TTS 作为降级的理由**：
1. 网络不稳定时，后端 TTS 请求可能超时/失败
2. 离线场景需要 TTS
3. 用户体验：即使后端失败，仍能提供基本 TTS 能力

**播放流程**：
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

---

## 五、TTS Provider 切换架构

### 5.1 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                    VoiceServiceFacade                        │
│                   （对外统一接口）                            │
└─────────────────────────────────────────────────────────────┘
                              │
         ┌──────────────────┼──────────────────┐
         ▼                  ▼                  ▼
┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│ XfyunTts    │      │MiniMaxTts   │      │ NoOpTts    │
│ Provider    │      │ Provider    │      │ Provider    │
└─────────────┘      └─────────────┘      └─────────────┘
```

### 5.2 接口定义

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

    /**
     * 异步合成语音
     * @param text 文本
     * @param callback 完成回调
     */
    void synthesizeAsync(String text, TtsCallback callback);
}

/**
 * TTS 回调
 */
public interface TtsCallback {
    void onSuccess(String audioUrl);
    void onError(String message);
}
```

### 5.3 配置切换

```yaml
voice:
  # 切换provider: xfyun / minimax / none
  tts-provider: ${TTS_PROVIDER:xfyun}
```

切换时只需修改配置，无需修改代码。

---

## 六、规范化要求

### 6.1 后端统一返回规范

| 字段 | 类型 | 说明 |
|------|------|------|
| `audio_url` | string | TTS 音频 URL，有则返回，无则为 `null` |

```
SSE 事件:
data: {"type":"audio_url","url":"https://raw.githubusercontent.com/..."}\n\n
data: {"type":"audio_url","url":null}\n\n  ← 不显示播放按钮
```

### 6.2 前端处理规范

```
onAudioUrl: (url: string | null) => {
  if (url && url.length > 0) {
    msg.audioUrl = url
    // 显示播放按钮
  } else {
    msg.audioUrl = null
    // 不显示播放按钮
  }
}
```

### 6.3 Android 处理规范

```
when (event) {
  is StreamEvent.AudioUrl -> {
    if (event.url.isNotBlank()) {
      message.audioUrl = event.url
      // 显示播放按钮
    } else {
      message.audioUrl = null
      // 不显示播放按钮，可选启用本地 TTS 降级
    }
  }
}
```

---

## 七、变更记录

| 日期 | 版本 | 变更内容 |
|------|------|---------|
| 2026-03-24 | 1.0 | 初始版本，记录当前实现状态和问题 |
| 2026-03-24 | 1.1 | 新增 MiniMax TTS 方案分析 |
| 2026-03-24 | 1.2 | 完善各端问题与解决方案，统一规范 |
| 2026-03-25 | 1.3 | 解答 P1/P3/P5 详述；移除 P4 重试机制；明确 F3/A4 问题；标注已完成功能（暂停/继续/从头播放）|

---

## 八、待办事项与优先级

### 后端

| 待办 | 优先级 | 方案选择 | 状态 |
|------|--------|---------|------|
| TTS Provider 策略模式重构 | **P0** | 实现 `TtsProvider` 接口 + 工厂 | 待实现 |
| TTS 异步化（解决阻塞） | **P0** | 方案 P1-A: 完全异步化 | 待实现 |
| MiniMax TTS 实现 | P1 | 新增 `MiniMaxTtsProvider` | 待实现 |
| 长文本智能分块 | P2 | 方案 P3-A: 智能语义分块 | **已设计方案** |
| 意图路由 TTS 异步化 | P1 | 统一 P1-A 方案 | 与 P1 合并 |

### 前端

| 待办 | 优先级 | 方案选择 | 状态 |
|------|--------|---------|------|
| 统一使用 `audioUrl` | **P0** | 移除 `streamAudioUrl` | 待实现 |
| SSE 后监听 `audio_url` 事件 | P1 | Redis Pub/Sub / WebSocket | 与 P1 合并 |
| 暂停/继续/从头播放 | — | — | ✅ **已完成** |

### Android

| 待办 | 优先级 | 方案选择 | 状态 |
|------|--------|---------|------|
| 明确后端 URL 为主，本地 TTS 为降级 | **P0** | 方案 A1 | 已明确 |
| 缓存目录合并 | P2 | 合并为 `voice_cache/` | 待实现 |
| 暂停/继续/从头播放 | — | — | ✅ **已完成** |

---

## 九、方案决策矩阵

### 核心决策

| 决策点 | 推荐方案 | 备选方案 |
|--------|---------|---------|
| TTS Provider 切换 | 策略模式 + 配置驱动 | 硬编码判断 |
| 前端 TTS | 后端统一提供 | Web Speech API |
| 后端 TTS 阻塞 | 完全异步化 | 预生成 |
| 音频存储 | GitHub 公开 URL | 本地存储 + 认证下载 |

### 决策依据

| 方案 | 优点 | 缺点 | 适用场景 |
|------|------|------|---------|
| 后端统一 TTS | 简单可控，多端一致 | 增加后端负载 | 生产环境（推荐） |
| 前端 Web Speech | 无需后端调用 | 质量差，不支持所有浏览器 | 降级方案 |
| 本地 TTS（Android） | 无需网络 | 效果一般 | 离线/降级 |

---

**文档版本**: 1.3
**最后更新**: 2026-03-25
