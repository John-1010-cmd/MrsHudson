# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

MrsHudson（哈德森夫人）是一个AI管家助手应用，采用前后端分离架构：
- **前端**: Vue 3 + TypeScript + Vite + Element Plus (Port 3000)
- **后端**: Spring Boot 3.2 + MyBatis Plus + MySQL 8.0 + Redis (Port 8080)
- **AI**: 集成 Kimi API (月之暗面) 提供对话能力，通过 MCP 工具模式调用业务功能

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
- **具体工具**: WeatherTool、CalendarTool、TodoTool 实现业务功能调用

添加新工具步骤：
1. 在 `mcp/` 下创建新 Tool 类实现 `BaseTool`
2. 在 `ToolRegistry` 中注册工具
3. 在系统提示词中描述工具用途

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

### 数据库访问 (MyBatis Plus)

- **实体类**: `domain/entity/`，使用 MyBatis Plus 注解 (`@TableName`, `@TableId`, `@TableField`)
- **Mapper**: `mapper/` 包，继承 `BaseMapper<T>` 获得基础 CRUD
- **自动填充**: `MyMetaObjectHandler` 自动处理 `created_at`/`updated_at`
- **逻辑删除**: 配置在 `application.yml`，字段名为 `deleted`

### 数据库表结构

**conversation（会话表）**:
```sql
CREATE TABLE conversation (
  id VARCHAR(64) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  title VARCHAR(200),
  provider VARCHAR(50),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0
);
```

**chat_message（消息表）**:
```sql
CREATE TABLE chat_message (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  conversation_id VARCHAR(64),
  role VARCHAR(20) NOT NULL, -- user/assistant/system
  content TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (conversation_id) REFERENCES conversation(id)
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
└── 主内容区：<RouterView />
    ├── ChatRoom.vue (默认子路由)
    ├── CalendarView.vue
    ├── TodoView.vue
    └── WeatherView.vue
```

**状态传递**：
- `ChatView.vue` 通过 `provide()` 提供当前会话状态
- `ChatRoom.vue` 通过 `inject()` 接收会话 ID 并加载对应消息
- 切换会话时自动重新加载该会话的消息历史

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

## 配置文件

| 文件 | 用途 |
|------|------|
| `.env` | Docker 环境变量，需配置 `KIMI_API_KEY` |
| `mrshudson-backend/src/main/resources/application.yml` | 后端应用配置 |
| `mrshudson-frontend/vite.config.ts` | Vite 构建配置 |

## 环境变量

关键环境变量（在 `.env` 中配置）：

- `KIMI_API_KEY`: Kimi AI API 密钥（必填，从 https://platform.moonshot.cn/ 获取）
- `WEATHER_API_KEY`: 高德地图天气 API 密钥（可选）
- `MYSQL_ROOT_PASSWORD`/`MYSQL_PASSWORD`: 数据库密码
- `REDIS_PASSWORD`: Redis 密码

## 端口映射

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端开发服务器 | 3000 | Vite dev server |
| 后端 API | 8080 | Spring Boot |
| MySQL | 3306 | 数据库 |
| Redis | 6379 | 缓存 |

## Demo 账号

- 用户名: `admin`
- 密码: `admin`
