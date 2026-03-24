# 语音播放实现规范

**文档版本**: 1.2
**创建日期**: 2026-03-24
**最后更新**: 2026-03-24
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

后端发送的完整 SSE 事件序列：

```
data: {"type":"content","text":"Hello"}\n\n           ← AI 增量内容
data: {"type":"tool_call","toolCall":{...}}\n\n      ← 工具调用
data: {"type":"tool_result","toolResult":{...}}\n\n ← 工具结果
data: {"type":"content","text":"今天天气不错"}\n\n  ← AI 最终回复
data: {"type":"token_usage","..."}\n\n           ← Token 统计
data: {"type":"audio_url","url":"..."}\n\n       ← TTS 音频 URL（关键！）
data: {"type":"done"}\n\n                       ← 完成标记
```

### 2.5 后端问题与解决方案

| # | 问题 | 严重程度 | 解决方案 |
|---|------|---------|---------|
| P1 | TTS 同步阻塞 SSE 流 | **高** | 方案1: TTS 完全异步化，done 先发<br>方案2: TTS 预生成，并行合成 |
| P2 | TTS Provider 硬编码 | **高** | 实现策略模式，支持讯飞/MiniMax 切换 |
| P3 | 文本超 1000 字符直接截断 | **中** | 方案1: 智能分块 TTS<br>方案2: 长文本流式合成 |
| P4 | TTS 失败无重试机制 | **中** | 添加 1-2 次指数退避重试 |
| P5 | 意图路由的 TTS 也在主流程 | **低** | 统一异步化处理 |

### 2.6 后端解决方案详述

#### 方案 P1-A: TTS 完全异步化（推荐）

```java
// StreamChatService.java 改造
public Flux<String> streamSendMessage(Long userId, SendMessageRequest request) {
    return aiStream
        .flatMap(event -> ...)
        .doOnComplete(() -> {
            // 立即发送 done，不等待 TTS
            saveAssistantMessage(...);  // 保存消息（audioUrl 初始为 null）
        })
        .concatWith(Flux.just(SseFormatter.done()));  // 先发 done

        // TTS 在后台异步生成，通过异步事件推送
        CompletableFuture.runAsync(() -> {
            String audioUrl = voiceService.textToSpeech(finalContent);
            // 通过 Redis Pub/Sub 或 WebSocket 推送 audio_url 事件
            publishAudioUrlEvent(conversationId, audioUrl);
        });
}
```

#### 方案 P1-B: TTS 预生成（备选）

```java
// 在 AI 生成内容时并行触发 TTS
AtomicReference<String> partialContent = new AtomicReference("");

aiStream.subscribe(chunk -> {
    partialContent.set(partialContent.get() + chunk);
    // 预触发 TTS（取最新 500 字符）
    if (partialContent.get().length() > 500) {
        ttsService.preSynthesize(partialContent.get());
    }
});
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

### 3.2 语音播放实现（简化版）

前端**只负责播放**，不负责 TTS 生成。

#### 数据结构

```typescript
interface Message {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  audioUrl?: string          // SSE 流中获取的音频 URL（后端返回）
  isPlaying?: boolean
  pausedAt?: number
  hasPaused?: boolean
}
```

#### 播放按钮显示条件

```html
<!-- 有 audioUrl 才显示播放按钮 -->
<div v-if="msg.role === 'assistant' && msg.audioUrl" class="audio-player">
  <el-button @click="toggleAudio(msg)">
    {{ msg.isPlaying ? '暂停' : '播放' }}
  </el-button>
  <!-- 无 audioUrl → 不显示按钮，客户端不生成 TTS -->
</div>
```

#### 播放流程（简化）

```
用户点击播放
    │
    ├── msg.audioUrl 存在 → 直接播放
    └── msg.audioUrl 为空 → 不显示按钮（前端无 TTS 能力）
```

### 3.3 前端问题与解决方案

| # | 问题 | 严重程度 | 解决方案 |
|---|------|---------|---------|
| F1 | 前端无 TTS 能力 | **高** | 方案1: Web Speech API 降级<br>方案2: 后端统一提供（推荐） |
| F2 | `streamAudioUrl` 与 `audioUrl` 分离容易混淆 | **中** | 统一使用 `audioUrl`，SSE 直接赋值 |
| F3 | 流式响应结束后重新加载消息 | **低** | 设计如此，无需修改 |

#### 方案 F1: 后端统一提供（推荐）

```
后端职责:
  - AI 回复生成后，立即调用 TTS
  - 通过 SSE audio_url 事件返回
  - 无 URL → 前端不显示播放按钮

前端职责:
  - 只负责播放后端返回的 URL
  - 不关心 TTS 来源（讯飞/MiniMax/其他）
```

#### 方案 F1-ALT: Web Speech API 降级（备选）

```typescript
// ChatRoom.vue - 降级方案
const playWithWebSpeech = (text: string) => {
  if ('speechSynthesis' in window) {
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'zh-CN';
    speechSynthesis.speak(utterance);
  } else {
    ElMessage.warning('浏览器不支持语音播放');
  }
};
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

| # | 问题 | 严重程度 | 解决方案 |
|---|------|---------|---------|
| A1 | 认证 URL 需先下载再播放 | **中** | 方案1: 后端统一返回公开 URL<br>方案2: 保留下载逻辑 |
| A2 | 两个独立缓存目录 | **低** | 合并为 `voice_cache/` |
| A3 | 系统 TTS 不支持暂停/继续 | **中** | 记录播放进度，重新播放 |
| A4 | 本地 TTS 与后端 TTS 混杂 | **中** | 明确分层：后端为主，本地为降级 |

#### 方案 A1: 后端统一返回公开 URL（推荐）

```
后端存储策略:
  1. TTS 生成 → 本地文件
  2. 本地文件 → 上传 GitHub（公开可访问）
  3. 返回 GitHub URL（无需认证）

Android 播放流程:
  audioUrl 存在 → GitHub URL → 直接播放（无需下载）
  audioUrl 为空 → 本地 TTS 降级
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

---

## 八、待办事项与优先级

### 后端

| 待办 | 优先级 | 方案选择 |
|------|--------|---------|
| TTS Provider 策略模式重构 | **P0** | 实现 `TtsProvider` 接口 + 工厂 |
| TTS 异步化（解决阻塞） | **P0** | 方案 P1-A: 完全异步化 |
| MiniMax TTS 实现 | P1 | 新增 `MiniMaxTtsProvider` |
| 长文本智能分块 | P2 | 方案 P3-A: 流式合成 |
| TTS 失败重试机制 | P2 | 指数退避重试 1-2 次 |

### 前端

| 待办 | 优先级 | 方案选择 |
|------|--------|---------|
| 统一使用 `audioUrl` | **P0** | 移除 `streamAudioUrl` |
| Web Speech API 降级（可选） | P3 | 方案 F1-ALT |

### Android

| 待办 | 优先级 | 方案选择 |
|------|--------|---------|
| 明确后端 URL 为主，本地 TTS 为降级 | **P0** | 方案 A1 |
| 缓存目录合并 | P2 | 合并为 `voice_cache/` |
| 系统 TTS 暂停/继续优化 | P3 | 重新播放 + 进度记录 |

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

**文档版本**: 1.2
**最后更新**: 2026-03-24
