# AI Agent 二次优化 - 任务文档

## 任务总览

本规格包含4个主要功能模块，共**20个实施任务**。

**任务统计:**
- 模块1 (兜底回答机制): 6个任务
- 模块2 (Token 统计显示): 5个任务
- 模块3 (上下文管理): 4个任务
- 模块4 (质量优化): 5个任务

**总计: 20个任务**

---

## 模块1: 兜底回答机制

- [ ] 1.1 创建 FallbackHandler 接口
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/fallback/FallbackHandler.java`
  - 定义兜底处理器的核心接口
  - _Leverage: 现有 ToolRegistry, IntentRouter
  - _Requirements: Requirement 1
  - _Prompt: 角色：Java 接口设计专家 | 任务：创建 FallbackHandler 接口，定义 shouldFallback 和 executeFallback 方法 | 限制：接口设计清晰，支持同步/异步 | 成功：接口可被实现

- [ ] 1.2 创建 FallbackDecisionStrategy 决策类
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/fallback/FallbackDecisionStrategy.java`
  - 实现兜底判断逻辑
  - _Leverage: IntentType 枚举
  - _Requirements: Requirement 1.1-1.3
  - _Prompt: 角色：业务逻辑专家 | 任务：实现兜底判断策略，包括工具不可用、执行失败、非工具类意图等场景 | 限制：判断逻辑清晰，可配置 | 成功：单元测试覆盖

- [ ] 1.3 创建 FallbackPromptBuilder 提示词构建器
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/fallback/FallbackPromptBuilder.java`
  - 构建兜底回答时的提示词
  - _Leverage: ToolRegistry.getToolDescriptions()
  - _Requirements: Requirement 1.3-1.4
  - _Prompt: 角色：提示词工程专家 | 任务：构建兜底回答提示词，包含工具参考和重要提示 | 限制：提示词不超过 500 字 | 成功：生成提示词可读性好

- [ ] 1.4 创建 FallbackHandlerImpl 默认实现
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/fallback/FallbackHandlerImpl.java`
  - 实现兜底处理器
  - _Leverage: KimiClient
  - _Requirements: Requirement 1.5-1.6
  - _Prompt: 角色：Java 实现专家 | 任务：实现 FallbackHandler 接口，调用 LLM 直接回答 | 限制：支持流式输出 | 成功：功能测试通过

- [ ] 1.5 修改 ChatServiceImpl 集成兜底机制
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/service/impl/ChatServiceImpl.java`
  - 在工具执行失败时触发兜底
  - _Leverage: 现有 ChatServiceImpl
  - _Requirements: Requirement 1.1-1.2
  - _Prompt: 角色：Java 集成专家 | 任务：修改 ChatServiceImpl，在工具不可用或失败时调用 FallbackHandler | 限制：不破坏现有功能 | 成功：集成测试通过

- [ ] 1.6 添加兜底日志和统计
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/fallback/FallbackMetrics.java`
  - 记录兜底触发次数和占比
  - _Leverage: Micrometer Metrics
  - _Requirements: Requirement 1.5
  - _Prompt: 角色：监控专家 | 任务：创建兜底指标计数器 | 成功：可在 Prometheus 查看

---

## 模块2: Token 统计显示

- [ ] 2.1 创建 TokenUsage 统计模型
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/token/TokenUsage.java`
  - Token 消耗数据结构
  - _Leverage: 无
  - _Requirements: Requirement 2
  - _Prompt: 角色：Java 模型专家 | 任务：创建 TokenUsage 类 | 成功：可序列化为 JSON

- [ ] 2.2 创建 TokenTracker 接口和实现
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/token/TokenTracker.java`
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/token/TokenTrackerService.java`
  - Token 追踪服务
  - _Leverage: RedisTemplate（可选）
  - _Requirements: Requirement 2.1-2.4
  - _Prompt: 角色：Java 服务专家 | 任务：实现 TokenTracker 接口，支持记录和统计 | 限制：线程安全 | 成功：单元测试通过

- [ ] 2.3 实现 Token 格式化显示
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/token/TokenFormatter.java`
  - 格式化 token 统计为友好文本
  - _Leverage: TokenTrackerService
  - _Requirements: Requirement 2.1-2.4
  - _Prompt: 角色：格式化为专家 | 任务：实现 formatStatistics 方法 | 成功：输出格式符合需求

- [ ] 2.4 修改 KimiClient 支持 Token 统计
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/mcp/KimiClient.java`
  - 从 API 响应中提取 token 使用量
  - _Leverage: 现有 KimiClient
  - _Requirements: Requirement 2.2
  - _Prompt: 角色：API 集成专家 | 任务：解析 Kimi API 响应中的 usage 字段 | 成功：正确提取 token 数

- [ ] 2.5 SSE 流式响应末尾追加 Token 统计
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/service/impl/ChatServiceImpl.java`
  - 流式输出完成后追加统计
  - _Leverage: KimiClient, TokenTracker
  - _Requirements: Requirement 2.5-2.6
  - _Prompt: 角色：流式响应专家 | 任务：修改 streamChat 方法，在流结束时追加 token 统计 | 成功：前端能收到统计信息

---

## 模块3: 上下文管理

- [ ] 3.1 创建 ContextManager 接口
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/context/ContextManager.java`
  - 上下文管理接口
  - _Leverage: 无
  - _Requirements: Requirement 4
  - _Prompt: 角色：接口设计专家 | 任务：定义上下文管理接口 | 成功：接口清晰

- [ ] 3.2 实现 ContextManagerImpl 上下文压缩
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/context/ContextManagerImpl.java`
  - 实现智能上下文压缩
  - _Leverage: ChatMessageMapper, KimiClient
  - _Requirements: Requirement 4.1-4.5
  - _Prompt: 角色：算法专家 | 任务：实现上下文压缩逻辑，保留关键信息 | 成功：压缩后 token 减少 50%+

- [ ] 3.3 集成上下文管理器到 ChatService
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/service/impl/ChatServiceImpl.java`
  - 在构建上下文时调用压缩
  - _Leverage: ContextManagerImpl
  - _Requirements: Requirement 4.1
  - _Prompt: 角色：集成专家 | 任务：修改 buildContext 方法，调用上下文压缩 | 成功：集成测试通过

- [ ] 3.4 添加上下文压缩触发条件配置
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim.java`
  - 可配置压缩阈值/config/OptimProperties
  - _Leverage: 现有 OptimProperties
  - _Requirements: Requirement 4.2
  - _Prompt: 角色：配置专家 | 任务：添加压缩相关配置项 | 成功：配置可生效

---

## 模块4: 质量优化

- [ ] 4.1 创建 QualityProperties 质量配置
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/quality/QualityProperties.java`
  - 质量优化配置
  - _Leverage: AIProperties
  - _Requirements: Requirement 3
  - _Prompt: 角色：配置专家 | 任务：创建质量配置类，支持 SPEED/BALANCED/QUALITY 模式 | 成功：配置可注入

- [ ] 4.2 实现 QualityOptimizer 质量优化器
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/quality/QualityOptimizer.java`
  - 根据问题复杂度自动调整参数
  - _Leverage: QualityProperties
  - _Requirements: Requirement 3.2-3.6
  - _Prompt: 角色：优化专家 | 任务：实现自动质量优化逻辑 | 成功：复杂问题自动提升质量

- [ ] 4.3 集成质量优化器到 ChatService
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/service/impl/ChatServiceImpl.java`
  - 调用质量优化器调整参数
  - _Leverage: QualityOptimizer
  - _Requirements: Requirement 3.1
  - _Prompt: 角色：集成专家 | 任务：修改请求构建逻辑 | 成功：参数优化生效

- [ ] 4.4 添加质量模式切换 API
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/controller/QualityController.java`
  - 允许用户切换质量模式
  - _Leverage: QualityProperties
  - _Requirements: Requirement 3.1
  - _Prompt: 角色：API 设计专家 | 任务：创建质量模式切换接口 | 成功：可通过 API 切换

- [ ] 4.5 添加质量优化日志和监控
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/quality/QualityMetrics.java`
  - 记录质量优化触发情况
  - _Leverage: Micrometer
  - _Requirements: Requirement 3.6
  - _Prompt: 角色：监控专家 | 任务：创建质量相关指标 | 成功：可监控优化效果

---

## 实施顺序建议

### 第一阶段：Token 统计（优先级：高）
- 任务 2.1 → 2.2 → 2.3 → 2.4 → 2.5
- 原因：用户可见度高，实现相对独立

### 第二阶段：兜底回答机制（优先级：高）
- 任务 1.1 → 1.2 → 1.3 → 1.4 → 1.5 → 1.6
- 原因：核心功能扩展，用户体验提升明显

### 第三阶段：上下文管理（优先级：中）
- 任务 3.1 → 3.2 → 3.3 → 3.4
- 原因：优化长对话体验

### 第四阶段：质量优化（优先级：中）
- 任务 4.1 → 4.2 → 4.3 → 4.4 → 4.5
- 原因：可选优化，按需实施
