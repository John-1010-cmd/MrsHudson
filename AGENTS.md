# MrsHudson AI管家 - 项目指南

## 项目概述

**MrsHudson（哈德森夫人）** 是一款AI管家助手应用，灵感源自《福尔摩斯探案集》中的房东哈德森太太。项目采用前后端分离 + Android 多端架构，通过自然语言对话帮助用户管理日常事务，包括天气查询、日程管理、待办事项、智能提醒、路线规划等功能。

### 核心价值

> "让每个现代人都能拥有自己的私人管家"

### 技术架构概览

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Vue 3 前端    │────▶│  Spring Boot    │────▶│   MySQL 8.0     │
│  (Port 3000)    │     │   后端服务      │     │   主数据存储    │
│  Element Plus   │◀────│  (Port 8080)    │◀────│                 │
│    Pinia        │     │                 │     │   Redis 7.0     │
└─────────────────┘     └─────────────────┘     │   缓存/会话     │
                │                               └─────────────────┘
                │                                        │
┌───────────────┘                               ┌─────────────────┐
│ Android App                                   │  Kimi/MiniMax   │
│ (Jetpack Compose)                             │   AI API        │
└───────────────────────────────────────────────┴─────────────────┘
```

---

## 技术栈详情

### 后端 (mrshudson-backend/)

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 编程语言 |
| Spring Boot | 3.2.3 | 主框架 |
| Spring Security | 6.x | 认证授权 (JWT) |
| Spring WebFlux | 6.x | 响应式流和SSE |
| MyBatis Plus | 3.5.5 | ORM 数据访问 |
| MySQL Connector | 8.x | 数据库驱动 |
| Redis | 7.x | 缓存与会话 |
| Lombok | - | 代码简化 |
| FastJSON2 | 2.0.46 | JSON 处理 |
| JJWT | 0.12.3 | JWT Token 处理 |
| Firebase Admin | 9.2.0 | Android 推送 |
| 讯飞语音 SDK | 2.0.3 | 语音识别/合成 |

### 前端 (mrshudson-frontend/)

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.4.21 | 前端框架 |
| TypeScript | 5.3.3 | 类型系统 |
| Vite | 5.1.5 | 构建工具 |
| Element Plus | 2.5.6 | UI 组件库 |
| Pinia | 2.1.7 | 状态管理 |
| Vue Router | 4.3.0 | 路由管理 |
| Axios | 1.6.7 | HTTP 客户端 |
| Element Plus Icons | 2.3.2 | 图标库 |
| ECharts | 5.5.0 | 图表库 |

### Android (mrshudson-android/)

| 技术 | 版本 | 用途 |
|------|------|------|
| Kotlin | 1.9.x | 编程语言 |
| Android SDK | 34 (target) | 目标平台 |
| minSdk | 26 | 最低支持 Android 8.0 |
| Jetpack Compose | BOM 2024.x | UI 框架 |
| Hilt | 2.50 | 依赖注入 |
| Room | 2.6.x | 本地数据库 |
| Retrofit | 2.9.x | 网络请求 |
| DataStore | 1.0.x | 偏好存储 |
| Firebase Messaging | BOM 32.x | 推送通知 |
| WorkManager | 2.9.x | 后台任务 |

---

## 项目结构

### 后端目录结构

```
rshudson-backend/
├── pom.xml                           # Maven 构建配置
├── Dockerfile                        # 后端容器构建文件
└── src/
    ├── main/
    │   ├── java/com/mrshudson/
    │   │   ├── MrshudsonApplication.java    # 应用入口
    │   │   ├── ai/                          # AI 服务抽象层
    │   │   │   ├── AIClientFactory.java
    │   │   │   ├── AIProvider.java
    │   │   │   ├── AIService.java
    │   │   │   └── impl/                    # Kimi/MiniMax 实现
    │   │   ├── config/                      # 配置类
    │   │   │   ├── AIProperties.java        # AI 模型配置
    │   │   │   ├── AsyncConfig.java         # 异步线程池
    │   │   │   ├── DotenvConfig.java        # 环境变量加载
    │   │   │   ├── SecurityConfig.java      # Spring Security + JWT
    │   │   │   ├── WebClientConfig.java     # WebFlux 配置
    │   │   │   ├── WebConfig.java           # Web 配置 (CORS)
    │   │   │   ├── mybatis/                 # MyBatis Plus 配置
    │   │   │   └── GlobalExceptionHandler.java
    │   │   ├── controller/                  # REST API 控制器
    │   │   │   ├── AuthController.java      # 认证接口 (JWT)
    │   │   │   ├── ChatController.java      # AI 对话接口 (同步)
    │   │   │   ├── StreamChatController.java # SSE 流式对话
    │   │   │   ├── CalendarController.java  # 日历管理
    │   │   │   ├── TodoController.java      # 待办事项
    │   │   │   ├── WeatherController.java   # 天气查询
    │   │   │   ├── RouteController.java     # 路线规划
    │   │   │   ├── ReminderController.java  # 提醒管理
    │   │   │   ├── PushController.java      # 推送接口
    │   │   │   ├── CostStatsController.java # 成本统计
    │   │   │   └── MetricsController.java   # 性能指标
    │   │   ├── domain/                      # 领域模型
    │   │   │   ├── entity/                  # 实体类 (MyBatis Plus)
    │   │   │   │   ├── User.java
    │   │   │   │   ├── ChatMessage.java
    │   │   │   │   ├── Conversation.java    # 会话管理
    │   │   │   │   ├── CalendarEvent.java
    │   │   │   │   ├── TodoItem.java
    │   │   │   │   ├── Reminder.java
    │   │   │   │   └── AiCostRecord.java    # AI成本记录
    │   │   │   └── dto/                     # 数据传输对象
    │   │   ├── mapper/                      # MyBatis Plus Mapper
    │   │   ├── service/                     # 业务逻辑层
    │   │   │   ├── impl/                    # 服务实现类
    │   │   │   └── tts/                     # TTS 提供商实现
    │   │   ├── mcp/                         # MCP 工具层
    │   │   │   ├── BaseTool.java
    │   │   │   ├── ToolRegistry.java
    │   │   │   ├── kimi/                    # Kimi AI 客户端
    │   │   │   ├── minimax/                 # MiniMax AI 客户端
    │   │   │   ├── weather/WeatherTool.java
    │   │   │   ├── calendar/CalendarTool.java
    │   │   │   ├── todo/TodoTool.java
    │   │   │   └── route/RouteTool.java     # 路线规划
    │   │   ├── optim/                       # AI 调用优化层
    │   │   │   ├── cache/                   # 语义缓存
    │   │   │   ├── compress/                # 对话压缩
    │   │   │   ├── context/                 # 上下文管理
    │   │   │   ├── correction/              # 自纠错机制
    │   │   │   ├── cost/                    # 成本监控
    │   │   │   ├── fallback/                # 降级处理
    │   │   │   ├── intent/                  # 意图路由
    │   │   │   ├── monitor/                 # 监控告警
    │   │   │   ├── quality/                 # 质量优化
    │   │   │   ├── token/                   # Token 追踪
    │   │   │   └── wrapper/                 # 优化包装器
    │   │   ├── filter/                      # 过滤器
    │   │   │   └── JwtAuthenticationFilter.java
    │   │   ├── util/                        # 工具类
    │   │   │   ├── JwtTokenUtil.java        # JWT 工具
    │   │   │   ├── JwtContext.java          # JWT 上下文
    │   │   │   └── SseFormatter.java        # SSE 格式化
    │   │   └── job/                         # 定时任务
    │   │       └── ReminderJob.java
    │   └── resources/
    │       ├── application.yml              # 应用配置
    │       └── db/migration/                # 数据库初始化脚本
    │           └── V1__init_schema.sql
    └── test/                                # 测试代码
        └── java/com/mrshudson/optim/        # 优化层集成测试
```

### 前端目录结构

```
mrshudson-frontend/
├── package.json               # npm 依赖配置
├── vite.config.ts             # Vite 构建配置
├── tsconfig.json              # TypeScript 配置
├── Dockerfile                 # 前端容器构建文件
├── nginx.conf                 # Nginx 配置
└── src/
    ├── main.ts                # 应用入口
    ├── App.vue                # 根组件
    ├── router/
    │   └── index.ts           # 路由配置
    ├── stores/
    │   ├── index.ts           # Pinia 配置
    │   └── user.ts            # 用户状态管理 (Token)
    ├── api/                   # API 封装
    │   ├── axios.ts           # Axios 实例 (JWT 拦截)
    │   ├── auth.ts            # 认证接口
    │   ├── chat.ts            # 对话接口
    │   ├── calendar.ts        # 日历接口
    │   ├── todo.ts            # 待办接口
    │   ├── metrics.ts         # 指标接口
    │   └── ...
    ├── views/                 # 页面组件
    │   ├── LoginView.vue      # 登录页
    │   ├── ChatView.vue       # 主布局 (含会话侧边栏)
    │   ├── ChatRoom.vue       # 对话界面
    │   ├── CalendarView.vue   # 日历视图
    │   ├── TodoView.vue       # 待办列表
    │   ├── RouteView.vue      # 路线规划
    │   ├── ReminderView.vue   # 提醒视图
    │   ├── MetricsView.vue    # 成本指标
    │   └── WeatherView.vue    # 天气视图
    ├── components/            # 可复用组件
    │   ├── EventDialog.vue
    │   ├── TodoItem.vue
    │   ├── ReminderItem.vue
    │   └── VoiceInputButton.vue
    └── utils/sse/
        └── SseClient.ts       # SSE 流式客户端
```

### Android 目录结构

```
mrshudson-android/
├── app/
│   ├── build.gradle.kts       # 应用构建配置
│   └── src/main/java/com/mrshudson/android/
│       ├── MainActivity.kt
│       ├── MrsHudsonApplication.kt
│       ├── di/                # Hilt 依赖注入模块
│       │   ├── DatabaseModule.kt
│       │   ├── NetworkModule.kt
│       │   ├── RepositoryModule.kt
│       │   └── DataStoreModule.kt
│       ├── data/
│       │   ├── local/         # 本地数据 (Room + DataStore)
│       │   │   ├── dao/
│       │   │   ├── database/
│       │   │   ├── datastore/
│       │   │   └── entity/
│       │   ├── remote/        # 远程 API (Retrofit)
│       │   │   ├── *.kt       # API 接口定义
│       │   │   └── dto/       # 数据传输对象
│       │   └── repository/    # 仓库实现
│       ├── domain/model/      # 领域模型
│       ├── ui/
│       │   ├── screens/       # 屏幕组件
│       │   │   ├── login/
│       │   │   ├── main/
│       │   │   ├── chat/
│       │   │   ├── calendar/
│       │   │   ├── todo/
│       │   │   ├── route/
│       │   │   ├── reminder/
│       │   │   ├── weather/
│       │   │   └── settings/
│       │   ├── components/    # 可复用组件
│       │   ├── navigation/    # 导航配置
│       │   └── theme/         # 主题配置
│       └── service/
│           └── FcmService.kt  # Firebase 推送服务
├── build.gradle.kts           # 项目构建配置
├── settings.gradle.kts
└── gradle/libs.versions.toml  # 版本目录
```

---

## 构建与运行

### 环境要求

- **后端**: JDK 17+, Maven 3.8+
- **前端**: Node.js 18+
- **Android**: Android Studio Hedgehog+, Android SDK 34
- **基础设施**: Docker & Docker Compose

### 环境变量配置

复制 `.env.example` 为 `.env` 并配置：

```bash
# 必填: AI API 密钥
KIMI_API_KEY=your-kimi-api-key-here
# 或 MiniMax
MINIMAX_API_KEY=your-minimax-api-key-here
AI_PROVIDER=kimi  # 可选: kimi, minimax

# 可选但建议配置: 天气和路线规划
WEATHER_API_KEY=your-amap-api-key-here

# 可选: JWT 密钥（生产环境建议配置）
JWT_SECRET=your-256-bit-secret-key

# 可选: 讯飞语音（用于语音功能）
XFYUN_APP_ID=
XFYUN_API_SECRET=
XFYUN_API_KEY=
```

### 基础设施启动

```bash
# 启动 MySQL + Redis
docker-compose up -d mysql redis

# 服务地址:
#   - MySQL: localhost:3306
#   - Redis: localhost:6379
```

### 后端开发

```bash
cd mrshudson-backend

# 编译
mvn clean compile

# 运行
mvn spring-boot:run

# 打包
mvn clean package

# 运行测试
mvn test
mvn test -Dtest=OptimIntegrationTest

# 后端 API: http://localhost:8080
```

### 前端开发

```bash
cd mrshudson-frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产版本
npm run build

# 预览生产构建
npm run preview

# 前端: http://localhost:3000
```

### Android 开发

```bash
cd mrshudson-android

# 编译调试版本
./gradlew assembleDebug

# 编译发布版本
./gradlew assembleRelease

# 安装到设备
./gradlew installDebug

# 运行测试
./gradlew test
./gradlew connectedAndroidTest
```

### 完整 Docker 部署

```bash
# 构建并启动所有服务
docker-compose up -d

# 服务地址:
#   - 前端: http://localhost:80
#   - 后端: http://localhost:8080
#   - MySQL: localhost:3306
#   - Redis: localhost:6379
```

---

## 核心架构设计

### AI 对话双通道架构

后端提供两种对话模式：

| 模式 | Controller | Service | 协议 |
|------|-----------|---------|------|
| 同步 | ChatController | ChatServiceImpl | POST /api/chat/send，JSON 响应 |
| 流式 | StreamChatController | StreamChatService | POST /api/chat/stream，SSE 流式响应 |

流式模式是主要路径，同步模式为兼容保留。

### 五层拦截架构（成本优化）

```
用户请求
  │
  ▼
┌─────────────────────────────────────────────────┐
│ L1 响应缓存 (SemanticCacheService)               │  ← 命中直接返回，跳过全部后续
│   语义相似度匹配                                 │
└──────────────┬──────────────────────────────────┘
               │ 未命中
  ▼
┌─────────────────────────────────────────────────┐
│ L2 意图向量缓存 (IntentCacheStore)    [已上线]   │  ← 命中返回 IntentResult
│   三级缓存：内存(5min) → Redis(7d) → 向量搜索(30d) │
└──────────────┬──────────────────────────────────┘
               │ 未命中
  ▼
┌─────────────────────────────────────────────────┐
│ L3 意图路由 (IntentRouter)                       │  ← 规则/轻量AI 拦截简单查询
│   三层混合：规则 → 轻量AI → 完整AI               │
└──────────────┬──────────────────────────────────┘
               │ 未拦截
  ▼
┌─────────────────────────────────────────────────┐
│ L4 置信度评估 (IntentConfidenceEvaluator)        │  ← 置信度不足返回澄清提示
│   低置信度 → clarification，不调用 AI             │
└──────────────┬──────────────────────────────────┘
               │ 置信度充足
  ▼
┌─────────────────────────────────────────────────┐
│ L5 AI 调用 (AIClientFactory)                     │  ← 兜底：前四层均未拦截时执行
│   Kimi / MiniMax 双引擎                          │
└─────────────────────────────────────────────────┘
```

> **当前状态**：五层架构全部已上线，实际生效 L1 + L2 + L3 + L4 + L5 完整链路。

### 三级意图缓存 (Intent Vector Cache)

| 层级 | 实现 | 存储 | TTL | 命中率 |
|------|------|------|-----|--------|
| L1 | `L1CacheStore` | Caffeine (内存) | 5分钟 | ~95% |
| L2 | `L2CacheStore` | Redis Hash | 7天 | ~3% |
| L3 | `L3CacheStore` | Redis Vector (HNSW) | 30天 | ~1% |

**查询流程**: L1 → L2 → L3 级联，命中后自动回填上层缓存  
**失效策略**: 用户级 `invalidate(userId)` 全层清理  
**监控端点**: `/actuator/prometheus`

### 意图路由三层架构

| 层级 | 模型类型 | Token 消耗 | 用途 |
|------|---------|-----------|------|
| L1 规则层 | 无 AI 调用 | 0 | 关键词匹配 |
| L2 轻量AI层 | 轻量模型 | 20-50 tokens | 简单意图识别 |
| L3 完整AI层 | 完整模型 | 50-100 tokens | 复杂意图分类 |
| 主流程生成调用 | 完整模型 + 完整上下文 | 500-2000 tokens | 最终回复生成 |

### AI Provider 双引擎

| Provider | 客户端 | reasoning_content | 用途 |
|----------|--------|-------------------|------|
| MiniMax M2.7 | MiniMaxClient | 支持 | 默认 Provider |
| Kimi moonshot-v1-8k | KimiClient | 不支持 | 备选 |
| Kimi kimi-k2-thinking | KimiClient | 支持 | 思考模型 |

通过 `AI_PROVIDER` 环境变量切换。两个客户端统一输出 `[THINKING]`/`[TOOL_CALL]` 前缀标记，`StreamChatService` 统一处理。

### MCP 工具架构

```
ToolRegistry (工具注册中心)
  ├── WeatherTool — 天气查询 (高德 API)
  ├── CalendarTool — 日历事件管理
  ├── TodoTool — 待办事项管理
  └── RouteTool — 路线规划 (高德 API)
```

添加新工具：`mcp/` 下创建新 Tool 类实现 `BaseTool` → `ToolRegistry` 注册 → 系统提示词描述用途。

### TTS 策略模式

```
VoiceServiceFacade
  ├── XfyunTtsProvider — 讯飞语音
  ├── MiniMaxTtsProvider — MiniMax TTS
  └── NoOpTtsProvider — 空实现（降级）
```

通过 `voice.tts-provider` 配置切换。TTS 只合成 `content`，不合成 `thinking`。10 秒超时保护，失败不重试。

### 质量模式与模型路由

**质量模式**（三档可调）：

| 模式 | maxTokens | temperature | 适用场景 |
|------|-----------|-------------|----------|
| SPEED | 400 | 0.2 | 简单问答、闲聊 |
| BALANCED | 800 | 0.3 | 通用场景（默认） |
| QUALITY | 2000 | 0.7 | 复杂分析、创意写作 |

**模型路由策略**（两档）：

| 条件 | 选择模型 | 示例 |
|------|----------|------|
| 消息 ≤ 20字 / 问候语 / 简单问答 | 小模型 (moonshot-v1-8k) | "你好"、"谢谢" |
| 其他 | 标准模型 (moonshot-v1-32k) | 一般对话、复杂分析 |

---

## SSE 通信协议

### 事件类型定义

| 事件 | 用途 | 必须字段 |
|------|------|----------|
| `thinking` | AI 思考过程（增量，可选） | text, conversationId, messageId |
| `content` | 正式回复（增量） | text, conversationId, messageId |
| `content_done` | 文本生成完毕 | conversationId, messageId |
| `tool_call` | 工具调用通知 | toolCall |
| `tool_result` | 工具执行结果 | toolResult |
| `audio_done` | TTS 结束（4种状态互斥） | conversationId, messageId + url/timeout/error/noaudio |
| `token_usage` | Token 消耗统计 | inputTokens, outputTokens, duration, model |
| `cache_hit` | 缓存命中 | content |
| `clarification` | 意图澄清 | content |
| `error` | 错误 | message |
| `done` | 流结束 | — |

### 合法事件序列

**正常对话流**：`[thinking*] → content+ → content_done → audio_done → [token_usage] → done`

**工具调用流**：`[thinking*] → (tool_call → tool_result)+ → content+ → content_done → audio_done → [token_usage] → done`

**缓存命中流**（无 AI 调用）：`cache_hit → content_done → audio_done → done`

**意图澄清流**：`clarification → done`

### SSE 格式标准

```
data: {"type":"content","text":"Hello","conversationId":1,"messageId":2}\n\n
```

- 以 `data: ` 开头
- 消息体为 JSON
- 以 `\n\n` 结尾

详细规范见 [SSE_TTS_UNIFIED_SPEC.md](mds/core/SSE_TTS_UNIFIED_SPEC.md)。

---

## API 设计

### 认证模块

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/auth/login` | 用户登录 (返回 JWT) |
| POST | `/api/auth/logout` | 用户登出 |
| POST | `/api/auth/refresh` | 刷新 Access Token |
| GET | `/api/auth/me` | 获取当前用户信息 |

**Demo 账号**: `admin` / `admin`

### 对话模块

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/chat/send` | 发送消息 (同步) |
| POST | `/api/chat/stream` | SSE 流式对话 |
| GET | `/api/chat/history` | 获取对话历史 |
| POST | `/api/chat/voice` | 语音输入 |

### 会话管理模块

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/chat/conversation` | 创建会话 |
| GET | `/api/chat/conversations` | 获取会话列表 |
| DELETE | `/api/chat/conversation/{id}` | 删除会话 |

### 日历模块

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/calendar/events` | 获取事件列表 |
| POST | `/api/calendar/events` | 创建事件 |
| PUT | `/api/calendar/events/{id}` | 更新事件 |
| DELETE | `/api/calendar/events/{id}` | 删除事件 |

### 待办模块

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/todos` | 获取待办列表 |
| POST | `/api/todos` | 创建待办 |
| PUT | `/api/todos/{id}` | 更新待办 |
| PUT | `/api/todos/{id}/complete` | 标记完成 |
| DELETE | `/api/todos/{id}` | 删除待办 |

### 路线规划模块

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/route/plan` | 规划出行路线 |

### 天气模块

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/weather/current` | 当前天气 |
| GET | `/api/weather/forecast` | 天气预报 |

### 管理后台 API

| 路径 | 方法 | 说明 |
|------|------|------|
| `/api/admin/metrics/current` | GET | 当前优化效果指标快照 |
| `/api/admin/metrics/trend` | GET | 指标趋势（`?days=7`）|
| `/api/admin/metrics/comparison` | GET | 优化前后对比 |
| `/api/admin/cost/stats` | GET | 系统成本统计 |
| `/api/admin/cost/today` | GET | 今日成本快速查询 |
| `/api/admin/cache/clear/semantic` | POST | 清除语义缓存 |
| `/api/admin/cache/clear/tool` | POST | 清除工具缓存 |
| `/api/admin/cache/clear/all` | POST | 清除全部缓存 |
| `/api/admin/quality/mode` | GET/PUT | 查询/切换质量模式 |

### 响应格式

后端统一返回 `Result<T>` 格式:

```json
{
  "code": 200,
  "data": { ... },
  "message": "success"
}
```

---

## 数据库设计

### 核心表结构

1. **user** - 用户表
2. **conversation** - 会话表 (支持多会话)
3. **chat_message** - 对话记录表 (支持函数调用记录)
4. **calendar_event** - 日历事件表 (支持重复事件)
5. **todo_item** - 待办事项表 (支持优先级和状态)
6. **reminder** - 提醒记录表
7. **ai_cost_record** - AI 调用成本记录表

### 数据库初始化

数据库表结构由 `V1__init_schema.sql` 初始化，包含默认 admin 用户。

---

## 开发约定

### 后端代码风格

- 使用 **Lombok** 简化 POJO 代码 (`@Data`, `@RequiredArgsConstructor`)
- 使用构造函数注入 (`@RequiredArgsConstructor`)
- 统一返回格式: `Result<T>` (code, data, message)
- 使用 Slf4j 进行日志记录
- 异常处理统一在 `GlobalExceptionHandler`
- 实体类使用 MyBatis Plus 注解 (`@TableName`, `@TableId`, `@TableField`)
- Mapper 继承 `BaseMapper<T>` 获得基础 CRUD 能力
- 逻辑删除字段统一为 `deleted`

### 前端代码风格

- 使用 **Composition API** (`<script setup>`)
- 使用 **TypeScript** 进行类型检查
- 状态管理使用 **Pinia**
- UI 组件使用 **Element Plus**
- API 调用统一封装在 `api/` 目录
- 使用 provide/inject 在组件间传递会话状态

### Android 代码风格

- 使用 **MVVM** 架构
- 使用 **Kotlin Coroutines + Flow** 处理异步
- 依赖注入使用 **Hilt**
- UI 使用 **Jetpack Compose**
- 数据层使用 **Repository 模式**

### 命名规范

| 类型 | 命名风格 | 示例 |
|------|----------|------|
| Java 类 | 大驼峰 | `ChatController` |
| Java 方法 | 小驼峰 | `sendMessage` |
| Vue 组件 | 大驼峰 | `ChatView.vue` |
| Kotlin 类 | 大驼峰 | `ChatViewModel` |
| 数据库表 | 下划线 | `chat_message` |
| 数据库字段 | 下划线 | `created_at` |

---

## 测试策略

### 后端测试

测试目录: `mrshudson-backend/src/test/java/com/mrshudson/`

| 测试类 | 描述 |
|--------|------|
| `OptimIntegrationTest` | 优化层集成测试 (语义缓存、意图路由、成本监控) |
| `ContextManagerTest` | 上下文管理测试 |
| `IntentClarificationServiceTest` | 意图澄清服务测试 |
| `IntentConfidenceEvaluatorTest` | 意图置信度评估测试 |
| `ToolResultValidatorTest` | 工具结果验证测试 |
| `TokenUsageTest` | Token 使用统计测试 |

**运行测试**:
```bash
mvn test
mvn test -Dtest=OptimIntegrationTest
```

### Android 测试

```bash
# 单元测试
./gradlew test

# 仪器测试
./gradlew connectedAndroidTest
```

---

## 安全考虑

### 当前实现

- **JWT 认证**: Access Token 1小时过期，Refresh Token 7天过期
- **CSRF 防护**: 开发环境已禁用 (前后端分离)
- **API 认证**: 除 `/api/auth/**` 外，所有接口需要认证
- **SQL 注入**: MyBatis Plus 参数绑定防护
- **CORS**: 配置允许前端域名访问

### 生产环境建议

- 启用 HTTPS
- 配置 API 限流
- 敏感配置外部化 (K8s Secrets / Vault)
- 启用 SQL 注入、XSS 防护
- 配置 JWT Blacklist 处理登出

---

## 部署配置

### Docker Compose 服务

| 服务 | 容器名 | 端口 | 说明 |
|------|--------|------|------|
| mysql | mrshudson-mysql | 3306 | MySQL 8.0 |
| redis | mrshudson-redis | 6379 | Redis 7 |
| backend | mrshudson-backend | 8080 | Spring Boot |
| frontend | mrshudson-frontend | 80 | Nginx |

### 配置文件清单

| 配置文件 | 用途 | 敏感信息 |
|---------|------|---------|
| `.env` | Docker 环境变量 | API Keys, 数据库密码 |
| `application.yml` | 后端应用配置 | 从环境变量读取 |
| `vite.config.ts` | 前端构建配置 | 无 |
| `nginx.conf` | Nginx 配置 | 无 |

---

## 扩展开发

### 添加新的 MCP 工具

1. 在 `mcp/` 包下创建新的 Tool 类，实现 `BaseTool`
2. 在 `ToolRegistry` 中注册工具
3. 在系统提示词中描述工具用途
4. 实现工具执行逻辑

### 添加新的 API 端点

1. Controller 添加 `@RestController` 和 `@RequestMapping`
2. 创建对应的 Service 接口和实现
3. 如需数据持久化，创建 Entity 和 Mapper
4. 更新数据库初始化脚本 (如需要)

---

## 故障排查

### 常见问题

1. **后端启动失败**: 检查 MySQL 和 Redis 是否已启动
2. **前端无法连接后端**: 检查 `vite.config.ts` 代理配置
3. **AI API 调用失败**: 检查 `KIMI_API_KEY` 环境变量
4. **数据库连接失败**: 检查 `application.yml` 中的数据库配置
5. **JWT 验证失败**: 检查 Token 是否过期，尝试重新登录

### 日志位置

- **后端**: 控制台输出 (默认配置)
- **前端**: 浏览器控制台
- **Android**: Logcat

---

## 相关文档

| 文档 | 路径 | 说明 |
|------|------|------|
| **AI 架构设计** | `mds/core/AI_ARCHITECTURE.md` | 完整后端架构、调用链、优化层设计 (v3.5) |
| **SSE+TTS 规范** | `mds/core/SSE_TTS_UNIFIED_SPEC.md` | SSE 事件协议、TTS 策略、跨端规范 (v2.6) |
| 需求文档 | `.spec-workflow/specs/mrshudson-core/requirements.md` | 核心需求 |
| 设计文档 | `.spec-workflow/specs/mrshudson-core/design.md` | 系统设计 |
| AI 优化设计 | `.spec-workflow/specs/ai-cost-optimization/design.md` | 成本优化设计 |
| 产品愿景 | `.spec-workflow/steering/product.md` | 产品方向 |
| 技术架构 | `.spec-workflow/steering/tech.md` | 技术规划 |

---

## 更新日志

### 2026-04-04 - 意图向量缓存上线 v2.2

- Intent Vector Cache (L1+L2+L3) 三级缓存系统上线
- L1 Caffeine 内存缓存 (5min TTL, ~95% 命中率)
- L2 Redis Hash 缓存 (7d TTL, ~3% 命中率)
- L3 Redis Vector 向量搜索 (30d TTL, ~1% 命中率)
- 集成熔断器保护 (Circuit Breaker)
- Micrometer 监控指标 (`intent_cache_*`)
- 更新五层拦截架构状态 (全部已上线)

### 2026-04-04 - 架构完善 v2.1

- 完善 AGENTS.md 项目文档
- 更新技术栈和架构描述
- 添加 Android 模块文档
- 添加 AI 优化层文档
- 补充五层拦截架构详细说明
- 补充意图路由三层架构（含模型选择）
- 补充质量模式与模型路由
- 补充 SSE 通信协议详细规范
- 更新管理后台 API 路径

### 2026-03-03 - JWT 与路线规划

- 实现 JWT 认证机制替代 Session
- 添加出行路线规划功能
- 修复页面切换空白问题
- 实现对话标题 AI 概括

### 2026-03-01 - 架构重大变更

- ORM 框架迁移 (JPA → MyBatis Plus)
- 移除 Flyway
- 配置清理 (移除阿里云 NLS)
- 语音服务统一使用讯飞

### 2026-02 - 初始版本

- 用户认证（登录/注册）
- AI 对话功能（集成 Kimi API）
- 天气查询工具
- 日历管理工具
- 待办事项管理工具

---

*最后更新: 2026-04-04*
*文档版本: 2.2*
