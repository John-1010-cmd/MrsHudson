# AI调用成本优化 - 需求文档

## Introduction

随着MrsHudson用户量增长，AI调用成本呈线性上升趋势。当前架构中每个用户请求都直接调用Kimi API，缺乏有效的缓存和优化机制。本规格文档定义一套完整的AI调用成本优化方案，通过多层缓存、意图识别、对话压缩等技术手段，在保证用户体验的前提下显著降低token开销。

## Alignment with Product Vision

MrsHudson定位为"贴心的私人管家助手"，核心优势在于：
- **快速响应**：用户期望管家能即时回复
- **精准服务**：天气、日历、待办等工具的高效调用
- **持久记忆**：长对话上下文的保持

本优化方案通过智能缓存减少重复AI调用，通过意图识别加速工具调用，通过对话压缩保持长对话能力，完全符合产品愿景。

## Requirements

### Requirement 1: 智能语义缓存系统

**User Story:** 作为系统管理员，我希望重复的常见问题能够直接命中缓存返回，无需调用AI，以降低API成本。

#### Acceptance Criteria

1. WHEN 用户发送消息 AND 该消息与历史问题的语义相似度超过0.92 THEN 系统 SHALL 直接返回缓存的AI回复，不进行API调用
2. IF 缓存命中 THEN 系统 SHALL 记录缓存命中日志，用于后续优化分析
3. WHEN 缓存未命中 AND 完成AI调用后 THEN 系统 SHALL 将问题和回复存入语义缓存，供后续复用
4. IF 缓存数据超过7天未使用 THEN 系统 SHALL 自动清理过期缓存数据
5. WHEN 用户明确发送"清除缓存"指令 THEN 系统 SHALL 清除该用户的所有语义缓存

### Requirement 2: 意图识别路由层

**User Story:** 作为用户，我希望天气、日历、待办等工具类查询能够快速响应，不经过AI生成。

#### Acceptance Criteria

1. WHEN 用户发送天气相关查询（包含"天气"、"温度"、"下雨"等关键词）THEN 系统 SHALL 直接调用天气API返回结果，零AI token消耗
2. WHEN 用户发送日历相关查询（包含"日程"、"会议"、"今天有什么"等关键词）THEN 系统 SHALL 直接查询数据库返回结果，零AI token消耗
3. WHEN 用户发送待办相关查询（包含"待办"、"任务"、"提醒"等关键词）THEN 系统 SHALL 直接查询数据库返回结果，零AI token消耗
4. WHEN 用户发送路线规划查询（包含"怎么去"、"路线"、"导航"等关键词）THEN 系统 SHALL 直接调用路线规划API返回结果，零AI token消耗
5. WHEN 用户发送简单问候（"你好"、"在吗"等）THEN 系统 SHALL 返回预定义的快捷回复，零AI token消耗
6. IF 意图识别置信度低于0.7 THEN 系统 SHALL 降级到AI处理，确保不丢失复杂请求

### Requirement 3: 对话历史压缩

**User Story:** 作为用户，我希望在长对话中AI仍然能保持上下文理解，同时不因为历史消息过多而变慢。

#### Acceptance Criteria

1. WHEN 对话消息数量超过10条 THEN 系统 SHALL 触发对话历史压缩机制
2. WHEN 执行压缩时 THEN 系统 SHALL 保留最近4条原始消息，将更早的消息压缩为摘要
3. WHEN 生成摘要时 THEN 系统 SHALL 使用AI生成不超过100字的对话主题摘要
4. IF 压缩后的token数超过原始消息的50% THEN 系统 SHALL 采用压缩后的消息列表发送给AI
5. WHEN 用户切换到新会话 THEN 系统 SHALL 重置压缩状态，从新的完整对话开始

### Requirement 4: 工具结果缓存

**User Story:** 作为用户，我希望在短时间内重复查询天气等信息时能够快速获得结果。

#### Acceptance Criteria

1. WHEN 调用天气工具获取某城市天气 THEN 系统 SHALL 将结果缓存到Redis，TTL为10分钟
2. WHEN 用户在10分钟内再次查询同一城市天气 THEN 系统 SHALL 直接返回缓存结果，不再调用天气API
3. WHEN 调用日历/待办查询时 THEN 系统 SHALL 将结果缓存到Redis，TTL为2分钟
4. WHEN 用户修改日历/待办数据（增删改）THEN 系统 SHALL 立即清除相关缓存
5. IF 缓存服务不可用 THEN 系统 SHALL 降级到直接调用工具，不影响功能可用性

### Requirement 5: 流式响应Token控制

**User Story:** 作为用户，我希望AI回复既详细又简洁，不要过长导致等待时间过长。

#### Acceptance Criteria

1. WHEN AI生成回复时 THEN 系统 SHALL 设置max_tokens上限为800，避免超长回复
2. WHEN 配置AI参数时 THEN 系统 SHALL 设置temperature为0.3，提高回复确定性
3. WHEN 系统提示词构建时 THEN 系统 SHALL 精简系统提示词，删除冗余说明
4. WHEN 用户问题需要复杂分析时 THEN 系统 SHALL 在提示词中明确要求"用200字以内回答"
5. WHEN 流式响应开始后 THEN 系统 SHALL 支持用户中断，避免浪费未生成完毕的token

### Requirement 6: 会话标题优化

**User Story:** 作为系统管理员，我希望会话标题生成既能满足用户需求，又不消耗过多AI资源。

#### Acceptance Criteria

1. WHEN 用户发送第一条消息时 THEN 系统 SHALL 异步生成会话标题，不阻塞主流程
2. WHEN 生成标题时 THEN 系统 SHALL 使用轻量级提示词，限定返回10-15字
3. IF 用户消息是简单问候或工具查询 THEN 系统 SHALL 使用预设模板生成标题，零AI调用
4. WHEN 标题生成完成 THEN 系统 SHALL 更新会话标题并通知前端刷新
5. IF 标题生成失败 THEN 系统 SHALL 使用默认标题"新对话"，不影响用户体验

### Requirement 7: 成本监控与告警

**User Story:** 作为系统管理员，我希望实时了解AI调用成本情况，及时发现异常。

#### Acceptance Criteria

1. WHEN 每次AI调用完成时 THEN 系统 SHALL 记录消耗的input/output token数到日志
2. WHEN 每小时统计时 THEN 系统 SHALL 计算并记录该小时的AI调用总成本
3. WHEN 单日成本超过预设阈值（如50元）THEN 系统 SHALL 发送告警通知管理员
4. WHEN 单用户单日调用超过100次 THEN 系统 SHALL 记录该用户为高频用户并告警
5. WHEN 管理员查询成本报表时 THEN 系统 SHALL 提供最近7天的调用量、命中率、成本趋势图

## Non-Functional Requirements

### Code Architecture and Modularity

- **Single Responsibility Principle**:
  - SemanticCacheService: 只负责语义缓存的存储和查询
  - IntentRouter: 只负责意图识别和请求路由
  - ConversationSummarizer: 只负责对话历史压缩
  - ToolCacheManager: 只负责工具结果的缓存管理
  - CostMonitor: 只负责成本统计和监控

- **Modular Design**:
  - 缓存模块与业务逻辑解耦，支持随时切换存储实现（Redis/本地/向量库）
  - 意图识别模块支持动态添加新的意图类别
  - 所有优化模块支持独立开关，通过配置控制启用/禁用

- **Dependency Management**:
  - ChatServiceImpl只依赖优化后的接口，不依赖具体实现
  - 新增模块不修改现有KimiClient和ToolRegistry代码

- **Clear Interfaces**:
  - 定义CacheStrategy接口，支持多种缓存策略
  - 定义IntentClassifier接口，支持规则/AI/混合分类器

### Performance

- 语义缓存查询延迟 < 50ms（P95）
- 意图识别延迟 < 10ms（纯规则匹配）
- 对话压缩操作延迟 < 100ms（异步执行）
- 工具结果缓存命中时响应延迟 < 20ms
- 整体AI调用减少率达到60%以上（目标）

### Security

- 语义缓存中的用户数据加密存储
- 缓存键值不包含敏感信息
- 不同用户的缓存数据隔离，防止跨用户数据泄露
- 成本监控接口需要管理员权限

### Reliability

- 缓存服务故障时自动降级，不影响核心功能
- 意图识别失败时自动降级到AI处理
- 对话压缩失败时保留原始消息列表
- 所有优化模块支持热开关，可实时禁用

### Usability

- 缓存命中率、成本节省等数据可视化展示
- 管理员可通过API手动清理缓存
- 支持按用户、按会话查看成本明细
- 异常告警信息清晰明确，包含处理建议
