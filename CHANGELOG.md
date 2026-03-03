# MrsHudson 更新日志

## [Unreleased] - 2026-03-02

### 新增功能

#### 1. 会话管理功能
- **功能**: 支持多会话对话，可以创建、切换、删除会话
- **实现**:
  - 新增 `Conversation` 实体和数据库表
  - 新增 `ConversationMapper` 用于数据访问
  - 新增会话管理 API：`POST /chat/conversation`, `GET /chat/conversations`, `DELETE /chat/conversation/{id}`
  - 修改 `ChatMessage` 添加 `conversation_id` 外键关联
- **文件**:
  - `mrshudson-backend/src/main/java/com/mrshudson/domain/entity/Conversation.java`
  - `mrshudson-backend/src/main/java/com/mrshudson/mapper/ConversationMapper.java`
  - `mrshudson-backend/src/main/java/com/mrshudson/service/impl/ChatServiceImpl.java`
  - `mrshudson-backend/src/main/java/com/mrshudson/controller/ChatController.java`

#### 2. 前端会话侧边栏
- **功能**: 左侧显示会话列表，支持创建新会话、切换会话、删除会话
- **实现**:
  - 重构 `ChatView.vue` 添加会话侧边栏布局
  - 使用 Vue provide/inject 在父子组件间传递会话状态
  - 新增 `ChatRoom.vue` 接收注入的会话 ID 并加载对应消息
- **文件**:
  - `mrshudson-frontend/src/views/ChatView.vue`
  - `mrshudson-frontend/src/views/ChatRoom.vue`
  - `mrshudson-frontend/src/api/chat.ts`

### 修复

#### 1. 对话页面消息排序错误
- **问题**: 历史消息按时间倒序展示，新消息显示在顶部
- **修复**: 修改 `ChatServiceImpl.getChatHistory()` 方法，将 `orderByDesc` 改为 `orderByAsc`
- **文件**: `mrshudson-backend/src/main/java/com/mrshudson/service/impl/ChatServiceImpl.java:162`

#### 2. AI日期回答错误
- **问题**: AI回答"今天是2023年11月28日"，与实际日期不符
- **修复**:
  - 添加 `buildSystemPrompt()` 方法动态生成系统提示词
  - 在提示词中注入当前日期（格式：yyyy年MM月dd日）
- **文件**: `mrshudson-backend/src/main/java/com/mrshudson/service/impl/ChatServiceImpl.java`

#### 3. 天气查询功能报错
- **问题**: 调用 Kimi API 返回 `400 Bad Request: tool_call_id is not found`
- **原因**:
  1. `tool` 类型消息中不能包含 `name` 字段（即使为 null）
  2. RestTemplate 使用 Jackson 重新序列化，忽略了我们自定义的 Fastjson2 过滤器
- **修复**:
  - 添加 `PropertyFilter` 过滤 `Message` 中为 null 的 `name` 字段
  - 修改 `HttpEntity` 使用手动序列化后的 JSON 字符串
- **文件**: `mrshudson-backend/src/main/java/com/mrshudson/mcp/kimi/KimiClient.java`

#### 4. 前端空白页面问题
- **问题**: 前端页面空白，无法正常加载
- **原因**:
  1. axios baseURL 配置为绝对路径，与 Vite 代理配置冲突
  2. TypeScript 类型错误导致构建失败
- **修复**:
  - 修改 axios baseURL 为相对路径 `/api`
  - 修复 EventDialog.vue 中 category 类型定义
  - 修复 CalendarView.vue 中 v-model:visible 绑定
  - 添加 env.d.ts 类型声明文件
- **文件**:
  - `mrshudson-frontend/src/api/axios.ts`
  - `mrshudson-frontend/src/api/chat.ts`
  - `mrshudson-frontend/src/components/EventDialog.vue`
  - `mrshudson-frontend/src/views/CalendarView.vue`
  - `mrshudson-frontend/env.d.ts`

### 配置变更

#### 1. JDK17 支持
- 配置项目使用 JDK17 编译
- 更新 Maven 配置文件指定 JDK17 路径

#### 2. AI 模型简化
- 删除讯飞星火模型支持（暂不支持工具调用）
- 保留 Kimi 作为唯一 AI 提供商
- 简化 `AIProperties` 和 `AIProvider` 配置

### 技术说明

#### Kimi API 工具调用注意事项
1. **assistant 消息**包含 `tool_calls` 时，`content` 必须为 `null`（不能是空字符串）
2. **tool 消息**不能包含 `name` 字段
3. 序列化时需要保留 null 值字段（使用 `WriteMapNullValue`）

#### 相关代码变更
```java
// KimiClient.java - 添加属性过滤器
private static final PropertyFilter MESSAGE_NAME_FILTER = (object, name, value) -> {
    if (object instanceof Message && "name".equals(name) && value == null) {
        return false; // 跳过 null 的 name 字段
    }
    return true;
};

// 使用过滤器序列化请求体
String requestBody = JSON.toJSONString(request, MESSAGE_NAME_FILTER,
    JSONWriter.Feature.WriteMapNullValue);
HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
```

## [Unreleased] - 2026-03-03

### 新增功能

#### 1. JWT 认证机制（替换 Session）
- **功能**: 实现完整的 JWT Token 认证和刷新机制，替代原有的 Session 认证
- **实现**:
  - 添加 JWT 依赖（jjwt-api, jjwt-impl, jjwt-jackson）
  - 创建 `JwtTokenUtil` 工具类，支持 Access Token 和 Refresh Token 生成/验证
  - 创建 `JwtContext` ThreadLocal 上下文，在请求线程中传递 Token
  - 创建 `JwtAuthenticationFilter` 过滤器，自动验证请求头中的 Token
  - 修改 `AuthService` 和 `AuthServiceImpl`，使用 Redis 存储 Refresh Token
  - 添加 `/auth/refresh` 接口用于刷新 Token
  - 修改前端 `axios.ts` 拦截器，自动添加 Authorization Header 并处理 401 刷新
  - 修改前端 `user.ts` store，管理 Token 的存储和刷新
- **文件**:
  - `mrshudson-backend/src/main/java/com/mrshudson/util/JwtTokenUtil.java` (新建)
  - `mrshudson-backend/src/main/java/com/mrshudson/util/JwtContext.java` (新建)
  - `mrshudson-backend/src/main/java/com/mrshudson/filter/JwtAuthenticationFilter.java` (新建)
  - `mrshudson-backend/src/main/java/com/mrshudson/service/impl/AuthServiceImpl.java`
  - `mrshudson-backend/src/main/java/com/mrshudson/controller/AuthController.java`
  - `mrshudson-backend/src/main/java/com/mrshudson/config/SecurityConfig.java`
  - `mrshudson-frontend/src/api/axios.ts`
  - `mrshudson-frontend/src/stores/user.ts`
  - `mrshudson-backend/src/main/resources/application.yml`

#### 2. 出行路线规划功能
- **功能**: 支持步行、驾车、公交三种出行方式的路线规划
- **实现**:
  - 创建 `RouteService` 服务类，调用高德地图路径规划 API
  - 创建 `RouteTool` MCP 工具，注册 `plan_route` 工具供 AI 调用
  - 支持起点/终点地址自动解析为经纬度坐标
  - 返回详细的路线信息：距离、时间、步骤、费用等
  - API 异常时返回模拟数据降级处理
- **文件**:
  - `mrshudson-backend/src/main/java/com/mrshudson/service/RouteService.java` (新建)
  - `mrshudson-backend/src/main/java/com/mrshudson/mcp/route/RouteTool.java` (新建)
  - `mrshudson-backend/src/main/java/com/mrshudson/service/impl/ChatServiceImpl.java` (系统提示词更新)

#### 3. 消息时间格式优化
- **功能**: 对话历史消息显示完整的年月日时分
- **修改**: 从 `HH:mm` 改为 `YYYY-MM-DD HH:mm` 格式
- **文件**: `mrshudson-frontend/src/views/ChatRoom.vue`

#### 2. 对话标题 AI 概括
- **功能**: 用户发送第一条消息时，自动调用 AI 生成会话标题
- **实现**:
  - 添加 `@EnableAsync` 启用 Spring 异步支持
  - 创建 `AsyncConfig` 配置线程池
  - 添加 `generateConversationTitle` 方法异步生成标题
  - 标题生成提示词："请用10-15字概括以下对话的主题..."
- **文件**:
  - `mrshudson-backend/src/main/java/com/mrshudson/config/AsyncConfig.java` (新建)
  - `mrshudson-backend/src/main/java/com/mrshudson/MrshudsonApplication.java`
  - `mrshudson-backend/src/main/java/com/mrshudson/mapper/ConversationMapper.java`
  - `mrshudson-backend/src/main/java/com/mrshudson/service/impl/ChatServiceImpl.java`

### 修复

#### 1. 页面切换空白问题
- **问题**: 从对话切换到日历/待办/天气再切回对话时出现空白
- **修复**:
  - 使用 `:key="route.fullPath"` 强制 RouterView 在路由变化时重新创建组件
  - 修复 `inject` 的类型定义，避免 `readonly` 导致的响应式问题
  - 添加空状态显示（当没有消息时显示 "暂无消息，开始对话吧"）
- **文件**:
  - `mrshudson-frontend/src/views/ChatView.vue`
  - `mrshudson-frontend/src/views/ChatRoom.vue`

#### 2. 会话切换导航问题
- **问题**: 从日历/待办/天气页面点击左侧对话，页面未自动跳转
- **修复**: 在 `switchConversation` 中添加路由判断，自动导航到对话页面
- **文件**: `mrshudson-frontend/src/views/ChatView.vue`

#### 3. 登录错误提示优化
- **功能**: 区分网络错误、服务器错误、认证失败等不同错误类型
- **文件**:
  - `mrshudson-frontend/src/stores/user.ts`
  - `mrshudson-frontend/src/views/LoginView.vue`

#### 4. 天气服务降级处理
- **功能**: 当天气 API 无法连接或密钥无效时，返回模拟数据并给出友好提示
- **实现**:
  - 检查 API 密钥是否配置
  - 捕获 `ResourceAccessException` 网络连接错误
  - 返回带提示的模拟天气数据
- **文件**: `mrshudson-backend/src/main/java/com/mrshudson/service/WeatherService.java`

### 待办事项（已完成）

- [x] 配置有效的 `WEATHER_API_KEY`（已配置，可能需要验证有效性）
- [x] 验证前端页面加载是否正常
- [x] 验证会话创建、切换、删除功能
- [x] 验证页面导航（日历/待办/天气）是否正常
- [ ] 考虑添加工具调用重试机制
- [x] 优化工具返回错误时的 AI 回复内容（已实现降级提示）

## [1.0.0] - 初始版本

### 功能
- 用户认证（登录/注册）
- AI 对话功能（集成 Kimi API）
- 天气查询工具
- 日历管理工具
- 待办事项管理工具
