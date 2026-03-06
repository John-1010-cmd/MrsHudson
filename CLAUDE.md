# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

MrsHudson（哈德森夫人）是一个AI管家助手应用，采用前后端分离架构：
- **前端**: Vue 3 + TypeScript + Vite + Element Plus (Port 3000)
- **后端**: Spring Boot 3.2 + MyBatis Plus + MySQL 8.0 + Redis (Port 8080)
- **AI**: 集成 Kimi API (月之暗面) 提供对话能力，通过 MCP 工具模式调用业务功能
- **Android**: Kotlin + Jetpack Compose + Hilt + Room (API 26+)

## 常用命令

### 基础设施启动
```bash
# 启动 MySQL 和 Redis
docker-compose up -d mysql redis
```

### 后端开发 (mrshudson-backend/)
```bash
cd mrshudson-backend

# 编译
mvn clean compile

# 运行
mvn spring-boot:run

# 打包
mvn clean package

# 运行单个测试类
mvn test -Dtest=ClassName

# 运行单个测试方法
mvn test -Dtest=ClassName#methodName
```

### 前端开发 (mrshudson-frontend/)
```bash
cd mrshudson-frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建
npm run build

# 预览生产构建
npm run preview
```

### 完整部署
```bash
# 复制环境变量模板
cp .env.example .env
# 编辑 .env 填入 KIMI_API_KEY

# 使用 Docker Compose 启动所有服务
docker-compose up -d
```

### Android 开发 (mrshudson-android/)
```bash
cd mrshudson-android

# 编译调试版本
./gradlew assembleDebug

# 编译发布版本
./gradlew assembleRelease

# 安装到连接的设备
./gradlew installDebug

# 运行单元测试
./gradlew test

# 运行仪器测试
./gradlew connectedAndroidTest

# 清理构建
./gradlew clean

# 同步项目（用于更新依赖）
./gradlew sync
```

## 架构设计

### MCP (Model Context Protocol) 工具架构

后端采用 MCP 模式让 AI 调用业务功能：

```
ChatService → KimiClient → Kimi API
     │
     ▼
ToolRegistry (工具注册中心)
     │
  ┌──┴──┬────────┬────────┐
  ▼     ▼        ▼        ▼
Weather Calendar  Todo   (扩展)
 Tool    Tool    Tool
```

- **BaseTool**: 工具接口定义 (`mcp/BaseTool.java`)
- **ToolRegistry**: 工具注册中心，管理所有可用工具 (`mcp/ToolRegistry.java`)
- **具体工具**: WeatherTool、CalendarTool、TodoTool、RouteTool 实现业务功能调用

添加新工具步骤：
1. 在 `mcp/` 下创建新 Tool 类实现 `BaseTool`
2. 在 `ToolRegistry` 中注册工具
3. 在系统提示词中描述工具用途

### JWT 认证架构

使用 JWT Token 替代 Session 进行身份认证：

```
登录请求 → AuthController.login()
              ↓
         AuthServiceImpl.login()
              ↓
    生成 AccessToken + RefreshToken
              ↓
         返回给前端存储
              ↓
后续请求 → JwtAuthenticationFilter 验证 Token
              ↓
         JwtContext 存储当前 Token
              ↓
         Controller/Service 通过 Token 获取用户ID
```

- **JwtTokenUtil**: Token 生成和验证工具 (`util/JwtTokenUtil.java`)
- **JwtContext**: ThreadLocal 存储当前请求 Token (`util/JwtContext.java`)
- **JwtAuthenticationFilter**: 请求过滤器，验证 Authorization Header (`filter/JwtAuthenticationFilter.java`)
- **Token 有效期**: Access Token 1小时，Refresh Token 7天（存储在 Redis）

### 路线规划工具

使用高德地图路径规划 API 实现出行路线查询：

- **RouteTool**: MCP 工具，支持步行/驾车/公交三种方式 (`mcp/route/RouteTool.java`)
- **RouteService**: 调用高德 API 获取路线信息 (`service/RouteService.java`)
- **功能**:
  - 自动解析地址为经纬度坐标
  - 返回距离、时间、详细步骤
  - 驾车：显示过路费、红绿灯数量
  - 公交：显示换乘方案、步行距离
  - API 异常时返回模拟数据降级

### 异步任务配置

对话标题生成使用 Spring 异步任务：

- **AsyncConfig**: 线程池配置 (`config/AsyncConfig.java`)
- **@EnableAsync**: 主类启用异步支持 (`MrshudsonApplication.java`)
- **generateConversationTitle**: 异步生成标题方法 (`service/impl/ChatServiceImpl.java`)

标题生成流程：
1. 用户发送第一条消息
2. 检查该会话的用户消息数量是否为 1
3. 异步调用 Kimi API 生成标题（提示词："请用10-15字概括以下对话的主题..."）
4. 更新会话标题到数据库
5. 前端下次获取会话列表时显示新标题

### Kimi API 工具调用规范

**重要**: 使用工具调用时需遵循以下规范，否则会导致 `400 Bad Request` 错误：

1. **assistant 消息**包含 `tool_calls` 时，`content` 必须为 `null`（不能是空字符串 `""`）
2. **tool 消息**不能包含 `name` 字段（即使为 null）
3. 序列化时需保留 null 值字段（使用 `WriteMapNullValue`）

**正确示例**:
```json
{"role":"assistant","content":null,"tool_calls":[{"id":"call_xxx","type":"function","function":{"name":"get_weather","arguments":"{\"city\":\"深圳\"}"}}]}
{"role":"tool","tool_call_id":"call_xxx","content":"天气结果..."}
```

**错误示例**:
```json
{"role":"assistant","content":"","tool_calls":[...]}  // content 为空字符串
{"role":"tool","tool_call_id":"call_xxx","name":null,"content":"..."}  // 包含 name 字段
```

详见 `KimiClient.java` 中的 `MESSAGE_NAME_FILTER` 实现。

### 天气服务降级处理

当天气 API 无法连接或密钥无效时，服务会自动降级返回模拟数据：

- **检查 API 密钥**: 未配置时返回模拟数据并提示配置
- **网络错误处理**: 捕获 `ResourceAccessException`，返回带提示的模拟数据
- **密钥无效处理**: 检测高德 API 返回的密钥错误，给出配置指引
- **模拟数据示例**:
  ```
  ⚠️ 无法连接到天气服务器（网络限制）。北京当前天气模拟数据：
  ☀️ 天气：晴
  🌡️ 温度：25°C
  💧 湿度：60%
  🌬️ 风向：东南风 微风

  💡 提示：天气API需要在有外网访问权限的环境中使用
  ```

获取真实天气数据需要在 `.env` 中配置 `WEATHER_API_KEY`：
- 申请地址：https://lbs.amap.com/api/webservice/guide/api/weatherinfo

### 数据库访问 (MyBatis Plus)

- **实体类**: `domain/entity/`，使用 MyBatis Plus 注解 (`@TableName`, `@TableId`, `@TableField`)
- **Mapper**: `mapper/` 包，继承 `BaseMapper<T>` 获得基础 CRUD
- **自动填充**: `MyMetaObjectHandler` 自动处理 `created_at`/`updated_at`
- **逻辑删除**: 配置在 `application.yml`，字段名为 `deleted`

### 数据库表结构

**user（用户表）**:
```sql
CREATE TABLE user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

**conversation（会话表）**:
```sql
CREATE TABLE conversation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL DEFAULT '新对话',
    provider VARCHAR(50) DEFAULT 'kimi-for-coding',
    last_message_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);
```

**chat_message（消息表）**:
```sql
CREATE TABLE chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    conversation_id BIGINT NULL,
    role VARCHAR(20) NOT NULL COMMENT 'user, assistant, system',
    content TEXT NOT NULL,
    function_call JSON DEFAULT NULL COMMENT '函数调用信息',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);
```

**calendar_event（日历事件表）**:
```sql
CREATE TABLE calendar_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    location VARCHAR(200),
    category VARCHAR(20) DEFAULT 'personal',
    reminder_minutes INT DEFAULT 15,
    is_recurring BOOLEAN DEFAULT FALSE,
    recurrence_rule VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

**todo_item（待办事项表）**:
```sql
CREATE TABLE todo_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    priority VARCHAR(20) DEFAULT 'medium',
    status VARCHAR(20) DEFAULT 'pending',
    due_date TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### API 响应格式

后端统一返回格式 (`Result<T>`):
```json
{
  "code": 200,
  "data": {...},
  "message": "success"
}
```

### 前端状态管理

- **Pinia**: 状态管理库
- **user store**: `stores/user.ts`，管理登录状态和当前用户信息
- **Axios**: 封装在 `api/axios.ts`，baseURL 指向 `/api`（通过 Vite 代理转发到后端）

### 会话管理架构

前端使用 Vue Router 嵌套路由 + provide/inject 实现会话管理：

```
ChatView.vue (Layout)
├── 左侧：会话列表 sidebar
│   ├── 会话列表（可切换、删除）
│   └── 新建会话按钮
├── 底部：导航菜单（对话/日历/待办/天气）
└── 主内容区：<RouterView :key="route.fullPath" />
    ├── ChatRoom.vue (默认子路由)
    ├── CalendarView.vue
    ├── TodoView.vue
    └── WeatherView.vue
```

**状态传递**：
- `ChatView.vue` 通过 `provide()` 提供当前会话状态
- `ChatRoom.vue` 通过 `inject()` 接收会话 ID 并加载对应消息
- 切换会话时自动重新加载该会话的消息历史
- **页面切换修复**: 使用 `:key="route.fullPath"` 强制组件重新创建，解决切换后空白问题

**关键类型**：
```typescript
// api/chat.ts
interface ConversationDTO {
  id: string
  title: string
  provider: string
  lastMessageAt: string
  createdAt: string
}

interface SendMessageRequest {
  message: string
  conversationId?: string | null
}
```

### Android 应用架构

Android 应用采用 MVVM + Clean Architecture + Jetpack Compose 架构：

```
UI Layer (Compose Screens)
├── LoginScreen, MainScreen, ChatScreen, CalendarScreen, TodoScreen, WeatherScreen, RouteScreen
└── ViewModels (LoginViewModel, MainViewModel)

Domain Layer
├── User (domain/model/)
└── Repository Interfaces

Data Layer
├── Remote: AuthApi, BaseApi (Retrofit + Gson)
├── Local: MrsHudsonDatabase (Room), TokenDataStore (DataStore)
└── Repository Implementations (AuthRepository)

DI: Hilt Modules (NetworkModule, DatabaseModule, RepositoryModule, DataStoreModule)
```

**技术栈**：
- **UI**: Jetpack Compose + Material3 + Navigation Compose
- **依赖注入**: Hilt
- **网络**: Retrofit + OkHttp + Gson
- **本地存储**: Room (数据库) + DataStore (偏好设置)
- **异步**: Kotlin Coroutines + Flow

**关键文件**：
- `MainActivity.kt`: 应用入口，设置 Compose Theme
- `MrsHudsonNavHost.kt`: 导航图定义（登录/主界面路由）
- `BottomNavItem.kt`: 底部导航栏配置（对话/日历/待办/天气/路线）
- `TokenDataStore.kt`: JWT Token 本地存储管理
- `AuthInterceptor.kt`: 自动附加 Authorization Header
- `ApiResult.kt`: 封装 API 响应结果（Success/Error/Loading）

**添加新 Screen 步骤**：
1. 在 `ui/screens/` 下创建新目录和 Screen 文件
2. 如需 ViewModel，在同目录创建并注入 Repository
3. 在 `MrsHudsonNavHost.kt` 中添加导航路由
4. 如需底部导航，在 `BottomNavItem.kt` 中添加配置

## 配置文件

| 文件 | 用途 |
|------|------|
| `.env` | Docker 环境变量，需配置 `KIMI_API_KEY` |
| `mrshudson-backend/src/main/resources/application.yml` | 后端应用配置 |
| `mrshudson-frontend/vite.config.ts` | Vite 构建配置 |
| `mrshudson-android/gradle/libs.versions.toml` | Android 依赖版本管理 |
| `mrshudson-android/app/build.gradle.kts` | Android 应用构建配置 |
| `mrshudson-android/local.properties` | Android SDK 路径（自动生成）|

## 环境变量

关键环境变量（在 `.env` 中配置）：

- `KIMI_API_KEY`: Kimi AI API 密钥（必填，从 https://platform.moonshot.cn/ 获取）
- `AI_PROVIDER`: AI 提供商，可选 `kimi`（默认）或 `minimax`
- `MINIMAX_API_KEY`: MiniMax API 密钥（当 AI_PROVIDER=minimax 时必填，从 https://platform.minimax.cn/ 获取）
- `JWT_SECRET`: JWT 签名密钥（可选，默认使用内置密钥，生产环境建议配置）
  - 建议使用至少 256 位的随机字符串
  - 可使用 `openssl rand -base64 32` 生成
- `WEATHER_API_KEY`: 高德地图天气 API 密钥（可选，用于天气和路线规划）
  - 申请地址：https://lbs.amap.com/api/webservice/guide/api/weatherinfo
  - 路线规划也使用此密钥
- `MYSQL_ROOT_PASSWORD`/`MYSQL_PASSWORD`: 数据库密码
- `REDIS_PASSWORD`: Redis 密码

## 端口映射

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端开发服务器 | 3000 | Vite dev server |
| 后端 API | 8080 | Spring Boot |
| MySQL | 3306 | 数据库 |
| Redis | 6379 | 缓存 |

## Android 开发要求

- **JDK**: 17 或更高版本
- **minSdk**: 26 (Android 8.0)
- **targetSdk**: 34 (Android 14)
- **开发工具**: Android Studio Hedgehog 或更新版本

**首次导入项目**：
1. 使用 Android Studio 打开 `mrshudson-android` 目录
2. 等待 Gradle Sync 完成
3. 配置 Android SDK（如果未配置）
4. 运行 `app` 配置到模拟器或真机

## Demo 账号

- 用户名: `admin`
- 密码: `admin`

---

**文档最后更新**: 2026-03-05

### 最近变更摘要
- ✅ 初始化 Android 项目（Wave 1）：Jetpack Compose + Hilt + Room 架构
- ✅ 实现 JWT 认证（替换 Session）
- ✅ 实现出行路线规划功能
- ✅ 编译错误修复（添加缺失的 import）
