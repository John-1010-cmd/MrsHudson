# MrsHudson Core - 任务列表

## 概述

**规格名称**: mrshudson-core
**描述**: MrsHudson AI管家核心系统实现任务
**状态**: 待审批

---

## 阶段一：项目初始化与基础设施

### Task 1.1: 初始化后端项目结构
**状态**: [x]
**优先级**: P0
**关联需求**: US-001, US-002
**文件**:
- `mrshudson-backend/pom.xml`
- `mrshudson-backend/src/main/java/com/mrshudson/MrshudsonApplication.java`
- `mrshudson-backend/src/main/resources/application.yml`

**实现内容**:
1. 创建Spring Boot 3.2项目
2. 配置依赖：Spring Web, Spring Data JPA, MySQL Driver, Redis, Lombok
3. 配置application.yml（数据库、Redis连接）
4. 创建基础包结构

**_Prompt**:
```
Implement the task for spec mrshudson-core, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Java Backend Developer

Task: 初始化MrsHudson后端Spring Boot项目

Context:
- 项目根目录: /Users/huangzhuangcan/Documents/GitHub/John-1010-cmd/Mrs-Hudson
- 需要创建mrshudson-backend子项目
- 使用Spring Boot 3.2.x
- 数据库MySQL 8.x, 缓存Redis 7.x

Requirements:
1. 创建标准Maven项目结构
2. 配置Spring Boot parent POM
3. 添加依赖：spring-boot-starter-web, spring-boot-starter-data-jpa, mysql-connector-j, spring-boot-starter-data-redis, lombok
4. 创建启动类MrshudsonApplication
5. 配置application.yml：端口8080, MySQL连接, Redis连接
6. 创建基础包：com.mrshudson.config, controller, service, domain, repository

Restrictions:
- 不要添加不必要的依赖
- 配置使用YAML格式
- 数据库配置使用合理默认值

Success Criteria:
- mvn clean compile 成功
- 应用能正常启动（虽然会报错缺数据库，但结构正确）
```

---

### Task 1.2: 初始化前端项目结构
**状态**: [x]
**优先级**: P0
**关联需求**: US-002
**文件**:
- `mrshudson-frontend/package.json`
- `mrshudson-frontend/vite.config.ts`
- `mrshudson-frontend/src/main.ts`
- `mrshudson-frontend/src/App.vue`

**实现内容**:
1. 使用Vite创建Vue3 + TypeScript项目
2. 安装依赖：Vue Router, Pinia, Element Plus, Axios
3. 配置项目结构

**_Prompt**:
```
Implement the task for spec mrshudson-core, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Vue Frontend Developer

Task: 初始化MrsHudson前端Vue3项目

Context:
- 项目根目录: /Users/huangzhuangcan/Documents/GitHub/John-1010-cmd/Mrs-Hudson
- 需要创建mrshudson-frontend子项目
- 使用Vue3 + TypeScript + Vite

Requirements:
1. 使用npm create vite创建项目（template: vue-ts）
2. 安装依赖：vue-router@4, pinia, element-plus, axios
3. 配置vite.config.ts（端口3000, 代理到后端8080）
4. 创建src/router/index.ts路由配置
5. 创建src/stores/index.ts Pinia配置
6. 创建src/api/axios.ts封装axios
7. 创建基础目录：components, views, types, composables

Success Criteria:
- npm install 成功
- npm run dev 能启动开发服务器
- 浏览器访问 localhost:3000 显示Vue默认页面
```

---

### Task 1.3: 数据库初始化与实体类
**状态**: [x]
**优先级**: P0
**关联需求**: US-001
**文件**:
- `mrshudson-backend/src/main/resources/db/migration/V1__init_schema.sql`
- `mrshudson-backend/src/main/java/com/mrshudson/domain/entity/User.java`
- `mrshudson-backend/src/main/java/com/mrshudson/domain/entity/ChatMessage.java`

**实现内容**:
1. 创建数据库表结构（user, chat_message）
2. 插入admin账号
3. 创建JPA实体类

**_Prompt**:
```
Implement the task for spec mrshudson-core, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Java Backend Developer

Task: 创建数据库表结构和JPA实体类

Context:
- 使用Spring Data JPA
- 需要创建基础实体类
- 数据库表使用下划线命名，Java类使用驼峰命名

Requirements:
1. 创建Flyway迁移脚本 V1__init_schema.sql：
   - user表：id, username, password, created_at, updated_at
   - chat_message表：id, user_id, role, content, function_call(JSON), created_at
   - 插入admin/admin账号

2. 创建JPA实体类：
   - User：使用@Entity, @Table(name="user")，字段与表对应
   - ChatMessage：包含枚举Role(USER, ASSISTANT, SYSTEM)

3. 创建Repository接口：
   - UserRepository extends JpaRepository
   - ChatMessageRepository

Restrictions:
- 使用LocalDateTime而不是Date
- 使用@CreatedDate和@LastModifiedDate自动维护时间戳
- 需要配置JPA Auditing

Success Criteria:
- 实体类编译通过
- Flyway脚本语法正确
```

---

## 阶段二：用户认证模块

### Task 2.1: 实现简单登录认证
**状态**: [x]
**优先级**: P0
**关联需求**: US-001
**文件**:
- `mrshudson-backend/src/main/java/com/mrshudson/controller/AuthController.java`
- `mrshudson-backend/src/main/java/com/mrshudson/service/AuthService.java`
- `mrshudson-backend/src/main/java/com/mrshudson/config/SecurityConfig.java`

**实现内容**:
1. 创建登录API（支持admin/admin）
2. 配置Spring Security（简化版，仅session）
3. 创建登录页面

**_Prompt**:
```
Implement the task for spec mrshudson-core, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Java Backend Developer

Task: 实现简化版用户登录认证

Context:
- Demo阶段使用简单session认证
- 仅支持admin/admin账号
- 不需要JWT、注册功能

Requirements:
1. 创建AuthController：
   - POST /api/auth/login：验证username/password，成功后设置session
   - POST /api/auth/logout：使session失效
   - GET /api/auth/me：获取当前登录用户信息

2. 创建AuthService：
   - login(username, password)：验证账号，返回User
   - 仅验证admin/admin，密码明文比较（Demo阶段）

3. 配置Spring Security（SecurityConfig）：
   - 禁用CSRF（前后端分离开发）
   - 放行/api/auth/login
   - 其他接口需要认证
   - 使用Session管理登录状态

4. 创建登录请求/响应DTO

Restrictions:
- 不要实现JWT
- 不要实现注册
- 密码暂时明文存储和比较

Success Criteria:
- POST /api/auth/login {"username":"admin","password":"admin"} 返回成功并设置cookie
- 访问需要认证的接口时，未登录返回401
- 登录后可以访问受保护接口
```

---

### Task 2.2: 实现前端登录页面
**状态**: [x]
**优先级**: P0
**关联需求**: US-001
**文件**:
- `mrshudson-frontend/src/views/LoginView.vue`
- `mrshudson-frontend/src/router/index.ts`
- `mrshudson-frontend/src/api/auth.ts`

**实现内容**:
1. 创建登录页面UI
2. 配置路由
3. 实现登录API调用

**_Prompt**:
```
Implement the task for spec mrshudson-core, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Vue Frontend Developer

Task: 实现前端登录页面

Context:
- 使用Vue3 + Element Plus
- 需要调用后端 /api/auth/login
- 登录成功后跳转到主页面

Requirements:
1. 创建LoginView.vue：
   - 居中登录卡片
   - 用户名输入框（默认admin）
   - 密码输入框（默认admin）
   - 登录按钮
   - 登录失败提示

2. 创建auth.ts API模块：
   - login(username, password)
   - logout()
   - getCurrentUser()

3. 配置路由：
   - /login 显示登录页
   - / 显示主页面（暂时空白）
   - 路由守卫：未登录自动跳转到/login

4. 配置axios：
   - baseURL: '/api'
   - withCredentials: true（携带cookie）
   - 401响应自动跳转到登录页

Success Criteria:
- 访问localhost:3000自动跳转到登录页
- 输入admin/admin点击登录，调用后端API
- 登录成功后跳转到主页面
- 刷新页面保持登录状态
```

---

## 阶段三：AI对话核心

### Task 3.1: 集成Kimi API客户端
**状态**: [x]
**优先级**: P0
**关联需求**: US-002
**文件**:
- `mrshudson-backend/src/main/java/com/mrshudson/mcp/kimi/KimiClient.java`
- `mrshudson-backend/src/main/java/com/mrshudson/mcp/kimi/KimiProperties.java`
- `mrshudson-backend/src/main/java/com/mrshudson/mcp/kimi/dto/ChatRequest.java`
- `mrshudson-backend/src/main/java/com/mrshudson/mcp/kimi/dto/ChatResponse.java`

**实现内容**:
1. 创建Kimi API客户端
2. 配置API密钥
3. 实现对话接口调用

**_Prompt**:
```
Implement the task for spec mrshudson-core, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Java Backend Developer

Task: 集成Moonshot Kimi API

Context:
- Kimi API文档: https://platform.moonshot.cn/docs
- API Endpoint: https://api.moonshot.cn/v1/chat/completions
- 模型: moonshot-v1-8k

Requirements:
1. 创建KimiProperties配置类：
   - apiKey（从application.yml读取）
   - baseUrl（默认https://api.moonshot.cn/v1）
   - model（默认moonshot-v1-8k）

2. 创建DTO类：
   - ChatRequest：model, messages(List<Message>), tools(List<Tool>)
   - ChatResponse：choices(List<Choice>)
   - Message：role, content, tool_calls
   - Tool：type, function
   - Function：name, description, parameters
   - ToolCall：id, type, function

3. 创建KimiClient：
   - chatCompletion(List<Message> messages, List<Tool> tools)
   - 使用RestTemplate或WebClient发送POST请求
   - 处理API响应和错误

4. 在application.yml中添加配置项

Restrictions:
- API Key不要硬编码，使用配置文件
- 添加合理的超时设置（30秒）
- 记录API调用日志

Success Criteria:
- 单元测试：调用Kimi API发送"你好"，能返回正常响应
- 正确处理API错误（key无效、限流等）
```

---

### Task 3.2: 实现AI对话服务
**状态**: [x]
**优先级**: P0
**关联需求**: US-002
**文件**:
- `mrshudson-backend/src/main/java/com/mrshudson/service/ChatService.java`
- `mrshudson-backend/src/main/java/com/mrshudson/service/impl/ChatServiceImpl.java`
- `mrshudson-backend/src/main/java/com/mrshudson/controller/ChatController.java`

**实现内容**:
1. 创建对话服务
2. 实现消息处理逻辑
3. 创建对话API

**_Prompt**:
```
Implement the task for spec mrshudson-core, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Java Backend Developer

Task: 实现AI对话服务和API

Context:
- 使用KimiClient调用Kimi API
- 需要维护对话历史
- 系统提示词定义MrsHudson角色

Requirements:
1. 创建ChatService：
   - sendMessage(Long userId, String message)：处理用户消息
   - getChatHistory(Long userId, int limit)：获取历史消息

2. 实现sendMessage逻辑：
   - 保存用户消息到数据库
   - 构建消息列表：system消息（角色定义）+ 历史消息 + 当前消息
   - 调用KimiClient.chatCompletion()
   - 保存AI响应到数据库
   - 返回AI消息内容

3. 创建ChatController：
   - POST /api/chat/send：发送消息
   - GET /api/chat/history：获取历史
   - 参数：message（用户输入）
   - 响应：messageId, content, role, createdAt

4. 系统提示词：
   "你是MrsHudson（哈德森夫人），一位贴心的私人管家助手。帮助用户管理天气查询、日程安排、待办事项等日常事务。用友好、专业的语气回复。"

Success Criteria:
- POST /api/chat/send {"message":"你好"} 返回AI问候语
- 多次对话能保持上下文
- 历史记录正确保存到数据库
```

---

### Task 3.3: 实现前端对话界面
**状态**: [x]
**优先级**: P0
**关联需求**: US-002
**文件**:
- `mrshudson-frontend/src/views/ChatView.vue`
- `mrshudson-frontend/src/components/ChatMessage.vue`
- `mrshudson-frontend/src/api/chat.ts`

**实现内容**:
1. 创建聊天主界面
2. 实现消息气泡组件
3. 实现消息发送和显示

**_Prompt**:
```
Implement the task for spec mrshudson-core, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Vue Frontend Developer

Task: 实现AI对话聊天界面

Context:
- 类似ChatGPT的对话界面
- 左侧边栏导航，右侧聊天区域
- 需要调用后端 /api/chat/send

Requirements:
1. 创建ChatView.vue：
   - 左侧边栏：导航菜单（对话、日历、待办、天气）
   - 右侧聊天区域：
     - 消息列表区域（滚动）
     - 输入框区域（底部固定）
   - 响应式布局

2. 创建ChatMessage组件：
   - Props: message(对象，包含role, content, createdAt)
   - 用户消息：右对齐，蓝色背景
   - AI消息：左对齐，白色背景，带MrsHudson头像
   - 显示发送时间

3. 创建chat.ts API：
   - sendMessage(message: string)
   - getHistory()

4. 实现交互：
   - 输入框输入内容，点击发送
   - 显示加载状态（AI思考中）
   - 新消息自动滚动到底部
   - 页面加载时获取历史消息

Success Criteria:
- 界面美观，类似ChatGPT
- 输入消息后显示在用户气泡
- 等待AI响应时显示加载动画
- AI回复显示在左侧气泡
- 历史消息正确加载显示
```

---

## 阶段四：MCP工具集成

### Task 4.1: 实现天气工具（MCP）
**状态**: [x]
**优先级**: P1
**关联需求**: US-003
**文件**:
- `mrshudson-backend/src/main/java/com/mrshudson/mcp/weather/WeatherTool.java`
- `mrshudson-backend/src/main/java/com/mrshudson/service/WeatherService.java`

**实现内容**:
1. 创建天气工具类
2. 接入天气API（和风天气/高德）
3. 在Kimi Function Calling中注册

**_Prompt**:
```
Implement the task for spec mrshudson-core, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Java Backend Developer

Task: 实现天气查询MCP工具

Context:
- 使用和风天气API或高德天气API
- 需要在Kimi Function Calling中注册
- 工具会自动被AI调用

Requirements:
1. 创建WeatherService：
   - getCurrentWeather(String city)：获取当前天气
   - getWeatherForecast(String city, int days)：获取天气预报
   - 返回：温度、湿度、天气状况、风力等

2. 创建WeatherTool（MCP工具）：
   - 定义工具名称: "get_weather"
   - 定义参数: city(城市名), date(日期，可选)
   - 定义描述: "获取指定城市的天气信息"

3. 集成到ChatService：
   - 发送消息时，将WeatherTool添加到tools列表
   - 处理Kimi返回的function_call
   - 调用WeatherService获取天气
   - 将结果返回给Kimi生成回复

4. 在application.yml配置天气API key

Success Criteria:
- 用户发送"北京天气怎么样"
- Kimi识别意图，调用get_weather工具
- 返回正确的天气信息
- AI生成自然语言回复
```

---

### Task 4.2: 实现日历工具（MCP）
**状态**: [x]
**优先级**: P1
**关联需求**: US-004
**文件**:
- `mrshudson-backend/src/main/java/com/mrshudson/mcp/calendar/CalendarTool.java`
- `mrshudson-backend/src/main/java/com/mrshudson/service/CalendarService.java`
- `mrshudson-backend/src/main/java/com/mrshudson/domain/entity/CalendarEvent.java`

**实现内容**:
1. 创建日历实体和Repository
2. 创建日历工具
3. 实现增删改查API

**_Prompt**:
```
Implement the task for spec mrshudson-core, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Java Backend Developer

Task: 实现日历管理MCP工具和API

Context:
- 用户可以通过自然语言创建事件
- AI会调用calendar工具
- 需要存储到数据库

Requirements:
1. 创建CalendarEvent实体：
   - 字段：id, userId, title, description, startTime, endTime, location, category, reminderMinutes
   - 添加索引：userId + startTime

2. 创建CalendarRepository

3. 创建CalendarService：
   - createEvent(Long userId, CreateEventRequest)：创建事件
   - getEvents(Long userId, LocalDateTime start, LocalDateTime end)：查询事件
   - deleteEvent(Long userId, Long eventId)：删除事件

4. 创建CalendarTool（MCP）：
   - create_calendar_event: 创建事件
   - get_calendar_events: 查询事件
   - delete_calendar_event: 删除事件

5. 创建CalendarController API：
   - GET /api/calendar/events?start_date=&end_date=
   - POST /api/calendar/events
   - DELETE /api/calendar/events/{id}

Success Criteria:
- 用户说"明天下午3点开个会"
- AI调用create_calendar_event工具
- 事件保存到数据库
- API能查询到创建的事件
```

---

### Task 4.3: 实现前端日历视图
**状态**: [x]
**优先级**: P1
**关联需求**: US-004
**文件**:
- `mrshudson-frontend/src/views/CalendarView.vue`
- `mrshudson-frontend/src/components/CalendarMonth.vue`
- `mrshudson-frontend/src/api/calendar.ts`

**实现内容**:
1. 创建日历视图组件
2. 实现月视图显示
3. 实现事件创建弹窗

**_Prompt**:
```
Implement the task for spec mrshudson-core, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Vue Frontend Developer

Task: 实现前端日历视图

Context:
- 使用Element Plus组件或自定义日历
- 支持月视图
- 显示事件并支持点击创建

Requirements:
1. 创建CalendarView.vue：
   - 顶部：月份导航（上月/下月/今天）
   - 主体：月视图日历网格
   - 每天格子显示当天事件

2. 创建EventDialog组件：
   - 事件标题输入
   - 开始/结束时间选择
   - 分类选择（工作/个人/家庭）
   - 保存/取消按钮

3. 创建calendar.ts API：
   - getEvents(startDate, endDate)
   - createEvent(eventData)
   - deleteEvent(eventId)

4. 实现交互：
   - 点击日期格子打开创建弹窗
   - 点击事件显示详情
   - 月份切换加载对应数据

Success Criteria:
- 日历正确显示当月日期
- 事件在对应日期显示
- 可以创建新事件
- 创建后日历刷新显示
```

---

### Task 4.4: 实现待办工具（MCP）
**状态**: [x]
**优先级**: P1
**关联需求**: US-005
**文件**:
- `mrshudson-backend/src/main/java/com/mrshudson/mcp/todo/TodoTool.java`
- `mrshudson-backend/src/main/java/com/mrshudson/service/TodoService.java`
- `mrshudson-backend/src/main/java/com/mrshudson/domain/entity/TodoItem.java`

**实现内容**:
1. 创建待办实体和Repository
2. 创建待办工具
3. 实现API

**_Prompt**:
```
Implement the task for spec mrshudson-core, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Java Backend Developer

Task: 实现待办事项MCP工具和API

Requirements:
1. 创建TodoItem实体：
   - 字段：id, userId, title, description, priority(LOW/MEDIUM/HIGH), status, dueDate, completedAt

2. 创建TodoService：
   - createTodo(Long userId, CreateTodoRequest)
   - getTodos(Long userId, TodoFilter filter)
   - completeTodo(Long userId, Long todoId)
   - deleteTodo(Long userId, Long todoId)

3. 创建TodoTool（MCP）：
   - create_todo: 创建待办
   - list_todos: 列���待办
   - complete_todo: 完成待办

4. 创建TodoController API：
   - GET /api/todos?status=&priority=
   - POST /api/todos
   - PUT /api/todos/{id}/complete
   - DELETE /api/todos/{id}

Success Criteria:
- 用户说"提醒我明天买菜"
- AI创建待办事项
- API能查询和操作待办
```

---

### Task 4.5: 实现前端待办列表
**状态**: [x]
**优先级**: P1
**关联需求**: US-005
**文件**:
- `mrshudson-frontend/src/views/TodoView.vue`
- `mrshudson-frontend/src/components/TodoItem.vue`
- `mrshudson-frontend/src/api/todo.ts`

**实现内容**:
1. 创建待办列表视图
2. 实现待办项组件
3. 实现增删改查

**_Prompt**:
```
Implement the task for spec mrshudson-core, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Vue Frontend Developer

Task: 实现前端待办列表界面

Requirements:
1. 创建TodoView.vue：
   - 顶部：筛选标签（全部/进行中/已完成）
   - 主体：待办列表
   - 底部：输入框快速添加

2. 创建TodoItem组件：
   - 复选框标记完成
   - 标题（完成的有删除线）
   - 优先级标签（颜色区分）
   - 截止日期显示
   - 删除按钮

3. 实现功能：
   - 添加待办
   - 标记完成/取消完成
   - 删除待办
   - 按状态筛选

Success Criteria:
- 界面清晰美观
- 可以添加、完成、删除待办
- 筛选功能正常
```

---

## 阶段五：语音与进阶功能

### Task 5.1: 实现语音输入功能
**状态**: [x]
**优先级**: P1
**关联需求**: US-008
**文件**:
- `mrshudson-backend/src/main/java/com/mrshudson/service/VoiceService.java`
- `mrshudson-backend/src/main/java/com/mrshudson/controller/ChatController.java`（语音接口）
- `mrshudson-frontend/src/components/VoiceInputButton.vue`

**实现内容**:
1. 后端集成语音识别（阿里云/讯飞）
2. 实现语音上传接口
3. 前端实现录音按钮

**_Prompt**:
```
Implement the task for spec mrshudson-core, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Full Stack Developer

Task: 实现语音输入功能

Context:
- 后端：使用阿里云NLS或讯飞语音识别
- 前端：使用Web Audio API录音
- 语音转成文字后送入AI对话流程

Requirements:
1. 后端：
   - 创建VoiceService：
     - speechToText(MultipartFile audioFile)：调用ASR API
   - 添加POST /api/chat/voice接口：
     - 接收音频文件
     - 调用ASR转为文字
     - 调用ChatService处理文字
     - 返回AI回复

2. 前端：
   - 创建VoiceInputButton组件：
     - 按住录音按钮
     - 录音动画效果
     - 松开停止录音并上传
   - 集成到ChatView输入框

3. 配置ASR API key

Restrictions:
- 录音格式使用mp3或wav
- 限制录音时长60秒

Success Criteria:
- 按住按钮录音，松开后发送
- 语音转为文字并显示
- AI根据语音内容回复
```

---

### Task 5.2: 实现语音输出功能
**状态**: [x]
**优先级**: P2
**关联需求**: US-008
**文件**:
- `mrshudson-backend/src/main/java/com/mrshudson/service/VoiceService.java`（TTS方法）
- `mrshudson-frontend/src/components/ChatMessage.vue`（语音播放）

**实现内容**:
1. 后端集成语音合成（阿里云/讯飞）
2. AI回复时生成语音
3. 前端播放语音

**_Prompt**:
```
Implement the task for spec mrshudson-core, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Full Stack Developer

Task: 实现语音输出（文字转语音）功能

Requirements:
1. 后端：
   - VoiceService添加textToSpeech(String text)：
     - 调用TTS API生成音频
     - 保存音频文件到static目录
     - 返回音频URL
   - ChatService处理消息时：
     - 调用TTS生成语音
     - 将audioUrl加入返回结果

2. 前端：
   - ChatMessage组件添加播放按钮
   - 点击播放AI回复的语音
   - 使用HTML5 Audio API

Success Criteria:
- AI回复包含audioUrl
- 点击播放按钮能听到语音
- 支持暂停/继续播放
```

---

### Task 5.3: 实现智能提醒功能
**状态**: [x]
**优先级**: P2
**关联需求**: US-007
**文件**:
- `mrshudson-backend/src/main/java/com/mrshudson/service/ReminderService.java`
- `mrshudson-backend/src/main/java/com/mrshudson/job/ReminderJob.java`

**实现内容**:
1. 创建提醒服务
2. 实现定时任务扫描提醒
3. 推送通知

**_Prompt**:
```
Implement the task for spec mrshudson-core, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Java Backend Developer

Task: 实现智能提醒功能

Requirements:
1. 创建ReminderService：
   - createReminder(userId, type, title, content, remindAt)
   - getUnreadReminders(userId)
   - markAsRead(reminderId)

2. 创建ReminderJob（使用@Scheduled）：
   - 每分钟扫描一次reminder表
   - 找到remindAt <= 当前时间且未发送的提醒
   - 调用推送服务发送

3. 创建ReminderController API：
   - GET /api/reminders
   - PUT /api/reminders/{id}/read

4. 集成到业务：
   - 创建日历时，自动创建提醒
   - 待办到期前自动提醒

Success Criteria:
- 创建事件时自动生成提醒
- 到达提醒时间能触发（打印日志即可，V2再做真实推送）
```

---

## 阶段六：多端与部署

### Task 6.1: 实现移动端适配
**状态**: [x]
**优先级**: P2
**关联需求**: US-009
**文件**:
- `mrshudson-frontend/src/App.vue`（响应式）
- `mrshudson-frontend/src/styles/responsive.scss`

**实现内容**:
1. 响应式布局适配移动端
2. 移动端UI优化

**_Prompt**:
```
Implement the task for spec mrshudson-core, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Vue Frontend Developer

Task: 实现移动端适配

Requirements:
1. 使用CSS媒体查询适配移动端
2. ChatView移动端优化：
   - 侧边栏变为底部导航或抽屉
   - 消息气泡适配小屏幕
   - 输入框固定在底部
3. CalendarView移动端优化
4. TodoView移动端优化

Success Criteria:
- 手机浏览器访问界面正常
- 触摸操作流畅
- 布局自适应
```

---

### Task 6.2: 项目打包与部署配置
**状态**: [x]
**优先级**: P2
**关联需求**: 整体
**文件**:
- `docker-compose.yml`
- `mrshudson-backend/Dockerfile`
- `mrshudson-frontend/Dockerfile`

**实现内容**:
1. 创建Dockerfile
2. 创建docker-compose配置
3. 配置生产环境

**_Prompt**:
```
Implement the task for spec mrshudson-core, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: DevOps Engineer

Task: 创建Docker部署配置

Requirements:
1. 创建mrshudson-backend/Dockerfile：
   - 使用openjdk:17-jdk-slim
   - 复制jar包
   - 暴露8080端口

2. 创建mrshudson-frontend/Dockerfile：
   - 使用nginx:alpine
   - 复制dist文件
   - 配置nginx反向代理到后端

3. 创建docker-compose.yml：
   - services: mysql, redis, backend, frontend
   - 配置网络和数据卷
   - 配置环境变量

Success Criteria:
- docker-compose up 能启动所有服务
- 浏览器访问localhost能使用系统
```

---

## 任务依赖图

```
Task 1.1 (后端项目初始化)
    │
    ├── Task 1.3 (数据库实体) ─── Task 2.1 (登录后端)
    │                                    │
Task 1.2 (前端项目初始化)                 │
    │                                    │
    ├── Task 2.2 (登录前端) ─────────────┤
    │           │                        │
    │           └─────────────────────── Task 3.3 (对话界面)
    │                                    │
Task 3.1 (Kimi客户端) ───────────┬────── Task 3.2 (对话服务)
    │                            │
    ├── Task 4.1 (天气工具) ─────┤
    ├── Task 4.2 (日历工具) ─────┼────── Task 4.3 (日历界面)
    └── Task 4.4 (待办工具) ─────┼────── Task 4.5 (待办界面)
                                │
Task 5.1 (语音输入) ────────────┤
Task 5.2 (语音输出) ────────────┤
Task 5.3 (智能提醒) ────────────┤
Task 6.1 (移动端适配) ──────────┤
Task 6.2 (Docker部署) ──────────┘
```

---

## 开发顺序建议

### Sprint 1（快速跑通Demo）
1. Task 1.1 - 后端项目初始化
2. Task 1.2 - 前端项目初始化
3. Task 1.3 - 数据库实体
4. Task 2.1 - 登录后端
5. Task 2.2 - 登录前端
6. Task 3.1 - Kimi客户端
7. Task 3.2 - 对话服务
8. Task 3.3 - 对话界面

### Sprint 2（功能完善）
9. Task 4.1 - 天气工具
10. Task 4.2 - 日历工具
11. Task 4.3 - 日历界面
12. Task 4.4 - 待办工具
13. Task 4.5 - 待办界面

### Sprint 3（进阶与部署）
14. Task 5.1 - 语音输入
15. Task 5.2 - 语音输出
16. Task 5.3 - 智能提醒
17. Task 6.1 - 移动端适配
18. Task 6.2 - Docker部署

---

## 阶段七：出行路线规划（US-006）

### Task 7.1: 实现路线规划前端页面
**状态**: [x]
**优先级**: P2
**关联需求**: US-006
**文件**:
- `mrshudson-frontend/src/views/RouteView.vue`
- `mrshudson-frontend/src/api/route.ts`
- `mrshudson-backend/src/main/java/com/mrshudson/controller/RouteController.java`

**实现内容**:
1. 创建 RouteController 后端 API（POST /api/route/plan）
2. 创建 route.ts API 模块
3. 创建 RouteView.vue 页面（起点/终点输入、出行方式选择、路线结果显示）
4. 更新路由配置和导航菜单

**实现说明**:
- 支持三种出行方式：步行、驾车、公交
- 调用高德地图路径规划 API
- 后端返回文本格式的路线结果
- 前端使用 pre-line 样式保留换行格式
