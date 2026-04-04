# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

MrsHudson（哈德森夫人）是一个 AI 管家助手应用，采用前后端分离 + Android 多端架构：
- **前端**: Vue 3 + TypeScript + Vite + Element Plus (Port 3000)
- **后端**: Spring Boot 3.2 + MyBatis Plus + MySQL 8.0 + Redis (Port 8080)
- **AI**: 双 Provider 架构（Kimi / MiniMax），SSE 流式响应，MCP 工具调用
- **Android**: Kotlin + Jetpack Compose + Hilt + Room (API 26+)

## 常用命令

### 基础设施
```bash
docker-compose up -d mysql redis
```

### 后端 (mrshudson-backend/)
```bash
cd mrshudson-backend
mvn clean compile          # 编译
mvn spring-boot:run        # 运行
mvn clean package           # 打包
mvn test -Dtest=ClassName  # 单个测试类
mvn test -Dtest=ClassName#methodName  # 单个测试方法
```

### 前端 (mrshudson-frontend/)
```bash
cd mrshudson-frontend
npm install     # 安装依赖
npm run dev     # 开发服务器 (localhost:3000)
npm run build   # 构建
```

### Android (mrshudson-android/)
```bash
cd mrshudson-android
./gradlew assembleDebug            # 编译调试版
./gradlew installDebug             # 安装到设备
./gradlew test                     # 单元测试
./gradlew connectedAndroidTest     # 仪器测试
```

## 核心架构

### 逻辑分层架构

```
表现层: StreamChatController (SSE) / ChatController (同步)
         ↓
应用层: StreamChatService — 协调优化层，管理 AI 调用流
         ↓
优化层: IntentRouter / CostOptimizer / ContextManager / QualityOptimizer
         ↓
领域层: ToolRegistry / SelfCorrectingAgent / FallbackHandler / ModelRouter
         ↓
基础设施层: KimiClient / MiniMaxClient / MySQL / Redis
```

**分层隔离原则**: 每层只依赖下层，禁止跨层调用。`SseFormatter` 在 Service 层构建纯 JSON，Controller 层统一添加 `data:` 前缀。

### 双通道对话架构

后端提供两种对话模式，对应不同的 Controller/Service：

| 模式 | Controller | Service | 端点 | 协议 |
|------|-----------|---------|------|------|
| 同步 | ChatController | ChatServiceImpl | POST /api/chat/send | JSON 响应 |
| 流式 | StreamChatController | StreamChatService | POST /api/chat/stream | SSE 流式响应 |

流式模式是主要路径，同步模式为兼容保留。

### SSE 流式调用链

```
POST /api/chat/stream (SSE)
  │
  ├─ ① 保存用户消息到 DB（失败仅日志，不阻断）
  ├─ ② 响应缓存检查 (CostOptimizer → SemanticCacheService)
  │     命中 → cache_hit + content_done + audio_done + done（无 token_usage）
  ├─ ③ 意图路由 (IntentRouter 三层混合)
  │     规则层/轻量AI拦截 → content + content_done + done
  ├─ ④ 置信度评估 → 低置信度返回 clarification + done（无 TTS，不发 content_done/audio_done/token_usage）
  ├─ ⑤ 构建上下文 (ContextManager.buildOptimizedContext)
  │     <15条 → 完整历史；≥15条 → 摘要 + 最近6条
  │     压缩失败 → 降级截取最后 N 条
  ├─ ⑥ Token 计数开始 (TokenTrackerService)
  ├─ ⑦ 质量检测 (QualityOptimizer) — 检测复杂度，记录日志
  └─ ⑧ 流式 AI 调用 (executeStreamingAiCallWithTools)
        ├─ 选择 Provider (AIClientFactory)
        ├─ 解析 chunk:
        │   [THINKING] 前缀 → thinking SSE 事件（不入库、不参与TTS）
        │   [TOOL_CALL] 前缀 → 工具调用流程:
        │     tool_call SSE → ToolRegistry.executeTool() → SelfCorrectingAgent.validate()
        │     → 结果注入 messages → tool_result SSE → continueAiStream() 递归
        │   普通文本 → 累加到 aiFinalContent → content SSE 事件
        ├─ 客户端断开 (doOnCancel):
        │   cancelSink.tryEmitEmpty() → 终止 AI 流
        │   content_done 已发 → TTS 静默继续，完成后写 DB
        │   content_done 未发 → 保存已有部分内容，不启动 TTS
        └─ AI 流正常完成:
              content_done → 异步 TTS(10s超时) → audio_done → token_usage → done
              异步：CostOptimizer.saveCache + 更新会话时间 + 标题生成
```

### 并发与取消模型

| 组件 | 线程模型 | 说明 |
|------|----------|------|
| Controller | Tomcat NIO | 每请求独立线程 |
| Service | Reactor Flux | `Flux<SseEvent>` 异步流，自动背压 |
| TTS 合成 | `CompletableFuture` | 异步，10s 超时保护 |
| 缓存保存 | 异步线程池 | 不阻塞主响应流 |
| 标题生成 | `@Async` | 后台异步执行 |

**取消传播**: `cancelSink` 是请求级局部变量，通过闭包在 `doOnCancel` 中引用。Controller 层无需感知 cancelSink，取消传播由 Service 内部 Flux 处理。

**并发控制**: 同一用户同一时刻只有一条 SSE 流。前端/Android 发送期间输入框 disable，切换会话时必须主动 abort 旧连接（不能依赖自然结束，TTS 等待期间 SSE 可能保持数十秒）。

### 五层拦截架构（成本优化）

```
L1 响应缓存 (SemanticCacheService) — 语义相似度匹配，命中直接返回
L2 意图向量缓存 (已上线) — 三级缓存：内存(5min)→Redis(7d)→向量搜索(30d)
L3 意图路由 (IntentRouter) — 规则→轻量AI→完整AI 三层混合
L4 置信度评估 (IntentConfidenceEvaluator) — 低置信度返回澄清提示
L5 AI 调用 (AIClientFactory) — 兜底，Kimi/MiniMax 双引擎
```

当前实际生效为 L1+L2+L3+L4+L5 完整五层链路。

**意图路由模式**: `rule-only`（纯规则，IntentRouterImpl）/ `hybrid`（三层混合，HybridIntentRouter，默认）/ `ai-only`（无拦截）。

### AI Provider 双引擎

| Provider | 客户端 | reasoning_content | 用途 |
|----------|--------|-------------------|------|
| MiniMax M2.7 | MiniMaxClient | 支持（delta.reasoning_content） | 默认 Provider |
| Kimi moonshot-v1-8k | KimiClient | 不支持 | 备选 |
| Kimi kimi-k2-thinking | KimiClient | 支持（delta.reasoning_content） | 思考模型 |

通过 `AI_PROVIDER` 环境变量切换。两个客户端统一输出 `[THINKING]`/`[TOOL_CALL]` 前缀标记，`StreamChatService.handleChunk()` 统一分发。

### 质量模式与模型路由

**质量模式**（`/api/quality/mode` 切换）:

| 模式 | maxTokens | temperature | 适用场景 |
|------|-----------|-------------|----------|
| SPEED | 400 | 0.2 | 简单问答、闲聊 |
| BALANCED | 800 | 0.3 | 通用场景（默认） |
| QUALITY | 2000 | 0.7 | 复杂分析、创意写作 |

**模型路由策略**（两档）:

| 条件 | 选择模型 | 示例 |
|------|----------|------|
| ≤20字 / 问候语 / 简单问答 | 小模型 (moonshot-v1-8k) | "你好"、"谢谢" |
| 其他 | 标准模型 (moonshot-v1-32k) | 一般对话、复杂分析 |

> **说明**: 当前为两档策略，未来可引入中等模型（如 moonshot-v1-128k）扩展为三档。
> 
> **联动策略**（2026-04-04 已实现）:
> - `QUALITY` 模式 → 强制使用大模型
> - `SPEED` 模式 → 强制使用小模型
> - `BALANCED` 模式 → 根据消息复杂度动态选择
> 
> 通过 `ModelRouter` 注入 `QualityProperties` 实现协调，成本/质量优化现已联动。

### MCP 工具架构

```
ToolRegistry (工具注册中心)
  ├── WeatherTool — 天气查询 (get_weather)
  ├── CalendarTool — 日历管理 (create/get/delete_calendar_event)
  ├── TodoTool — 待办管理 (create/list/complete/delete_todo)
  └── RouteTool — 路线规划 (plan_route)
```

添加新工具：`mcp/` 下创建新 Tool 类实现 `BaseTool` → `ToolRegistry` 注册 → 系统提示词描述用途。

工具调用支持多轮递归，递归深度限制为 `MAX_TOOL_RECURSION_DEPTH = 5`。每轮经 `SelfCorrectingAgent.validate()` 验证结果，失败时纠错重试。

### TTS 策略模式

```
VoiceServiceFacade
  ├── XfyunTtsProvider — 讯飞语音（2-5s）
  ├── MiniMaxTtsProvider — MiniMax TTS（<250ms）
  └── NoOpTtsProvider — 空实现（降级）
```

通过 `voice.tts-provider` 配置切换。关键约束：
- TTS 只合成 `content`，不合成 `thinking`
- 10 秒超时保护（从 `content_done` 开始计时，非请求开始）
- 失败不重试（重试增加 4-10s 延迟，历史会话可兜底）
- `timeout` 时后台继续合成，完成后写 DB，下次加载历史消息可获取

### 三级意图缓存 (Intent Vector Cache)

| 层级 | 实现 | 存储 | Key/查询 | TTL | 命中率 |
|------|------|------|----------|-----|-------|
| L1 | `L1CacheStore` | Caffeine (内存) | `userId:fingerprint` Hash | 5分钟 | ~95% |
| L2 | `L2CacheStore` | Redis Hash | `intent:L2:userId:fingerprint` | 7天 | ~3% |
| L3 | `L3CacheStore` | Redis + 向量搜索 | 遍历+余弦相似度 | 30天 | ~1% |

**查询流程**: L1 → L2 → L3 级联，命中后自动回填上层缓存
**失效策略**: 用户级 `invalidate(userId)` 全层清理
**监控端点**: `/actuator/prometheus` (Spring Boot Actuator)

### 缓存数据结构

| 缓存类型 | 实现 | Key/查询模式 | TTL |
|---------|------|-------------|-----|
| 语义响应缓存 | `SemanticCacheService` → `VectorStore` | 按 userId 隔离，向量相似度匹配 | VectorStore 过期策略，`cleanup()` 清理 |
| 工具结果缓存 | `ToolCacheManager` → Redis | `tool:{toolName}:{paramsHash}` | 按工具类型定制（`OptimProperties` 配置） |
| 意图识别缓存 | `IntentCacheStore` (L1+L2+L3) | 指纹匹配 → 向量相似度搜索 | 分层 TTL |

### 优化层 (optim 包)

| 子系统 | 包路径 | 职责 | 状态 |
|--------|--------|------|------|
| 意图路由 | `optim.intent` | 三层路由 + 置信度评估 | 已上线 |
| 响应缓存 | `optim.cache` + `optim.cost` | 语义缓存(向量相似度) + 工具缓存(Redis) | 已上线 |
| 上下文压缩 | `optim.compress` + `optim.context` | >15条消息压缩为摘要，保留最近6条 | 部分实现（压缩路径未接入主调用链） |
| 自纠错 | `optim.correction` | 工具结果验证 + 纠错重试 | 已上线 |
| 降级兜底 | `optim.fallback` | 工具失败/AI异常兜底 | 已上线 |
| 质量/成本 | `optim.quality` + `optim.cost` | 模型路由 + Token 统计 + 质量模式 | 已上线 |
| 意图缓存 | `optim.intent.cache` | L1+L2+L3 三级缓存 + 熔断器 + Micrometer | 已上线 |

### SSE 事件协议

| 事件 | 用途 | 关键字段 |
|------|------|----------|
| `thinking` | AI 思考过程（增量，可选） | text, conversationId, messageId |
| `content` | 正式回复（增量） | text, conversationId, messageId |
| `content_done` | 文本生成完毕 | conversationId, messageId |
| `tool_call` | 工具调用通知 | toolCall: {name, arguments} |
| `tool_result` | 工具执行结果 | toolResult: {name, result} |
| `audio_done` | TTS 结束 | conversationId, messageId + 以下四状态互斥: |
| | | `url`有值=成功 / `timeout:true`=超时(后台继续) / `error`=异常 / `noaudio:true`=空 |
| `token_usage` | Token 消耗统计 | inputTokens, outputTokens, duration(ms), model |
| `cache_hit` | 缓存命中 | content |
| `clarification` | 意图澄清 | content |
| `error` | 错误 | message |
| `done` | 流结束（必发） | — |

**合法序列**: `[thinking*] → (tool_call → tool_result)* → content+ → content_done → audio_done → [token_usage] → done`

**关键约束**:
- `thinking` 不入库、不参与 TTS、历史消息不含思考过程
- 缓存命中无实际 AI 调用，不发 `token_usage`
- 意图澄清无 TTS，不发 `content_done`/`audio_done`/`token_usage`
- `timeout`/`error`/`noaudio` 均静默处理，不向用户展示错误提示

**响应时间阈值**: 0-10s 显示打字动画；10-30s 额外提示"AI正在处理中"；30-60s "响应较慢"；>60s 超时错误+重试按钮。

### 管理后台 API

| 端点前缀 | 权限 | 端点 |
|---------|------|------|
| `/api/admin/metrics/*` | 管理员 | current(快照)、trend(趋势)、comparison(对比)、reset(重置) |
| `/api/admin/cost/*` | 管理员 | stats(统计)、daily(每日趋势)、today(今日) |
| `/api/admin/cache/*` | 管理员 | clear/semantic、clear/tool、clear/all、stats |
| `/api/admin/quality/*` | 管理员 | GET mode(查询)、PUT mode(切换)、metrics(统计)、reset(重置) |
| ~~`/api/quality/*`~~ | ~~管理员~~ | ~~(已废弃，请迁移到 `/api/admin/quality/*`)~~ |

### 已知限制与待办

| 项目 | 状态 | 影响 |
|------|------|------|
| ContextManager 压缩路径 | **已接入** | 使用 `buildOptimizedContext()` 替代硬编码 LIMIT 20 |
| QualityOptimizer 与 ModelRouter 联动 | **已完成** | ~~各自独立运行~~ 现质量模式影响模型选择 |
| 工具调用递归深度限制 | **已完成** | 限制为 `MAX_TOOL_RECURSION_DEPTH = 5` 层 |
| 意图向量缓存 (L1+L2+L3) | **已完成** | 三级缓存已上线，含 Micrometer 监控 |
| MiniMax TTS 实际 API 集成 | **已完成** | 支持 `speech-2.6-turbo` 等模型，异步轮询模式 |

### 监控指标 (Micrometer/Prometheus)

**Actuator 端点**: `http://localhost:8080/actuator/prometheus`

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `intent_cache_hits_total` | Counter | 各层命中数 (tag: tier=l1/l2/l3) |
| `intent_cache_misses_total` | Counter | 各层未命中数 (tag: tier=l1/l2/l3) |
| `intent_cache_hit_rate` | Gauge | 命中率 0-1 (tag: tier=l1/l2/l3/overall) |
| `intent_cache_latency_seconds` | Timer | 查询延迟分布 (P50/P95/P99) |
| `intent_cache_size` | Gauge | L1 条目数 |
| `intent_cache_memory_bytes` | Gauge | L1 内存估算 |
| `intent_cache_circuit_breaker_state` | Gauge | 熔断器状态 (0=CLOSED, 1=OPEN, 2=HALF_OPEN) |
| `intent_cache_tier_enabled` | Gauge | 层级启用状态 (0/1) |

**管理 API 监控端点**:
| 端点 | 说明 |
|------|------|
| `GET /api/admin/cache/stats` | 缓存统计（命中/大小/延迟） |
| `POST /api/admin/cache/clear/all` | 清空所有缓存 |

详细规范见 [SSE_TTS_UNIFIED_SPEC.md](mds/core/SSE_TTS_UNIFIED_SPEC.md)，架构设计见 [AI_ARCHITECTURE.md](mds/core/AI_ARCHITECTURE.md)。

## JWT 认证

```
登录 → AuthController.login() → AuthServiceImpl.login()
  → 生成 AccessToken(1h) + RefreshToken(7d, Redis)
后续请求 → JwtAuthenticationFilter 验证 → JwtContext(ThreadLocal) 存 Token
```

- `JwtTokenUtil`: Token 生成/验证 (`util/JwtTokenUtil.java`)
- `JwtAuthenticationFilter`: 请求过滤器 (`filter/JwtAuthenticationFilter.java`)
- `JwtContext`: ThreadLocal Token 存储 (`util/JwtContext.java`)
- 管理 API (`/api/admin/**`) 要求管理员角色

## Kimi API 工具调用规范

`assistant` 消息含 `tool_calls` 时 `content` 必须为 `null`（非空字符串）。`tool` 消息不能含 `name` 字段。序列化保留 null 值（`WriteMapNullValue`）。详见 `KimiClient.java` 的 `MESSAGE_NAME_FILTER`。

## 数据库 (MyBatis Plus)

- **实体类**: `domain/entity/`，`@TableName`/`@TableId`/`@TableField` 注解
- **Mapper**: `mapper/` 包，继承 `BaseMapper<T>`
- **自动填充**: `MyMetaObjectHandler` 处理 `created_at`/`updated_at`
- **逻辑删除**: `deleted` 字段，配置在 `application.yml`

核心表：`user`、`conversation`、`chat_message`（含 `audio_url`、`function_call`）、`calendar_event`、`todo_item`、`reminder`。`chat_message` 的 `role` 支持 user/assistant/system/tool。

## 前端架构

```
ChatView.vue (Layout)
├── 左侧：会话列表 sidebar（切换、删除、新建）
├── 底部：导航菜单（对话/日历/待办/天气）
└── 主内容区：<RouterView :key="route.fullPath" />
    ├── ChatRoom.vue — SSE 流式对话（SseClient.ts）
    ├── CalendarView.vue
    ├── TodoView.vue
    └── WeatherView.vue
```

- **状态管理**: Pinia (`stores/user.ts`)
- **HTTP**: Axios (`api/axios.ts`)，baseURL `/api` → Vite 代理到 8080
- **SSE**: `SseClient.ts`，支持 `onThinking`/`onContent`/`onContentDone`/`onAudioDone` 等回调
- **会话传递**: `ChatView` provide → `ChatRoom` inject，切换会话自动 abort 旧 SSE 连接

## Android 架构

MVVM + Clean Architecture + Jetpack Compose：
- **UI**: Compose Screens + ViewModels (LoginViewModel, ChatViewModel, MainViewModel)
- **DI**: Hilt Modules (NetworkModule, DatabaseModule, RepositoryModule, DataStoreModule)
- **网络**: Retrofit + OkHttp + Gson
- **SSE**: `SseClient.kt` 解析 SSE 事件 → `StreamEvent` sealed class → ViewModel 处理
- **音频**: `AudioPlayer.kt` 播放 + `XfyunTtsManager.kt` TTS
- **Token**: `TokenDataStore` JWT 本地存储 + `AuthInterceptor` 自动附加 Header

添加新 Screen：`ui/screens/` 创建 → `MrsHudsonNavHost.kt` 添加路由 → `BottomNavItem.kt` 添加导航

## 环境变量

| 变量 | 必填 | 说明 |
|------|------|------|
| `KIMI_API_KEY` | 是 | Kimi AI API 密钥 (platform.moonshot.cn) |
| `AI_PROVIDER` | 否 | `kimi`(默认) 或 `minimax` |
| `MINIMAX_API_KEY` | 条件 | AI_PROVIDER=minimax 时必填 (platform.minimax.cn) |
| `JWT_SECRET` | 否 | JWT 签名密钥，建议 256 位随机字符串 |
| `WEATHER_API_KEY` | 否 | 高德地图 API 密钥（天气+路线规划） |
| `TTS_PROVIDER` | 否 | TTS 引擎：`xfyun`(默认) / `minimax` |
| `XFYUN_APP_ID/SECRET/KEY` | 否 | 讯飞语音配置 |

## 端口映射

| 服务 | 端口 |
|------|------|
| 前端开发服务器 | 3000 |
| 后端 API | 8080 |
| MySQL | 3306 |
| Redis | 6379 |

## 配置文件

| 文件 | 用途 |
|------|------|
| `.env` | Docker 环境变量 |
| `mrshudson-backend/src/main/resources/application.yml` | 后端配置 |
| `mrshudson-frontend/vite.config.ts` | Vite 构建配置 |
| `mrshudson-android/gradle/libs.versions.toml` | Android 依赖版本 |

## Demo 账号

- 用户名: `admin`，密码: `admin`

## 设计文档

| 文档 | 路径 | 说明 |
|------|------|------|
| AI 架构设计 | `mds/core/AI_ARCHITECTURE.md` | 完整后端架构、调用链、优化层设计 |
| SSE+TTS 规范 | `mds/core/SSE_TTS_UNIFIED_SPEC.md` | SSE 事件协议、TTS 策略、跨端规范 |

---

**文档最后更新**: 2026-04-04
