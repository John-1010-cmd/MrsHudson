# AI Agent 二次优化 - 任务文档

## 任务总览

本规格包含7个主要功能模块，共**37个实施任务**（合并重复需求后）。

**任务统计:**
- 模块1 (兜底回答机制): 6个任务
- 模块2 (Token 统计显示): 5个任务
- 模块3 (上下文管理): 4个任务
- 模块4 (质量优化): 5个任务
- 模块5 (意图理解增强): 5个任务
- 模块6 (自纠错机制): 6个任务
- 模块7 (智能缓存与成本优化): 6个任务 ← **合并：原Requirement 5+8**

**总计: 38个任务**

---

## 模块1: 兜底回答机制

- [ ] 1.1 创建 FallbackHandler 接口
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/fallback/FallbackHandler.java`
  - 定义兜底处理器的核心接口
  - _Leverage: 现有 ToolRegistry, IntentRouter
  - _Requirements: Requirement 1
  - _Prompt: 角色：Java 接口设计专家 | 任务：创建 FallbackHandler 接口，定义 shouldFallback 和 executeFallback 方法 | 限制：接口设计清晰，支持同步/异步 | 成功：接口可被实现
  - _依赖: 无

- [ ] 1.2 创建 FallbackDecisionStrategy 决策类
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/fallback/FallbackDecisionStrategy.java`
  - 实现兜底判断逻辑
  - _Leverage: IntentType 枚举
  - _Requirements: Requirement 1.1-1.3
  - _Prompt: 角色：业务逻辑专家 | 任务：实现兜底判断策略，包括工具不可用、执行失败、非工具类意图等场景 | 限制：判断逻辑清晰，可配置 | 成功：单元测试覆盖
  - _依赖: 1.1

- [ ] 1.3 创建 FallbackPromptBuilder 提示词构建器
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/fallback/FallbackPromptBuilder.java`
  - 构建兜底回答时的提示词
  - _Leverage: ToolRegistry.getToolDescriptions()
  - _Requirements: Requirement 1.3-1.4
  - _Prompt: 角色：提示词工程专家 | 任务：构建兜底回答提示词，包含工具参考和重要提示 | 限制：提示词不超过 500 字 | 成功：生成提示词可读性好
  - _依赖: 1.1

- [ ] 1.4 创建 FallbackHandlerImpl 默认实现
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/fallback/FallbackHandlerImpl.java`
  - 实现兜底处理器
  - _Leverage: KimiClient
  - _Requirements: Requirement 1.5-1.6
  - _Prompt: 角色：Java 实现专家 | 任务：实现 FallbackHandler 接口，调用 LLM 直接回答 | 限制：支持流式输出 | 成功：功能测试通过
  - _依赖: 1.1, 1.2, 1.3

- [ ] 1.5 修改 ChatServiceImpl 集成兜底机制
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/service/impl/ChatServiceImpl.java`
  - 在工具执行失败时触发兜底
  - _Leverage: 现有 ChatServiceImpl
  - _Requirements: Requirement 1.1-1.2
  - _Prompt: 角色：Java 集成专家 | 任务：修改 ChatServiceImpl，在工具不可用或失败时调用 FallbackHandler | 限制：不破坏现有功能 | 成功：集成测试通过
  - _依赖: 1.4

- [ ] 1.6 添加兜底日志和统计
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/fallback/FallbackMetrics.java`
  - 记录兜底触发次数和占比
  - _Leverage: Micrometer Metrics
  - _Requirements: Requirement 1.5
  - _Prompt: 角色：监控专家 | 任务：创建兜底指标计数器 | 成功：可在 Prometheus 查看
  - _依赖: 1.5

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
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/config/OptimProperties.java`
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

---

## 模块5: 意图理解增强

- [ ] 5.1 创建 IntentResult 数据模型
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/IntentResult.java`
  - 意图识别结果数据结构
  - _Leverage: 无
  - _Requirements: Requirement 6
  - _Prompt: 角色：Java 模型专家 | 任务：创建 IntentResult 类，包含意图类型、置信度、候选列表 | 成功：可序列化

- [ ] 5.2 创建 IntentRouter 接口
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/IntentRouter.java`
  - 意图路由接口
  - _Leverage: 无
  - _Requirements: Requirement 6.1-6.4
  - _Prompt: 角色：接口设计专家 | 任务：定义意图识别、澄清判断、澄清构建方法 | 成功：接口清晰

- [ ] 5.3 实现 IntentConfidenceEvaluator 置信度评估
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/IntentConfidenceEvaluator.java`
  - 计算意图识别置信度
  - _Leverage: 关键词匹配
  - _Requirements: Requirement 6.1-6.2
  - _Prompt: 角色：算法专家 | 任务：实现置信度计算，基于关键词和上下文 | 成功：单元测试通过

- [ ] 5.4 实现 IntentClarificationService 澄清服务
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/IntentClarificationService.java`
  - 构建澄清问题
  - _Leverage: IntentResult
  - _Requirements: Requirement 6.1-6.3
  - _Prompt: 角色：对话设计专家 | 任务：实现多候选、低置信度、缺参数三种澄清场景 | 成功：生成友好澄清

- [ ] 5.5 集成意图路由到 ChatService
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/service/impl/ChatServiceImpl.java`
  - 修改对话流程支持澄清
  - _Leverage: IntentRouter
  - _Requirements: Requirement 6.1-6.5
  - _Prompt: 角色：集成专家 | 任务：修改对话流程，识别到模糊意图时先返回澄清 | 成功：集成测试通过

---

## 模块6: 自纠错机制

- [ ] 6.1 创建 ValidationResult 数据模型
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/correction/ValidationResult.java`
  - 验证结果数据结构
  - _Leverage: 无
  - _Requirements: Requirement 7
  - _Prompt: 角色：Java 模型专家 | 任务：创建 ValidationResult 类 | 成功：可序列化

- [ ] 6.2 创建 SelfCorrectingAgent 接口
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/correction/SelfCorrectingAgent.java`
  - 自纠错代理接口
  - _Leverage: 无
  - _Requirements: Requirement 7.1-7.4
  - _Prompt: 角色：接口设计专家 | 任务：定义验证和纠错重试方法 | 成功：接口清晰

- [ ] 6.3 实现 ToolResultValidator 工具结果校验
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/correction/ToolResultValidator.java`
  - 校验工具返回结果
  - _Leverage: ToolRegistry
  - _Requirements: Requirement 7.1-7.2
  - _Prompt: 角色：校验专家 | 任务：实现格式检查、错误标记检测、空结果检测 | 成功：单元测试通过

- [ ] 6.4 实现 CorrectionRetryStrategy 纠错重试
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/correction/CorrectionRetryStrategy.java`
  - 纠错后的重试逻辑
  - _Leverage: KimiClient
  - _Requirements: Requirement 7.3-7.4
  - _Prompt: 角色：重试策略专家 | 任务：实现错误分析、纠错提示构建、重试调用 | 成功：功能测试通过

- [ ] 6.5 实现 ErrorPatternLearner 错误模式学习
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/correction/ErrorPatternLearner.java`
  - 记录和学习错误模式
  - _Leverage: RedisTemplate
  - _Requirements: Requirement 7.5
  - _Prompt: 角色：学习系统专家 | 任务：实现错误记录、频率统计、规避指引生成 | 成功：Redis 存储正常

- [ ] 6.6 集成自纠错到工具执行流程
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/service/impl/ChatServiceImpl.java`
  - 工具执行后验证和纠错
  - _Leverage: SelfCorrectingAgent
  - _Requirements: Requirement 7.1-7.5
  - _Prompt: 角色：集成专家 | 任务：修改工具执行流程，验证失败时触发纠错 | 成功：集成测试通过

---

## 模块7: 智能缓存与成本优化

- [ ] 7.1 创建 CachedResult 数据模型
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/cost/CachedResult.java`
  - 缓存结果数据结构
  - _Leverage: 无
  - _Requirements: Requirement 5
  - _Prompt: 角色：Java 模型专家 | 任务：创建 CachedResult 类 | 成功：可序列化

- [ ] 7.2 创建 CostOptimizer 接口
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/cost/CostOptimizer.java`
  - 成本优化接口
  - _Leverage: 无
  - _Requirements: Requirement 5.1-5.5
  - _Prompt: 角色：接口设计专家 | 任务：定义缓存检查、模型选择、缓存保存方法 | 成功：接口清晰

- [ ] 7.3 实现 SemanticCacheService 语义缓存
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/cost/SemanticCacheService.java`
  - 支持精确和语义相似缓存
  - _Leverage: RedisTemplate, EmbeddingService
  - _Requirements: Requirement 5.1-5.3
  - _Prompt: 角色：缓存专家 | 任务：实现精确匹配和余弦相似度匹配，24h TTL | 成功：命中率测试通过
  - _依赖: 7.1, 7.2

- [ ] 7.4 实现 ModelRouter 模型路由
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/cost/ModelRouter.java`
  - 根据问题复杂度选择模型
  - _Leverage: KimiClient
  - _Requirements: Requirement 5.4
  - _Prompt: 角色：路由专家 | 任务：简单问题（<20字/问候）用小模型，其他用大模型 | 成功：路由测试通过
  - _依赖: 7.2

- [ ] 7.5 集成成本优化到 ChatService
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/service/impl/ChatServiceImpl.java`
  - 缓存命中直接返回，小模型快速处理
  - _Leverage: CostOptimizer
  - _Requirements: Requirement 5.1-5.5
  - _Prompt: 角色：集成专家 | 任务：修改对话流程，先检查缓存，再路由模型 | 成功：集成测试通过
  - _依赖: 7.3, 7.4

- [ ] 7.6 添加缓存命中统计
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/cost/CacheMetrics.java`
  - 统计缓存命中率
  - _Leverage: Micrometer
  - _Requirements: Requirement 5.3
  - _Prompt: 角色：监控专家 | 任务：创建缓存命中/未命中计数器 | 成功：Prometheus 可查看

- [ ] 7.7 实现缓存清除管理
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/cost/CacheInvalidationService.java`
  - 数据变更时自动清除相关缓存
  - _Leverage: RedisTemplate
  - _Requirements: Requirement 5.7
  - _Prompt: 角色：缓存专家 | 任务：实现用户数据变更时自动清除缓存 | 成功：清除测试通过
  - _依赖: 7.3

---

## 模块8: 意图识别向量缓存优化

- [ ] 8.1 创建 IntentCacheEntry 数据模型
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/cache/IntentCacheEntry.java`
  - 意图缓存条目数据结构
  - _Leverage: 无
  - _Requirements: Requirement 8
  - _Prompt: 角色：Java 模型专家 | 任务：创建 IntentCacheEntry 类，包含向量、意图类型、参数、统计信息 | 成功：可序列化

- [ ] 8.2 创建 IntentCacheStore 接口
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/cache/IntentCacheStore.java`
  - 意图缓存存储接口
  - _Leverage: 无
  - _Requirements: Requirement 8
  - _Prompt: 角色：接口设计专家 | 任务：定义缓存查询、保存、批量操作、统计接口 | 成功：接口清晰

- [ ] 8.3 实现 QueryNormalizer 查询归一化器
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/normalize/QueryNormalizer.java`
  - 时效性表达归一化
  - _Leverage: LocalDate
  - _Requirements: Requirement 8.4
  - _Prompt: 角色：文本处理专家 | 任务：实现日期/时间/星期表达归一化，今天→2026-03-23 | 成功：单元测试覆盖

- [ ] 8.4 实现 EmbeddingService 向量服务
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/embedding/EmbeddingService.java`
  - 向量嵌入服务（配置化）
  - _Leverage: MiniMax API / Kimi API
  - _Requirements: Requirement 8
  - _Prompt: 角色：AI 服务集成专家 | 任务：实现 EmbeddingService 接口，支持 MiniMax/M2.7 配置化调用 | 成功：向量生成正确

- [ ] 8.5 实现 Redis 向量缓存存储
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/cache/impl/RedisIntentCacheStore.java`
  - Redis Hash + Vector 存储
  - _Leverage: RedisTemplate, Redis Search
  - _Requirements: Requirement 8.1-8.3
  - _Prompt: 角色：Redis 专家 | 任务：实现 L2 缓存（Hash）和 L3 缓存（Vector HNSW），支持相似度搜索 | 成功：向量搜索测试通过

- [ ] 8.6 实现 VectorBasedRecognizer 向量识别器
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/recognizer/VectorBasedRecognizer.java`
  - 基于向量相似度的意图识别
  - _Leverage: EmbeddingService, IntentCacheStore
  - _Requirements: Requirement 8.7-8.8
  - _Prompt: 角色：向量搜索专家 | 任务：实现双维度排序（向量相似度+意图类型一致性），阈值 0.92 | 成功：识别测试通过

- [ ] 8.7 实现 HybridIntentCacheRouter 混合缓存路由
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/router/HybridIntentCacheRouter.java`
  - L1/L2/L3 分层缓存 + 熔断
  - _Leverage: QueryNormalizer, VectorBasedRecognizer, CircuitBreaker
  - _Requirements: Requirement 8.1-8.9
  - _Prompt: 角色：缓存架构专家 | 任务：实现分层缓存路由，支持熔断降级 | 成功：集成测试通过

- [ ] 8.8 实现 IntentCacheCircuitBreaker 熔断器
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/circuit/IntentCacheCircuitBreaker.java`
  - 缓存层熔断保护
  - _Leverage: AtomicInteger
  - _Requirements: Requirement 8.9
  - _Prompt: 角色：稳定性专家 | 任务：实现 CLOSED/OPEN/HALF_OPEN 三态熔断，失败10次打开，1分钟后半开 | 成功：熔断测试通过

- [ ] 8.9 实现冷启动预热策略
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/bootstrap/ColdStartStrategy.java`
  - 公共模板预热
  - _Leverage: IntentCacheStore
  - _Requirements: Requirement 8.5-8.6
  - _Prompt: 角色：启动优化专家 | 任务：实现50条公共模板预热，支持 min-samples 配置 | 成功：冷启动测试通过

- [ ] 8.10 添加缓存统计指标
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/monitor/IntentCacheMetrics.java`
  - L1/L2/L3 命中率、成本节省统计
  - _Leverage: Micrometer
  - _Requirements: Requirement 8.3
  - _Prompt: 角色：监控专家 | 任务：创建各层命中率、AI调用节省统计 | 成功：Prometheus 可查看

- [ ] 8.11 集成到 HybridIntentRouter
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/impl/HybridIntentRouter.java`
  - 意图缓存作为第一层
  - _Leverage: HybridIntentCacheRouter
  - _Requirements: Requirement 8
  - _Prompt: 角色：集成专家 | 任务：修改 HybridIntentRouter，缓存命中则跳过 AI 识别 | 成功：集成测试通过
  - _依赖: 8.7

- [ ] 8.12 添加意图识别模式切换
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/config/IntentRecognitionProperties.java`
  - AI-FIRST / CACHE-FIRST / RULE-FIRST 切换
  - _Leverage: OptimProperties
  - _Requirements: Requirement 9
  - _Prompt: 角色：配置专家 | 任务：实现模式切换配置和运行时变更 | 成功：配置生效
