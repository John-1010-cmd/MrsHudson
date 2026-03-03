# MrsHudson 待办事项

## 近期修复（已完成）

- [x] 修复对话页面消息排序错误（改为正序）
- [x] 修复 AI 日期回答错误（系统提示词注入当前日期）
- [x] 修复天气查询功能报错（Kimi API tool_call_id 问题）
- [x] 添加会话历史功能（创建、切换、删除会话）
- [x] 修复前端页面路由和空白页面问题
- [x] 配置 JDK17 编译环境
- [x] 修复页面切换导致的空白问题（添加 keep-alive 后又移除，改为强制重新渲染）
- [x] 优化消息时间显示格式（改为 YYYY-MM-DD HH:mm）
- [x] 实现对话标题 AI 概括功能
- [x] 修复天气服务错误处理（添加模拟数据降级）
- [x] 修复登录错误提示（区分网络错误、服务器错误、认证失败）
- [x] 修复从日历/待办/天气切回对话的导航问题

## 2026-03-02 会话功能开发完成

### 后端变更
- [x] 新增 `Conversation` 实体和 `conversation` 表
- [x] 修改 `ChatMessage` 添加 `conversation_id` 字段
- [x] 新增 `ConversationMapper` 和 `ConversationDTO`
- [x] 新增会话管理 API（创建、列表、删除、切换）
- [x] 修改 `ChatController` 支持按会话查询历史消息
- [x] 删除讯飞星火模型支持（暂不支持工具调用）

### 前端变更
- [x] 重构 `ChatView.vue` 添加左侧会话列表
- [x] 新增 `ConversationDTO` 和会话相关 API
- [x] 使用 provide/inject 传递当前会话状态
- [x] 修复 Vue Router 子路由配置
- [x] 修复 TypeScript 类型错误（EventDialog, CalendarView 等）
- [x] 修复 axios baseURL 配置（改为相对路径 `/api`）

### 待验证
- [x] 登录后页面是否正常加载
- [x] 会话列表是否正确显示
- [x] 创建新会话是否正常工作
- [x] 切换页面（日历/待办/天气）是否正常

## 2026-03-03 页面切换、体验优化、JWT认证、路线规划

### 已完成功能

#### 1. JWT 认证机制实现 ✅
- **功能**: 实现完整的 JWT Token 认证和刷新机制，替代 Session 认证
- **Token 设计**:
  - Access Token: 有效期 1 小时
  - Refresh Token: 有效期 7 天，存储在 Redis 中
- **实现**:
  - 后端：创建 `JwtTokenUtil`、`JwtContext`、`JwtAuthenticationFilter`
  - 后端：修改 `AuthServiceImpl` 使用 Redis 存储 Refresh Token
  - 后端：添加 `/auth/refresh` 刷新接口
  - 前端：修改 `axios.ts` 拦截器，自动处理 Token 刷新
  - 前端：修改 `user.ts` store，管理 Token 存储
- **文件**:
  - `mrshudson-backend/src/main/java/com/mrshudson/util/JwtTokenUtil.java` (新建)
  - `mrshudson-backend/src/main/java/com/mrshudson/util/JwtContext.java` (新建)
  - `mrshudson-backend/src/main/java/com/mrshudson/filter/JwtAuthenticationFilter.java` (新建)
  - `mrshudson-backend/src/main/java/com/mrshudson/service/impl/AuthServiceImpl.java`
  - `mrshudson-frontend/src/api/axios.ts`
  - `mrshudson-frontend/src/stores/user.ts`

#### 2. 出行路线规划功能 ✅
- **功能**: 支持步行、驾车、公交三种出行方式的路线规划
- **实现**:
  - 创建 `RouteService` 调用高德路径规划 API
  - 创建 `RouteTool` MCP 工具 (`plan_route`)
  - 支持地址自动解析为经纬度
  - 返回距离、时间、详细步骤、费用等信息
  - API 异常时返回模拟数据降级
- **文件**:
  - `mrshudson-backend/src/main/java/com/mrshudson/service/RouteService.java` (新建)
  - `mrshudson-backend/src/main/java/com/mrshudson/mcp/route/RouteTool.java` (新建)
  - `mrshudson-backend/src/main/java/com/mrshudson/service/impl/ChatServiceImpl.java`

#### 3. 页面切换空白问题修复
- **问题**: 从对话切换到日历/待办/天气再切回对话时出现空白
- **修复方案**:
  - 使用 `:key="route.fullPath"` 强制 RouterView 重新创建组件
  - 修复 provide/inject 的类型定义问题
  - 添加空状态显示
- **文件**:
  - `mrshudson-frontend/src/views/ChatView.vue`
  - `mrshudson-frontend/src/views/ChatRoom.vue`

#### 4. 消息时间格式优化
- **修改**: 从 `HH:mm` 改为 `YYYY-MM-DD HH:mm` 格式
- **文件**: `mrshudson-frontend/src/views/ChatRoom.vue`

#### 5. 对话标题 AI 概括
- **功能**: 用户发送第一条消息时，异步调用 AI 生成会话标题
- **实现**:
  - 添加 `@EnableAsync` 启用异步支持
  - 创建 `AsyncConfig` 配置线程池
  - 在 `ChatServiceImpl` 中添加 `checkAndGenerateTitle` 和 `generateConversationTitle` 方法
  - 添加 `ConversationMapper.updateTitle` 方法
- **文件**:
  - `mrshudson-backend/src/main/java/com/mrshudson/config/AsyncConfig.java` (新建)
  - `mrshudson-backend/src/main/java/com/mrshudson/MrshudsonApplication.java`
  - `mrshudson-backend/src/main/java/com/mrshudson/mapper/ConversationMapper.java`
  - `mrshudson-backend/src/main/java/com/mrshudson/service/impl/ChatServiceImpl.java`

#### 6. 天气服务降级处理
- **功能**: 当天气 API 无法连接或密钥无效时，返回模拟数据并给出友好提示
- **文件**: `mrshudson-backend/src/main/java/com/mrshudson/service/WeatherService.java`

#### 7. 登录错误提示优化
- **功能**: 区分网络错误、服务器错误、认证失败等不同错误类型
- **文件**:
  - `mrshudson-frontend/src/stores/user.ts`
  - `mrshudson-frontend/src/views/LoginView.vue`

#### 8. 会话切换导航修复
- **问题**: 从日历/待办/天气页面点击左侧对话，页面未自动跳转
- **修复**: 在 `switchConversation` 中添加路由判断和自动导航
- **文件**: `mrshudson-frontend/src/views/ChatView.vue`

## 待处理事项

### 高优先级

- [ ] **配置 JWT 密钥**
  - 在 `.env` 文件中配置 `JWT_SECRET`（建议至少 256 位随机字符串）
  - 示例：`JWT_SECRET=your-256-bit-secret-key-change-in-production`

- [x] **配置天气 API 密钥** (已配置，但可能需要验证有效性)
  - 在 `.env` 文件中配置有效的 `WEATHER_API_KEY`
  - 获取地址：https://lbs.amap.com/api/webservice/guide/api/weatherinfo

- [ ] **添加工具调用重试机制**
  - 当工具调用失败时（如网络超时），支持自动重试
  - 最多重试 3 次，每次间隔递增

### 中优先级

- [ ] **JWT Token 续期优化**
  - 添加 Token 即将过期自动刷新机制（当前是 401 后才刷新）
  - 在前端拦截请求前检查 Token 过期时间

- [x] **优化工具错误提示** (已实现降级提示)
  - 当工具返回错误时，AI 应该给出更友好的提示
  - 例如天气查询失败时，提示用户检查 API 密钥配置

- [ ] **对话历史分页加载**
  - 当前是一次性加载所有历史消息
  - 改为分页加载（每次加载 20 条，滚动到顶部时加载更多）

- [ ] **添加单元测试**
  - `JwtTokenUtil` 生成和验证测试
  - `RouteService` 路线规划测试
  - `AuthService` JWT 认证流程测试
  - 目标覆盖率：80%+

### 低优先级

- [ ] **路线规划功能增强**
  - 支持多个途经点规划
  - 支持避让高速/收费路段选项
  - 在地图上可视化显示路线（需要前端地图组件）

- [ ] **语音输入优化**
  - 支持更长的语音输入
  - 添加语音识别加载动画

- [ ] **前端消息动画**
  - 添加消息发送/接收的过渡动画
  - AI 回复打字机效果（可选）

- [ ] **移动端适配**
  - 优化移动端对话界面
  - 添加底部安全区域适配

## 技术债务

- [ ] **代码重构**
  - `ChatServiceImpl` 中的工具调用逻辑可以抽取到单独的服务
  - 考虑使用策略模式处理不同类型的工具调用

- [ ] **日志优化**
  - 敏感信息（API Key、JWT Token）脱敏处理
  - 添加更详细的调用链路追踪

## 新功能想法

- [ ] **多轮对话上下文优化**
  - 智能截断超长对话历史，保留关键信息

- [ ] **用户偏好设置**
  - 记住用户的常用城市
  - 自定义 AI 回复风格

- [ ] **导出对话记录**
  - 支持导出为 Markdown 或 PDF

---

**最后更新**: 2026-03-03
