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

---

## 阶段八：Android 原生应用开发

### Task 8.1: 初始化 Android 项目结构
**状态**: [ ]
**优先级**: P1
**关联需求**: US-009 (Android原生应用)
**文件**:
- `mrshudson-android/build.gradle.kts` (Project)
- `mrshudson-android/app/build.gradle.kts` (Module)
- `mrshudson-android/gradle/libs.versions.toml` (版本目录)
- `mrshudson-android/app/src/main/java/com/mrshudson/android/MainActivity.kt`

**实现内容**:
1. 创建 Android Studio 项目（Kotlin DSL）
2. 配置 Jetpack Compose 依赖
3. 设置 MVVM + Repository 架构
4. 配置 Hilt 依赖注入
5. 配置 Retrofit + OkHttp 网络层
6. 配置 Room 数据库

**_Prompt**:
```
Implement the task for spec mrshudson-core:

Role: Android Developer

Task: 初始化 MrsHudson Android 项目

Context:
- 项目根目录: /Users/huangzhuangcan/Documents/GitHub/John-1010-cmd/Mrs-Hudson
- 需要创建 mrshudson-android 子项目
- 技术栈: Kotlin + Jetpack Compose + MVVM

Requirements:
1. 创建 Android 项目结构：
   - minSdk: 26 (Android 8.0)
   - targetSdk: 34
   - compileSdk: 34

2. 配置核心依赖（libs.versions.toml）：
   - compose-bom: 2024.02.00
   - hilt: 2.50
   - retrofit: 2.9.0
   - room: 2.6.1
   - datastore: 1.0.0
   - navigation-compose: 2.7.7
   - material3: 1.2.0

3. 创建基础架构：
   - MainActivity（Compose入口）
   - MrsHudsonApplication（Application类）
   - di/模块：NetworkModule, DatabaseModule, RepositoryModule
   - data/包：repository, local, remote
   - ui/包：theme, components, screens, viewmodel

4. 配置网络层：
   - Retrofit 配置（baseUrl: http://10.0.2.2:8080/api/）
   - OkHttp 添加 JWT Token 拦截器
   - 统一的 Result 封装类

5. 配置本地存储：
   - Room 数据库基础配置
   - DataStore 用于 Token 存储

Restrictions:
- 使用 Kotlin DSL 而非 Groovy
- 遵循 Clean Architecture 分层
- 不要创建不必要的示例代码

Success Criteria:
- 项目能正常编译运行
- 显示 Hello MrsHudson 首页
- 依赖注入正常工作
```

---

### Task 8.2: 实现用户认证模块
**状态**: [ ]
**优先级**: P1
**关联需求**: US-001, US-009
**文件**:
- `mrshudson-android/app/src/main/java/com/mrshudson/android/data/remote/AuthApi.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/data/repository/AuthRepository.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/ui/screens/login/LoginScreen.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/ui/screens/login/LoginViewModel.kt`

**实现内容**:
1. 创建认证相关 API 接口
2. 实现 AuthRepository（登录、登出、Token 管理）
3. 创建登录页面 UI
4. 实现自动登录逻辑

**_Prompt**:
```
Implement the task for spec mrshudson-core:

Role: Android Developer

Task: 实现 Android 端用户认证模块

Context:
- 后端已提供 JWT 认证 API
- 需要存储 Token 实现自动登录
- 使用后端相同的 API: POST /api/auth/login

Requirements:
1. 创建数据模型：
   - LoginRequest(username, password)
   - LoginResponse(accessToken, refreshToken)
   - User(id, username)

2. 创建 AuthApi 接口：
   - @POST("auth/login") suspend fun login(...): Response<LoginResponse>
   - @POST("auth/logout") suspend fun logout()
   - @GET("auth/me") suspend fun getCurrentUser(): Response<User>

3. 创建 TokenManager：
   - 使用 DataStore 存储 accessToken 和 refreshToken
   - 提供 saveTokens(), getAccessToken(), clearTokens()

4. 创建 AuthRepository：
   - login(username, password): Result<User>
   - logout()
   - isLoggedIn(): Boolean
   - getCurrentUser(): Flow<User?>

5. 创建登录页面：
   - 用户名输入框（默认admin）
   - 密码输入框（默认admin，隐藏显示）
   - 登录按钮（带加载状态）
   - 错误提示

6. 创建 LoginViewModel：
   - 处理登录逻辑
   - 验证输入不为空
   - 调用 Repository 登录
   - 登录成功跳转到主页

7. 添加 AuthInterceptor：
   - 自动为请求添加 Authorization Header
   - Token 过期时尝试刷新

Success Criteria:
- 输入 admin/admin 能成功登录
- Token 正确保存到 DataStore
- 重启 App 自动登录
- 401 错误时跳转到登录页
```

---

### Task 8.3: 实现主页面框架与底部导航
**状态**: [ ]
**优先级**: P1
**关联需求**: US-009
**文件**:
- `mrshudson-android/app/src/main/java/com/mrshudson/android/ui/screens/main/MainScreen.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/ui/navigation/BottomNavItem.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/ui/navigation/MainNavigation.kt`

**实现内容**:
1. 创建主页面框架
2. 实现底部导航栏
3. 配置各功能页面的导航

**_Prompt**:
```
Implement the task for spec mrshudson-core:

Role: Android Developer

Task: 实现 Android 主页面框架与底部导航

Requirements:
1. 创建 BottomNavItem 枚举：
   - CHAT("对话", Icons.Default.Chat)
   - CALENDAR("日历", Icons.Default.CalendarToday)
   - TODO("待办", Icons.Default.CheckCircle)
   - WEATHER("天气", Icons.Default.WbSunny)
   - ROUTE("路线", Icons.Default.Map)

2. 创建 MainScreen：
   - Scaffold 布局
   - 底部 NavigationBar
   - 中间内容区域（NavHost）

3. 创建占位页面：
   - ChatScreen（空白，后续实现）
   - CalendarScreen（空白，后续实现）
   - TodoScreen（空白，后续实现）
   - WeatherScreen（空白，后续实现）
   - RouteScreen（空白，后续实现）

4. 实现导航状态保持：
   - 切换底部导航时保持页面状态
   - 使用 rememberSaveable 或 ViewModel

5. 设计主题：
   - 定义 MrsHudson 品牌色（暖色调）
   - 配置 Material3 主题
   - 支持亮色/暗色模式

Success Criteria:
- 底部导航有5个选项卡
- 切换时显示对应页面标题
- 界面美观，符合 Material3 规范
```

---

### Task 8.4: 实现 AI 对话功能
**状态**: [ ]
**优先级**: P1
**关联需求**: US-002
**文件**:
- `mrshudson-android/app/src/main/java/com/mrshudson/android/data/remote/ChatApi.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/data/repository/ChatRepository.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/ui/screens/chat/ChatScreen.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/ui/screens/chat/ChatViewModel.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/ui/components/chat/MessageBubble.kt`

**实现内容**:
1. 创建对话相关的 API 和数据模型
2. 实现对话 Repository
3. 创建聊天界面（类似微信/WhatsApp）
4. 实现消息发送和接收

**_Prompt**:
```
Implement the task for spec mrshudson-core:

Role: Android Developer

Task: 实现 Android 端 AI 对话功能

Context:
- 后端 API: POST /api/chat/send, GET /api/chat/history
- 消息包含 role: user/assistant/system
- 需要支持流式响应（可选）

Requirements:
1. 创建数据模型：
   - Message(id, role, content, createdAt)
   - SendMessageRequest(message, conversationId)

2. 创建 ChatApi：
   - POST /api/chat/send
   - GET /api/chat/history?limit=50

3. 创建 ChatRepository：
   - sendMessage(content): Result<Message>
   - getHistory(): Flow<List<Message>>

4. 创建 MessageBubble 组件：
   - 用户消息：右对齐，蓝色背景
   - AI 消息：左对齐，带 MrsHudson 头像
   - 显示发送时间
   - 支持 Markdown 文本渲染

5. 创建 ChatScreen：
   - 顶部：标题栏（显示"哈德森夫人"）
   - 中部：消息列表（LazyColumn，倒序）
   - 底部：输入框 + 发送按钮
   - 下拉刷新加载历史

6. 创建 ChatViewModel：
   - 维护消息列表状态
   - 发送消息逻辑
   - 加载历史记录
   - 加载状态管理

7. 实现细节：
   - 发送消息后自动滚动到底部
   - 输入框支持多行
   - 空消息时显示欢迎语

Success Criteria:
- 能发送消息并显示在列表中
- AI 回复正确显示
- 界面美观，体验流畅
```

---

### Task 8.5: 实现日历功能
**状态**: [ ]
**优先级**: P1
**关联需求**: US-004
**文件**:
- `mrshudson-android/app/src/main/java/com/mrshudson/android/data/remote/CalendarApi.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/data/repository/CalendarRepository.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/ui/screens/calendar/CalendarScreen.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/ui/screens/calendar/CalendarViewModel.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/ui/components/calendar/MonthView.kt`

**实现内容**:
1. 创建日历相关的 API 和数据模型
2. 实现日历 Repository
3. 创建月视图日历界面
4. 实现事件的增删改查

**_Prompt**:
```
Implement the task for spec mrshudson-core:

Role: Android Developer

Task: 实现 Android 端日历功能

Context:
- 后端 API: /api/calendar/events
- 需要支持日/周/月视图（先实现月视图）

Requirements:
1. 创建数据模型：
   - CalendarEvent(id, title, description, startTime, endTime, location, category)
   - CreateEventRequest, UpdateEventRequest

2. 创建 CalendarApi：
   - GET /api/calendar/events?start_date=&end_date=
   - POST /api/calendar/events
   - PUT /api/calendar/events/{id}
   - DELETE /api/calendar/events/{id}

3. 创建 CalendarRepository：
   - getEvents(startDate, endDate): Flow<List<CalendarEvent>>
   - createEvent(event): Result<CalendarEvent>
   - updateEvent(id, event): Result<CalendarEvent>
   - deleteEvent(id): Result<Unit>

4. 创建 MonthView 组件：
   - 显示月历网格
   - 每天格子显示事件指示点
   - 支持月份切换
   - 点击日期选中

5. 创建 CalendarScreen：
   - 顶部：月份标题 + 左右切换按钮
   - 中部：月视图
   - 底部：选中日期的事件列表
   - FAB 按钮添加事件

6. 创建 EventDialog：
   - 事件标题输入
   - 开始/结束时间选择（DateTimePicker）
   - 分类选择（工作/个人/家庭）
   - 保存/删除按钮

7. 创建 CalendarViewModel：
   - 当前月份状态
   - 选中日期状态
   - 事件列表管理

Success Criteria:
- 日历正确显示当月日期
- 事件在对应日期显示指示点
- 可以创建、编辑、删除事件
- 数据与后端同步
```

---

### Task 8.6: 实现待办事项功能
**状态**: [ ]
**优先级**: P1
**关联需求**: US-005
**文件**:
- `mrshudson-android/app/src/main/java/com/mrshudson/android/data/remote/TodoApi.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/data/repository/TodoRepository.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/ui/screens/todo/TodoScreen.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/ui/screens/todo/TodoViewModel.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/ui/components/todo/TodoItemCard.kt`

**实现内容**:
1. 创建待办相关的 API 和数据模型
2. 实现待办 Repository
3. 创建待办列表界面
4. 实现待办的增删改查和完成状态切换

**_Prompt**:
```
Implement the task for spec mrshudson-core:

Role: Android Developer

Task: 实现 Android 端待办事项功能

Requirements:
1. 创建数据模型：
   - TodoItem(id, title, description, priority, status, dueDate, completedAt)
   - Priority: LOW, MEDIUM, HIGH
   - Status: PENDING, COMPLETED

2. 创建 TodoApi：
   - GET /api/todos?status=&priority=
   - POST /api/todos
   - PUT /api/todos/{id}/complete
   - DELETE /api/todos/{id}

3. 创建 TodoRepository：
   - getTodos(filter): Flow<List<TodoItem>>
   - createTodo(todo): Result<TodoItem>
   - completeTodo(id): Result<Unit>
   - deleteTodo(id): Result<Unit>

4. 创建 TodoItemCard 组件：
   - 复选框标记完成
   - 标题（完成的有删除线）
   - 优先级标签（颜色区分）
   - 截止日期显示
   - 左滑删除

5. 创建 TodoScreen：
   - 顶部：筛选标签（全部/进行中/已完成）
   - 中部：待办列表（LazyColumn）
   - 底部：输入框快速添加
   - FAB 按钮添加详细待办

6. 创建 AddTodoDialog：
   - 标题输入
   - 描述输入（可选）
   - 优先级选择
   - 截止日期选择

7. 创建 TodoViewModel：
   - 待办列表管理
   - 筛选状态管理
   - 操作处理

Success Criteria:
- 可以添加、完成、删除待办
- 筛选功能正常
- 列表滑动流畅
- 数据与后端同步
```

---

### Task 8.7: 实现天气查询功能
**状态**: [ ]
**优先级**: P1
**关联需求**: US-003
**文件**:
- `mrshudson-android/app/src/main/java/com/mrshudson/android/data/remote/WeatherApi.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/data/repository/WeatherRepository.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/ui/screens/weather/WeatherScreen.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/ui/screens/weather/WeatherViewModel.kt`

**实现内容**:
1. 创建天气相关的 API 和数据模型
2. 实现天气 Repository
3. 创建天气展示界面
4. 支持定位获取当前城市天气

**_Prompt**:
```
Implement the task for spec mrshudson-core:

Role: Android Developer

Task: 实现 Android 端天气查询功能

Context:
- 天气数据通过后端 API 获取（后端调用高德天气 API）
- 需要 Android 定位权限获取当前城市

Requirements:
1. 创建数据模型：
   - WeatherInfo(city, temperature, weather, humidity, windDirection, windPower)
   - WeatherForecast(date, dayWeather, nightWeather, dayTemp, nightTemp)

2. 创建 WeatherApi：
   - GET /api/weather/current?city=
   - GET /api/weather/forecast?city=&days=

3. 创建 WeatherRepository：
   - getCurrentWeather(city): Result<WeatherInfo>
   - getForecast(city, days): Result<List<WeatherForecast>>

4. 创建 WeatherScreen：
   - 顶部：当前城市（可搜索切换）
   - 中部：当前天气大卡片
     - 温度大字显示
     - 天气图标
     - 湿度、风向等信息
   - 底部：未来7天预报列表

5. 实现定位功能：
   - 申请定位权限
   - 使用 FusedLocationProviderClient
   - 根据坐标反编码获取城市名
   - 定位失败时默认显示北京

6. 创建 WeatherViewModel：
   - 当前城市状态
   - 天气数据加载
   - 刷新逻辑

7. UI 细节：
   - 根据天气显示不同背景色（晴天蓝色、雨天灰色等）
   - 下拉刷新
   - 加载动画

Success Criteria:
- 能获取当前位置并显示天气
- 可以搜索其他城市
- 显示7天预报
- 界面美观直观
```

---

### Task 8.8: 实现路线规划功能
**状态**: [ ]
**优先级**: P2
**关联需求**: US-006
**文件**:
- `mrshudson-android/app/src/main/java/com/mrshudson/android/data/remote/RouteApi.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/ui/screens/route/RouteScreen.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/ui/screens/route/RouteViewModel.kt`

**实现内容**:
1. 创建路线规划相关的 API
2. 创建路线规划界面
3. 实现起点/终点输入和路线显示

**_Prompt**:
```
Implement the task for spec mrshudson-core:

Role: Android Developer

Task: 实现 Android 端路线规划功能

Requirements:
1. 创建数据模型：
   - RouteRequest(origin, destination, mode)
   - RouteInfo(distance, duration, toll, steps)
   - TravelMode: WALK, DRIVE, BUS

2. 创建 RouteApi：
   - POST /api/route/plan

3. 创建 RouteScreen：
   - 起点输入框（支持当前位置）
   - 终点输入框
   - 出行方式选择（步行/驾车/公交）
   - 查询按钮
   - 路线结果显示区域

4. 实现功能：
   - 使用定位获取当前位置作为起点
   - 调用后端 API 获取路线
   - 显示距离、时间、费用
   - 显示详细步骤列表

5. 创建 RouteViewModel：
   - 输入状态管理
   - 路线查询逻辑
   - 加载和错误状态

Success Criteria:
- 输入起点终点能查询路线
- 显示路线详细信息
- 支持三种出行方式
```

---

### Task 8.9: 集成 Firebase Cloud Messaging 推送
**状态**: [ ]
**优先级**: P1
**关联需求**: US-007, US-009
**文件**:
- `mrshudson-android/app/src/main/java/com/mrshudson/android/service/FcmService.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/data/repository/PushRepository.kt`

**实现内容**:
1. 配置 Firebase 项目
2. 集成 FCM SDK
3. 实现消息接收服务
4. 实现 Device Token 上报

**_Prompt**:
```
Implement the task for spec mrshudson-core:

Role: Android Developer

Task: 集成 FCM 推送通知

Context:
- 后端需要 deviceToken 才能发送推送
- 推送场景：日程提醒、待办提醒

Requirements:
1. Firebase 配置：
   - 创建 Firebase 项目
   - 下载 google-services.json
   - 应用 build.gradle 添加 FCM 插件和依赖

2. 创建 FcmService：
   - 继承 FirebaseMessagingService
   - onMessageReceived：处理接收到的消息
   - onNewToken：Token 刷新时上报

3. 创建通知显示：
   - 创建 NotificationChannel（Android 8.0+）
   - 显示通知标题和内容
   - 点击通知打开 MainActivity

4. 创建 PushRepository：
   - 上报 Device Token 到后端
   - POST /api/push/register {deviceToken, platform: "android"}

5. Token 管理：
   - 登录后上报 Token
   - Token 刷新时重新上报
   - 登出时注销 Token

6. 权限配置：
   - AndroidManifest.xml 添加服务声明
   - 申请 POST_NOTIFICATIONS 权限（Android 13+）

Success Criteria:
- 应用能获取 FCM Token
- Token 成功上报后端
- 能接收并显示推送通知
- 点击通知打开应用
```

---

### Task 8.10: 实现离线模式与数据同步
**状态**: [ ]
**优先级**: P2
**关联需求**: US-009
**文件**:
- `mrshudson-android/app/src/main/java/com/mrshudson/android/data/local/dao/*.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/data/sync/SyncManager.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/worker/SyncWorker.kt`

**实现内容**:
1. 配置 Room 数据库表
2. 实现本地数据缓存
3. 实现网络状态监听
4. 实现数据同步机制

**_Prompt**:
```
Implement the task for spec mrshudson-core:

Role: Android Developer

Task: 实现离线模式与数据同步

Context:
- 用户希望离线时也能查看数据
- 网络恢复时自动同步

Requirements:
1. Room 数据库设计：
   - MessageEntity（缓存最近100条对话）
   - EventEntity（缓存日历事件）
   - TodoEntity（缓存待办事项）

2. 创建 Dao 接口：
   - MessageDao: insert, getAll, deleteAll
   - EventDao: insert, getByDateRange, delete, getAll
   - TodoDao: insert, getAll, update, delete

3. 实现离线支持 Repository：
   - 优先从本地数据库读取
   - 网络可用时从后端刷新
   - 新数据保存到本地

4. 创建 SyncManager：
   - 监听网络状态变化
   - 网络恢复时触发同步
   - 显示同步状态

5. 创建 SyncWorker（WorkManager）：
   - 定期后台同步（每6小时）
   - 同步日历和待办数据

6. UI 状态显示：
   - 离线时显示提示条
   - 同步中显示加载指示
   - 显示最后同步时间

Success Criteria:
- 离线时能查看缓存数据
- 网络恢复自动同步
- 数据保持一致性
- 同步状态可见
```

---

### Task 8.11: 实现语音输入功能
**状态**: [ ]
**优先级**: P2
**关联需求**: US-008
**文件**:
- `mrshudson-android/app/src/main/java/com/mrshudson/android/ui/components/chat/VoiceInputButton.kt`
- `mrshudson-android/app/src/main/java/com/mrshudson/android/utils/VoiceRecognizer.kt`

**实现内容**:
1. 集成 Android SpeechRecognizer
2. 创建语音输入按钮组件
3. 实现语音转文字并发送

**_Prompt**:
```
Implement the task for spec mrshudson-core:

Role: Android Developer

Task: 实现 Android 端语音输入功能

Requirements:
1. 权限配置：
   - AndroidManifest.xml 添加 RECORD_AUDIO 权限
   - 动态申请麦克风权限

2. 创建 VoiceRecognizer：
   - 使用 Android SpeechRecognizer API
   - 开始录音、停止录音
   - 回调识别结果

3. 创建 VoiceInputButton 组件：
   - 麦克风图标按钮
   - 按住录音，松开发送
   - 录音时显示动画效果
   - 显示录音时长

4. 集成到 ChatScreen：
   - 输入框旁边添加语音按钮
   - 语音输入转成文字后发送
   - 显示识别中的加载状态

5. 错误处理：
   - 权限被拒绝提示
   - 识别失败重试
   - 网络错误处理

Restrictions:
- 使用系统 SpeechRecognizer（无需第三方SDK）
- 仅支持中文语音识别
- 录音时长限制60秒

Success Criteria:
- 按住按钮可以录音
   - 语音正确转为文字
   - 文字自动发送给 AI
```

---

### Task 8.12: Android 应用打包与发布配置
**状态**: [ ]
**优先级**: P2
**关联需求**: US-009
**文件**:
- `mrshudson-android/app/build.gradle.kts`（签名配置）
- `mrshudson-android/app/proguard-rules.pro`
- Keystore 配置

**实现内容**:
1. 配置签名密钥
2. 配置 ProGuard 混淆
3. 配置多渠道打包
4. 生成发布 APK/AAB

**_Prompt**:
```
Implement the task for spec mrshudson-core:

Role: Android Developer

Task: 配置 Android 应用发布打包

Requirements:
1. 签名配置：
   - 创建 release.keystore
   - build.gradle.kts 配置 signingConfigs
   - 本地属性文件管理密钥密码

2. ProGuard 配置：
   - 基础混淆规则
   - Retrofit/OkHttp 保留规则
   - Room 实体保留规则
   - Compose 保留规则

3. 构建配置：
   - 开启 minifyEnabled（混淆）
   - 开启 shrinkResources（资源压缩）
   - 配置多架构支持（arm64-v8a, armeabi-v7a）

4. 版本管理：
   - 配置 versionCode 自动递增
   - versionName 语义化版本

5. 输出配置：
   - APK 输出配置
   - AAB (Android App Bundle) 配置

6. 渠道配置（可选）：
   - 友盟/极光等多渠道打包支持

Success Criteria:
- ./gradlew assembleRelease 成功
- APK 已签名且能正常安装
- 混淆后功能正常
- 应用体积优化
```

---

## Android 开发任务依赖图

```
Task 8.1 (项目初始化)
    │
    ├── Task 8.2 (用户认证)
    │       │
    │       └── Task 8.3 (主页面框架)
    │               │
    │               ├── Task 8.4 (AI对话)
    │               ├── Task 8.5 (日历)
    │               ├── Task 8.6 (待办)
    │               ├── Task 8.7 (天气)
    │               └── Task 8.8 (路线规划)
    │
    ├── Task 8.9 (FCM推送)
    │       │
    │       └── Task 8.3 (集成到主页面)
    │
    ├── Task 8.10 (离线模式)
    │       │
    │       └── Task 8.5, 8.6 (日历待办支持离线)
    │
    └── Task 8.11 (语音输入)
            │
            └── Task 8.4 (集成到对话)

Task 8.12 (打包发布) - 依赖所有功能完成
```

---

## Android 开发 Sprint 规划

### Android Sprint 1（基础框架）
1. Task 8.1 - 项目初始化
2. Task 8.2 - 用户认证
3. Task 8.3 - 主页面框架

### Android Sprint 2（核心功能）
4. Task 8.4 - AI对话
5. Task 8.5 - 日历
6. Task 8.6 - 待办

### Android Sprint 3（功能完善）
7. Task 8.7 - 天气
8. Task 8.8 - 路线规划
9. Task 8.9 - FCM推送

### Android Sprint 4（进阶优化）
10. Task 8.10 - 离线模式
11. Task 8.11 - 语音输入
12. Task 8.12 - 打包发布

---

## 阶段九：iOS 原生应用开发（优先级 P2）

### Task 9.1: 初始化 iOS 项目结构
**状态**: [ ]
**优先级**: P2
**关联需求**: US-009 (iOS原生应用)
**文件**:
- `mrshudson-ios/MrsHudson.xcodeproj`
- `mrshudson-ios/MrsHudson/MrsHudsonApp.swift`
- `mrshudson-ios/MrsHudson/Info.plist`

**实现内容**:
1. 创建 Xcode 项目（SwiftUI）
2. 配置 MVVM 架构
3. 配置依赖注入（EnvironmentObject）
4. 配置网络层（URLSession + Codable）

**_Prompt**:
```
Implement the task for spec mrshudson-core:

Role: iOS Developer

Task: 初始化 MrsHudson iOS 项目

Context:
- 项目根目录: /Users/huangzhuangcan/Documents/GitHub/John-1010-cmd/Mrs-Hudson
- 需要创建 mrshudson-ios 子项目
- 技术栈: Swift + SwiftUI + Combine

Requirements:
1. 创建 iOS 项目结构：
   - iOS Deployment Target: 16.0+
   - Swift Language Version: 5.9
   - SwiftUI 生命周期

2. 配置项目组织：
   - Models/（数据模型）
   - Services/（网络、认证服务）
   - ViewModels/（业务逻辑）
   - Views/（SwiftUI 界面）
   - Utils/（工具类）

3. 创建基础文件：
   - MrsHudsonApp.swift（应用入口）
   - ContentView.swift（占位主界面）
   - NetworkManager.swift（网络请求封装）

4. 配置网络层：
   - baseURL: http://localhost:8080/api/
   - 支持 JWT Token 注入
   - 统一的错误处理

Success Criteria:
- 项目在 Xcode 中能正常编译运行
- 显示 Hello MrsHudson 首页
- 网络层配置正确
```

---

### Task 9.2: 实现 iOS 用户认证模块
**状态**: [ ]
**优先级**: P2
**关联需求**: US-001, US-009
**文件**:
- `mrshudson-ios/MrsHudson/Services/AuthService.swift`
- `mrshudson-ios/MrsHudson/ViewModels/AuthViewModel.swift`
- `mrshudson-ios/MrsHudson/Views/Login/LoginView.swift`

**实现内容**:
1. 创建认证服务
2. 实现 Token 管理（Keychain）
3. 创建登录页面

**_Prompt**:
```
Implement the task for spec mrshudson-core:

Role: iOS Developer

Task: 实现 iOS 端用户认证模块

Context:
- 后端已提供 JWT 认证 API
- 需要安全存储 Token

Requirements:
1. 创建数据模型：
   - LoginRequest/LoginResponse
   - User

2. 创建 AuthService：
   - login(username, password) async throws -> User
   - logout()
   - isAuthenticated() -> Bool

3. 创建 TokenStorage（Keychain）：
   - saveTokens(accessToken, refreshToken)
   - getAccessToken() -> String?
   - clearTokens()

4. 创建 LoginView：
   - 居中登录卡片
   - 用户名/密码输入框
   - 登录按钮
   - 错误提示

5. 创建 AuthViewModel：
   - 处理登录逻辑
   - 管理认证状态

Success Criteria:
- 输入 admin/admin 能成功登录
- Token 安全存储在 Keychain
- 启动时自动检查登录状态
```

---

### Task 9.3: 实现 iOS 主页面框架与 Tab 导航
**状态**: [ ]
**优先级**: P2
**关联需求**: US-009
**文件**:
- `mrshudson-ios/MrsHudson/Views/Main/MainView.swift`
- `mrshudson-ios/MrsHudson/Views/Main/TabItem.swift`

**实现内容**:
1. 创建主页面框架
2. 实现 TabView 底部导航
3. 配置各功能页面的导航

**_Prompt**:
```
Implement the task for spec mrshudson-core:

Role: iOS Developer

Task: 实现 iOS 主页面框架与 Tab 导航

Requirements:
1. 创建 TabItem 枚举：
   - chat, calendar, todo, weather, route
   - 名称和图标配置

2. 创建 MainView：
   - TabView 底部导航
   - 5 个选项卡
   - 每个选项卡对应一个页面

3. 创建占位页面：
   - ChatView, CalendarView, TodoView
   - WeatherView, RouteView

4. 设计主题：
   - 定义 MrsHudson 品牌色
   - 配置 SwiftUI 主题
   - 支持亮色/暗色模式

Success Criteria:
- TabView 有5个选项卡
- 切换时显示对应页面
- 界面美观，符合 iOS 设计规范
```

---

### Task 9.4: 实现 iOS AI 对话功能
**状态**: [ ]
**优先级**: P2
**关联需求**: US-002
**文件**:
- `mrshudson-ios/MrsHudson/Services/ChatService.swift`
- `mrshudson-ios/MrsHudson/ViewModels/ChatViewModel.swift`
- `mrshudson-ios/MrsHudson/Views/Chat/ChatView.swift`
- `mrshudson-ios/MrsHudson/Views/Chat/MessageBubble.swift`

**实现内容**:
1. 创建对话服务
2. 实现聊天界面
3. 实现消息发送和接收

**_Prompt**:
```
Implement the task for spec mrshudson-core:

Role: iOS Developer

Task: 实现 iOS 端 AI 对话功能

Requirements:
1. 创建数据模型：
   - Message(id, role, content, createdAt)

2. 创建 ChatService：
   - sendMessage(content) async throws -> Message
   - getHistory() async throws -> [Message]

3. 创建 MessageBubble：
   - 用户消息：右对齐，蓝色
   - AI 消息：左对齐，带头像

4. 创建 ChatView：
   - 消息列表（List / ScrollView）
   - 底部输入框 + 发送按钮
   - 下拉刷新历史

5. 创建 ChatViewModel：
   - 消息列表管理
   - 发送消息逻辑
   - 加载状态

Success Criteria:
- 能发送消息并显示
- AI 回复正确显示
- 界面美观
```

---

### Task 9.5: 实现 iOS 日历功能
**状态**: [ ]
**优先级**: P2
**关联需求**: US-004
**文件**:
- `mrshudson-ios/MrsHudson/Services/CalendarService.swift`
- `mrshudson-ios/MrsHudson/ViewModels/CalendarViewModel.swift`
- `mrshudson-ios/MrsHudson/Views/Calendar/CalendarView.swift`

**实现内容**:
1. 创建日历服务
2. 实现日历界面
3. 实现事件的增删改查

**_Prompt**:
```
Implement the task for spec mrshudson-core:

Role: iOS Developer

Task: 实现 iOS 端日历功能

Requirements:
1. 创建数据模型：
   - CalendarEvent
   - CreateEventRequest

2. 创建 CalendarService：
   - getEvents(startDate, endDate)
   - createEvent(event)
   - updateEvent(id, event)
   - deleteEvent(id)

3. 创建 CalendarView：
   - UICalendarView（iOS 16+）或自定义月视图
   - 事件列表显示
   - 添加事件按钮

4. 创建 EventEditor（Sheet）：
   - 标题输入
   - 时间选择
   - 分类选择
   - 保存/取消

5. 创建 CalendarViewModel：
   - 当前月份管理
   - 事件列表管理

Success Criteria:
- 日历正确显示
- 可以创建、编辑、删除事件
- 数据与后端同步
```

---

### Task 9.6: 实现 iOS 待办事项功能
**状态**: [ ]
**优先级**: P2
**关联需求**: US-005
**文件**:
- `mrshudson-ios/MrsHudson/Services/TodoService.swift`
- `mrshudson-ios/MrsHudson/ViewModels/TodoViewModel.swift`
- `mrshudson-ios/MrsHudson/Views/Todo/TodoView.swift`
- `mrshudson-ios/MrsHudson/Views/Todo/TodoItemRow.swift`

**实现内容**:
1. 创建待办服务
2. 实现待办列表界面
3. 实现待办的增删改查

**_Prompt**:
```
Implement the task for spec mrshudson-core:

Role: iOS Developer

Task: 实现 iOS 端待办事项功能

Requirements:
1. 创建数据模型：
   - TodoItem
   - Priority: low, medium, high

2. 创建 TodoService：
   - getTodos(filter)
   - createTodo(todo)
   - completeTodo(id)
   - deleteTodo(id)

3. 创建 TodoItemRow：
   - 复选框标记完成
   - 标题（完成的有删除线）
   - 优先级颜色
   - 滑动删除

4. 创建 TodoView：
   - 筛选标签（Picker）
   - 待办列表
   - 添加待办按钮

5. 创建 TodoViewModel：
   - 待办列表管理
   - 筛选状态

Success Criteria:
- 可以添加、完成、删除待办
- 筛选功能正常
- 界面美观
```

---

### Task 9.7: 实现 iOS 天气查询功能
**状态**: [ ]
**优先级**: P2
**关联需求**: US-003
**文件**:
- `mrshudson-ios/MrsHudson/Services/WeatherService.swift`
- `mrshudson-ios/MrsHudson/ViewModels/WeatherViewModel.swift`
- `mrshudson-ios/MrsHudson/Views/Weather/WeatherView.swift`

**实现内容**:
1. 创建天气服务
2. 实现天气展示界面
3. 支持定位获取当前城市天气

**_Prompt**:
```
Implement the task for spec mrshudson-core:

Role: iOS Developer

Task: 实现 iOS 端天气查询功能

Requirements:
1. 创建数据模型：
   - WeatherInfo
   - WeatherForecast

2. 创建 WeatherService：
   - getCurrentWeather(city)
   - getForecast(city, days)

3. 创建 WeatherView：
   - 当前天气大卡片
   - 详细信息
   - 7天预报列表
   - 城市搜索

4. 实现定位功能：
   - Core Location 获取位置
   - 地理编码获取城市名
   - 定位失败默认北京

5. 创建 WeatherViewModel：
   - 天气数据管理
   - 刷新逻辑

Success Criteria:
- 能获取当前位置并显示天气
- 可以搜索其他城市
- 显示7天预报
```

---

### Task 9.8: 实现 iOS 路线规划功能
**状态**: [ ]
**优先级**: P2
**关联需求**: US-006
**文件**:
- `mrshudson-ios/MrsHudson/Services/RouteService.swift`
- `mrshudson-ios/MrsHudson/ViewModels/RouteViewModel.swift`
- `mrshudson-ios/MrsHudson/Views/Route/RouteView.swift`

**实现内容**:
1. 创建路线规划服务
2. 创建路线规划界面
3. 实现起点/终点输入和路线显示

**_Prompt**:
```
Implement the task for spec mrshudson-core:

Role: iOS Developer

Task: 实现 iOS 端路线规划功能

Requirements:
1. 创建数据模型：
   - RouteRequest
   - RouteInfo

2. 创建 RouteService：
   - planRoute(request)

3. 创建 RouteView：
   - 起点/终点输入
   - 出行方式选择
   - 路线结果显示

4. 创建 RouteViewModel：
   - 输入管理
   - 路线查询

Success Criteria:
- 能查询路线
- 显示路线详情
```

---

### Task 9.9: 集成 iOS APNs 推送
**状态**: [ ]
**优先级**: P2
**关联需求**: US-007, US-009
**文件**:
- `mrshudson-ios/MrsHudson/Services/PushNotificationService.swift`
- `mrshudson-ios/MrsHudson/MrsHudson.entitlements`

**实现内容**:
1. 配置 APNs 证书
2. 实现 Device Token 上报
3. 实现推送接收处理

**_Prompt**:
```
Implement the task for spec mrshudson-core:

Role: iOS Developer

Task: 集成 iOS APNs 推送通知

Requirements:
1. 配置 APNs：
   - 开启 Push Notification Capability
   - 配置 App ID
   - 配置证书

2. 创建 PushNotificationService：
   - 请求推送权限
   - 获取 Device Token
   - 上报 Token 到后端

3. 处理推送：
   - 应用内显示通知
   - 点击通知跳转对应页面

Success Criteria:
- 能获取 Device Token
- Token 成功上报后端
- 能接收推送通知
```

---

### Task 9.10: 实现 iOS 离线模式与数据同步
**状态**: [ ]
**优先级**: P2
**关联需求**: US-009
**文件**:
- `mrshudson-ios/MrsHudson/CoreData/*.xcdatamodeld`
- `mrshudson-ios/MrsHudson/Services/SyncService.swift`

**实现内容**:
1. 配置 Core Data
2. 实现本地数据缓存
3. 实现网络状态监听和同步

**_Prompt**:
```
Implement the task for spec mrshudson-core:

Role: iOS Developer

Task: 实现 iOS 端离线模式与数据同步

Requirements:
1. Core Data 配置：
   - Message, Event, Todo 实体
   - 数据模型版本管理

2. 创建 SyncService：
   - 监听网络状态（NWPathMonitor）
   - 网络恢复时触发同步
   - 定期后台同步

3. 离线支持：
   - 优先读取本地数据
   - 网络可用时刷新
   - 显示离线状态提示

Success Criteria:
- 离线能查看缓存数据
- 网络恢复自动同步
```

---

### Task 9.11: 实现 iOS 语音输入功能
**状态**: [ ]
**优先级**: P2
**关联需求**: US-008
**文件**:
- `mrshudson-ios/MrsHudson/Views/Components/VoiceInputButton.swift`
- `mrshudson-ios/MrsHudson/Utils/SpeechRecognizer.swift`

**实现内容**:
1. 集成 Speech 框架
2. 创建语音输入按钮
3. 实现语音转文字并发送

**_Prompt**:
```
Implement the task for spec mrshudson-core:

Role: iOS Developer

Task: 实现 iOS 端语音输入功能

Requirements:
1. 权限配置：
   - Info.plist 添加麦克风权限说明
   - 请求语音识别权限

2. 创建 SpeechRecognizer：
   - 使用 Speech 框架
   - 开始/停止录音
   - 返回识别结果

3. 创建 VoiceInputButton：
   - 按住录音动画
   - 录音时长显示

4. 集成到 ChatView

Success Criteria:
- 按住按钮录音
- 语音转为文字
- 文字自动发送
```

---

### Task 9.12: iOS 应用打包与发布配置
**状态**: [ ]
**优先级**: P2
**关联需求**: US-009
**文件**:
- `mrshudson-ios/MrsHudson.xcodeproj/project.pbxproj`

**实现内容**:
1. 配置签名证书
2. 配置 App Icon 和启动图
3. 生成发布 IPA

**_Prompt**:
```
Implement the task for spec mrshudson-core:

Role: iOS Developer

Task: 配置 iOS 应用发布打包

Requirements:
1. 签名配置：
   - 配置 Development 证书
   - 配置 Distribution 证书

2. App 资源：
   - App Icon（各尺寸）
   - Launch Screen
   - Info.plist 配置

3. 构建设置：
   - Release 配置优化
   - 启用 Bitcode
   - 代码混淆（可选）

4. 归档导出：
   - Archive 应用
   - 导出 IPA

Success Criteria:
- 应用能正常 Archive
- IPA 能正常安装
- 准备上架 App Store
```

---

## iOS 开发任务依赖图

```
Task 9.1 (项目初始化)
    │
    ├── Task 9.2 (用户认证)
    │       │
    │       └── Task 9.3 (主页面框架)
    │               │
    │               ├── Task 9.4 (AI对话)
    │               ├── Task 9.5 (日历)
    │               ├── Task 9.6 (待办)
    │               ├── Task 9.7 (天气)
    │               └── Task 9.8 (路线规划)
    │
    ├── Task 9.9 (APNs推送)
    │       │
    │       └── Task 9.3 (集成到主页面)
    │
    ├── Task 9.10 (离线模式)
    │       │
    │       └── Task 9.5, 9.6 (日历待办支持离线)
    │
    └── Task 9.11 (语音输入)
            │
            └── Task 9.4 (集成到对话)

Task 9.12 (打包发布) - 依赖所有功能完成
```

---

## iOS 开发 Sprint 规划

### iOS Sprint 1（基础框架）
1. Task 9.1 - 项目初始化
2. Task 9.2 - 用户认证
3. Task 9.3 - 主页面框架

### iOS Sprint 2（核心功能）
4. Task 9.4 - AI对话
5. Task 9.5 - 日历
6. Task 9.6 - 待办

### iOS Sprint 3（功能完善）
7. Task 9.7 - 天气
8. Task 9.8 - 路线规划
9. Task 9.9 - APNs推送

### iOS Sprint 4（进阶优化）
10. Task 9.10 - 离线模式
11. Task 9.11 - 语音输入
12. Task 9.12 - 打包发布
