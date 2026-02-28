# MrsHudson Core - 设计文档

## 概述

**规格名称**: mrshudson-core
**描述**: MrsHudson AI管家核心系统设计
**状态**: 待审批

---

## 架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                         客户端层                                │
├──────────────┬──────────────┬───────────────────────────────────┤
│   Web端      │   iOS App    │           Android App             │
│  (Vue3)      │  (SwiftUI)   │         (Jetpack Compose)         │
└──────┬───────┴──────┬───────┴──────────────┬────────────────────┘
       │              │                      │
       └──────────────┼──────────────────────┘
                      │ HTTPS / WebSocket
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                      API网关层                                  │
│              (Spring Boot + Spring Security)                    │
└─────────────────────────────────┬───────────────────────────────┘
                                  │
       ┌──────────────────────────┼──────────────────────────┐
       │                          │                          │
       ▼                          ▼                          ▼
┌─────────────┐        ┌──────────────────┐        ┌─────────────┐
│  用户模块   │        │   AI对话引擎     │        │  业务模块   │
│  (US-001)   │        │    (US-002)      │        │ (US-003~007)│
├─────────────┤        ├──────────────────┤        ├─────────────┤
│ • Admin登录 │        │ • Kimi API接入   │        │ • 天气服务  │
│ • Session   │        │ • 上下文管理     │        │ • 日历管理  │
│             │        │ • Function Call  │        │ • 待办管理  │
│             │        │ • 语音转文字     │        │ • 路线规划  │
│             │        │ • 文字转语音     │        │ • 智能提醒  │
└──────┬──────┘        └────────┬─────────┘        └──────┬──────┘
       │                        │                         │
       └────────────────────────┼─────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      MCP服务层                                  │
├────────────┬────────────┬────────────┬──────────────────────────┤
│ Weather MCP│ Calendar   │  Todo MCP  │     Maps MCP             │
│            │   MCP      │            │                          │
└────────────┴────────────┴────────────┴──────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      数据层                                     │
├─────────────────┬─────────────────┬─────────────────────────────┤
│     MySQL       │     Redis       │     外部API                 │
│   (主存储)      │   (缓存/会话)   │  Kimi/天气/地图             │
└─────────────────┴─────────────────┴─────────────────────────────┘
```

---

## 数据模型

### 用户表 (user)
```sql
CREATE TABLE user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Demo账号
INSERT INTO user (username, password) VALUES ('admin', 'admin');
```

### 对话记录表 (chat_message)
```sql
CREATE TABLE chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role ENUM('user', 'assistant', 'system') NOT NULL,
    content TEXT NOT NULL,
    function_call JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_time (user_id, created_at)
);
```

### 日历事件表 (calendar_event)
```sql
CREATE TABLE calendar_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    location VARCHAR(200),
    category ENUM('work', 'personal', 'family') DEFAULT 'personal',
    reminder_minutes INT DEFAULT 15,
    is_recurring BOOLEAN DEFAULT FALSE,
    recurrence_rule VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_time (user_id, start_time)
);
```

### 待办事项表 (todo_item)
```sql
CREATE TABLE todo_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    priority ENUM('low', 'medium', 'high') DEFAULT 'medium',
    status ENUM('pending', 'in_progress', 'completed') DEFAULT 'pending',
    due_date TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_status (user_id, status),
    INDEX idx_due_date (due_date)
);
```

### 提醒记录表 (reminder)
```sql
CREATE TABLE reminder (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    type ENUM('event', 'todo', 'weather') NOT NULL,
    ref_id BIGINT,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    remind_at TIMESTAMP NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    channel ENUM('in_app', 'email', 'push') DEFAULT 'in_app',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_remind (user_id, remind_at)
);
```

---

## API设计

### 认证模块

#### POST /api/auth/login
**描述**: 用户登录
**请求体**:
```json
{
  "username": "admin",
  "password": "admin"
}
```
**响应**:
```json
{
  "code": 200,
  "data": {
    "token": "session_id_here",
    "user": {
      "id": 1,
      "username": "admin"
    }
  }
}
```

#### POST /api/auth/logout
**描述**: 用户登出
**响应**:
```json
{
  "code": 200,
  "message": "登出成功"
}
```

### AI对话模块

#### POST /api/chat/send
**描述**: 发送消息
**请求体**:
```json
{
  "message": "明天北京天气怎么样？",
  "session_id": "optional_existing_session"
}
```
**响应**:
```json
{
  "code": 200,
  "data": {
    "message_id": "msg_xxx",
    "content": "明天北京晴，温度15-25°C...",
    "function_calls": [
      {
        "name": "get_weather",
        "arguments": {"city": "北京", "date": "2026-03-01"}
      }
    ]
  }
}
```

#### GET /api/chat/history
**描述**: 获取对话历史
**响应**:
```json
{
  "code": 200,
  "data": {
    "messages": [
      {"role": "user", "content": "你好"},
      {"role": "assistant", "content": "您好！我是MrsHudson..."}
    ]
  }
}
```

#### POST /api/chat/voice
**描述**: 语音输入（语音识别后发送）
**Content-Type**: multipart/form-data
**参数**:
- `audio`: 语音文件 (mp3/wav)
- `session_id`: 可选会话ID

**响应**:
```json
{
  "code": 200,
  "data": {
    "transcribed_text": "明天北京天气怎么样",
    "response": {
      "message_id": "msg_xxx",
      "content": "明天北京晴...",
      "audio_url": "https://xxx/tts/xxx.mp3"
    }
  }
}
```

### 日历模块

#### GET /api/calendar/events
**描述**: 获取事件列表
**参数**:
- `start_date`: 开始日期 (YYYY-MM-DD)
- `end_date`: 结束日期 (YYYY-MM-DD)
- `view`: 视图类型 (day/week/month)

**响应**:
```json
{
  "code": 200,
  "data": {
    "events": [
      {
        "id": 1,
        "title": "团队会议",
        "start_time": "2026-03-01T15:00:00",
        "end_time": "2026-03-01T16:00:00",
        "category": "work"
      }
    ]
  }
}
```

#### POST /api/calendar/events
**描述**: 创建事件
**请求体**:
```json
{
  "title": "团队会议",
  "start_time": "2026-03-01T15:00:00",
  "end_time": "2026-03-01T16:00:00",
  "category": "work",
  "reminder_minutes": 15
}
```

#### DELETE /api/calendar/events/{id}
**描述**: 删除事件

### 待办模块

#### GET /api/todos
**描述**: 获取待办列表
**参数**:
- `status`: 状态筛选 (all/pending/in_progress/completed)
- `priority`: 优先级筛选

#### POST /api/todos
**描述**: 创建待办
**请求体**:
```json
{
  "title": "完成设计文档",
  "priority": "high",
  "due_date": "2026-03-01T18:00:00"
}
```

#### PUT /api/todos/{id}/complete
**描述**: 标记完成

### 天气模块

#### GET /api/weather/current
**描述**: 当前天气
**参数**:
- `city`: 城市名称

**响应**:
```json
{
  "code": 200,
  "data": {
    "city": "北京",
    "temperature": 20,
    "humidity": 45,
    "condition": "晴",
    "wind": "东风2级"
  }
}
```

#### GET /api/weather/forecast
**描述**: 天气预报
**参数**:
- `city`: 城市名称
- `days`: 天数 (1-7)

### 提醒模块

#### GET /api/reminders
**描述**: 获取提醒列表
**参数**:
- `unread_only`: 仅未读
- `limit`: 数量限制

#### PUT /api/reminders/{id}/read
**描述**: 标记已读

---

## 组件设计

### 后端组件

#### 1. AuthController
**职责**: 认证相关API
**方法**:
- `login(LoginRequest)`: 登录
- `logout()`: 登出

#### 2. ChatController
**职责**: AI对话API
**方法**:
- `sendMessage(ChatRequest)`: 发送消息
- `getHistory()`: 获取历史
- `sendVoice(VoiceRequest)`: 语音输入

#### 3. ChatService
**职责**: AI对话业务逻辑
**方法**:
- `processMessage(String, Long)`: 处理用户消息
- `callKimiAPI(List<Message>)`: 调用Kimi API
- `handleFunctionCall(FunctionCall)`: 处理函数调用
- `convertTextToSpeech(String)`: 文字转语音
- `convertSpeechToText(MultipartFile)`: 语音转文字

#### 4. KimiClient
**职责**: Kimi API客户端
**方法**:
- `chatCompletion(List<Message>, List<Tool>)`: 对话完成
- `getAvailableTools()`: 获取可用工具定义

#### 5. ToolRegistry
**职责**: MCP工具注册与调用
**方法**:
- `registerTool(ToolDefinition)`: 注册工具
- `executeTool(String, Map)`: 执行工具
- `getToolDefinitions()`: 获取工具定义列表

#### 6. MCPTools
**职责**: 具体MCP工具实现
- `WeatherTool`: 天气查询
- `CalendarTool`: 日历操作
- `TodoTool`: 待办管理
- `MapsTool`: 路线规划
- `ReminderTool`: 提醒设置

#### 7. CalendarService / TodoService / WeatherService
**职责**: 各业务领域服务

### 前端组件 (Web)

#### 1. LoginView
**职责**: 登录页面
- 用户名/密码输入
- 登录按钮

#### 2. ChatView (主界面)
**职责**: 对话主界面
```
┌─────────────────────────────────────┐
│  ┌─────────┐  ┌───────────────────┐ │
│  │ 侧边栏  │  │                   │ │
│  │ • 对话  │  │    对话区域       │ │
│  │ • 日历  │  │                   │ │
│  │ • 待办  │  │  ┌─────────────┐  │ │
│  │ • 天气  │  │  │ 消息气泡    │  │ │
│  │         │  │  └─────────────┘  │ │
│  │         │  │                   │ │
│  │         │  ├───────────────────┤ │
│  │         │  │  🎤 输入框... 📝 │ │
│  └─────────┘  └───────────────────┘ │
└─────────────────────────────────────┘
```

#### 3. ChatMessage
**职责**: 消息气泡组件
- 用户消息（右对齐）
- AI消息（左对齐，带头像）
- 语音播放按钮
- 加载状态

#### 4. VoiceInputButton
**职责**: 语音输入按钮
- 按住录音
- 录音动画
- 发送语音

#### 5. CalendarView
**职责**: 日历视图
- 日/周/月切换
- 事件显示
- 点击创建事件

#### 6. TodoListView
**职责**: 待办列表
- 待办项列表
- 筛选标签
- 添加/完成待办

---

## 实现细节

### Kimi API集成

**模型**: moonshot-v1-8k / moonshot-v1-32k
**功能调用配置**:
```json
{
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "get_weather",
        "description": "获取指定城市的天气信息",
        "parameters": {
          "type": "object",
          "properties": {
            "city": {"type": "string", "description": "城市名称"},
            "date": {"type": "string", "description": "日期，格式YYYY-MM-DD"}
          },
          "required": ["city"]
        }
      }
    }
  ]
}
```

**系统提示词**:
```
你是MrsHudson（哈德森夫人），一位贴心的私人管家助手。你的职责是帮助用户管理日常生活，包括：
- 查询天气并提供穿衣建议
- 管理日程安排
- 记录待办事项
- 规划出行路线

请用友好、专业的语气回复，适当使用表情符号。如果用户的问题超出你的能力范围，礼貌地说明。
```

### 语音处理

**语音识别**: 阿里云NLS / 讯飞语音听写
**语音合成**: 阿里云NLS / 讯飞语音合成
**流程**:
1. 用户点击语音按钮 -> 开始录音
2. 录音结束 -> 上传音频文件
3. 后端调用ASR服务 -> 获取文字
4. 文字送入AI对话流程
5. AI返回文字 -> 调用TTS服务生成音频
6. 返回文字+音频URL给前端

### 上下文管理

**策略**: 滑动窗口 + 摘要
- 保留最近20轮对话
- 超过20轮时，将最早10轮压缩为摘要
- 摘要作为system消息的一部分

### 多端同步

**WebSocket**: 用于实时消息推送
**数据同步**:
- 用户操作后立即同步到后端
- 其他端通过WebSocket接收更新
- 离线时缓存操作，联网后批量同步

---

## 技术要求

- Java 17+
- Spring Boot 3.2+
- Vue 3.4+
- TypeScript 5.0+
- MySQL 8.0+
- Redis 7.0+

## 开发技能支持

开发以下端应用时需使用对应的Claude Skill：

| 平台 | Skill名称 | 来源 |
|------|----------|------|
| iOS | `ios-swift-development` | aj-geddes/useful-ai-prompts |
| Android | `mobile-android-design` | wshobson/agents |

**安装命令**:
```bash
npx skills add aj-geddes/useful-ai-prompts@ios-swift-development -g
npx skills add wshobson/agents@mobile-android-design -g
```
