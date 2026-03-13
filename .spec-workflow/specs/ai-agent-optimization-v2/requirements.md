# AI Agent 二次优化 - 需求文档

## Introduction

在完成 AI 调用成本优化（ai-cost-optimization）后，本规格文档定义第二轮优化，主要聚焦于：
1. **兜底回答机制**：当用户提问超出当前工具能力范围时，允许 AI 直接基于自身能力回答
2. **流式 Token 统计**：流式输出响应后，显示本次对话的 token 消耗
3. **质量与性能平衡**：在保证响应质量的前提下，接受优化带来的额外 token 消耗

## Alignment with Product Vision

MrsHudson 定位为"贴心的私人管家助手"，核心优势在于：
- **智能响应**：能够回答用户各种问题，不局限于固定工具
- **透明消费**：用户清楚了解 AI 资源消耗
- **持续进化**：系统能够不断学习和优化

本轮优化完全符合产品愿景，让 AI 助手更加智能和透明。

## Requirements

### Requirement 1: 兜底回答机制

**User Story:** 作为用户，我希望即使我的问题超出了天气、日历、待办等工具范围，AI 也能基于自身知识给我一个合理的回答，而不是告诉我"无法处理"。

#### Acceptance Criteria

1. WHEN 用户发送消息 AND 意图路由识别为工具查询 BUT 工具执行失败 THEN 系统 SHALL 触发兜底机制，使用 LLM 直接回答
2. WHEN 用户发送消息 AND 消息内容无法被任何工具处理 THEN 系统 SHALL 跳过工具调用，直接使用 LLM 回答
3. WHEN 兜底机制触发时 THEN 系统 SHALL 使用完整的系统提示词，包含所有工具描述（作为知识参考）
4. WHEN 兜底回答生成时 THEN 系统 SHALL 明确告知用户"以下回答基于 AI 自身能力，可能不包含最新信息"
5. WHEN 兜底回答生成时 THEN 系统 SHALL 记录标记，用于统计非工具类对话占比
6. WHEN 用户发送通用知识问答 THEN 系统 SHALL 直接使用 LLM 回答，无需尝试工具调用

### Requirement 2: 流式 Token 统计显示

**User Story:** 作为用户，我想知道每次 AI 回复消耗了多少 token，以便了解 AI 资源使用情况。

#### Acceptance Criteria

1. WHEN AI 完成响应时 THEN 系统 SHALL 在响应末尾显示本次消耗的 token 数量
2. WHEN 流式输出完成时 THEN 系统 SHALL 计算并显示 input tokens、output tokens、总 tokens
3. WHEN Token 统计显示时 THEN 系统 SHALL 使用友好格式，如 "本次对话消耗: 输入 120 tokens / 输出 380 tokens / 总计 500 tokens"
4. WHEN Token 统计显示时 THEN 系统 SHALL 同时显示预估成本（如 ¥0.05）
5. WHEN 响应通过 SSE 流式传输时 THEN Token 统计 SHALL 作为最后一个数据块发送
6. WHEN 用户请求 API 时 THEN Token 统计 SHALL 同时记录到响应头或响应体中

### Requirement 3: 质量优化模式

**User Story:** 作为用户，我更看重回答的质量和完整性，愿意为此支付更多 token 消耗。

#### Acceptance Criteria

1. WHEN 用户未明确指定响应风格时 THEN 系统 SHALL 默认使用"平衡模式"（质量与速度平衡）
2. WHEN 配置为"质量优先"模式时 THEN 系统 SHALL 允许更长的 max_tokens（如 2000）
3. WHEN 配置为"质量优先"模式时 THEN 系统 SHALL 使用更高的 temperature（0.7-0.9）以获得更有创意 的回答
4. WHEN 配置为"质量优先"模式时 THEN 系统 SHALL 启用更全面的上下文记忆
5. WHEN 系统检测到复杂问题时 THEN 系统 SHALL 自动提升响应质量等级
6. WHEN 质量优化导致 token 超预算时 THEN 系统 SHALL 记录警告日志，但不阻断响应

### Requirement 4: 智能上下文管理

**User Story:** 作为用户，我希望 AI 在长对话中既能记住重要信息，又不会因为历史过长而变慢或出错。

#### Acceptance Criteria

1. WHEN 对话消息超过 15 条时 THEN 系统 SHALL 触发智能上下文压缩
2. WHEN 执行上下文压缩时 THEN 系统 SHALL 保留最近 6 条完整消息
3. WHEN 执行上下文压缩时 THEN 系统 SHALL 将更早消息压缩为不超过 200 字的摘要
4. WHEN 生成摘要时 THEN 系统 SHALL 识别并保留关键实体（人名、地点、时间）
5. WHEN 用户切换会话时 THEN 系统 SHALL 重置上下文，从新会话开始
6. WHEN 上下文压缩失败时 THEN 系统 SHALL 保留原始消息，降级为截断策略

### Requirement 5: 响应缓存增强

**User Story:** 作为系统管理员，我希望相同问题能够快速响应，同时支持更智能的缓存策略。

#### Acceptance Criteria

1. WHEN 用户发送完全相同的消息时 THEN 系统 SHALL 直接返回缓存结果，零 AI 调用
2. WHEN 用户发送语义相似的问题（相似度 > 0.9）时 THEN 系统 SHALL 可配置是否返回缓存结果
3. WHEN 缓存命中时 THEN 系统 SHALL 在响应中标记 "（缓存命中）"
4. WHEN 工具结果缓存时 THEN 系统 SHALL 区分不同工具设置不同 TTL
5. WHEN 用户数据变更时 THEN 系统 SHALL 自动清除相关缓存

## Non-Functional Requirements

### Code Architecture and Modularity

- **Single Responsibility Principle**:
  - FallbackHandler: 只负责兜底回答逻辑
  - TokenTracker: 只负责 token 统计和计算
  - ContextManager: 只负责上下文管理和压缩
  - QualityOptimizer: 只负责质量参数调整

- **Modular Design**:
  - 兜底机制作为独立处理器，不修改现有工具调用逻辑
  - Token 统计作为拦截器，对业务代码透明
  - 所有优化支持独立开关

- **Clear Interfaces**:
  - 定义 FallbackStrategy 接口，支持多种兜底策略
  - 定义 TokenCollector 接口，支持多种统计方式

### Performance

- 兜底回答响应延迟 < 3s（P95）
- Token 统计计算延迟 < 10ms
- 上下文压缩操作延迟 < 500ms（异步执行）
- 缓存查询延迟 < 20ms

### Security

- 兜底回答不泄露用户敏感数据
- Token 统计数据不包含敏感信息
- 缓存数据按用户隔离

### Reliability

- 兜底机制失败时降级为直接返回"抱歉，我暂时无法回答"
- Token 统计失败时不阻断响应返回
- 上下文压缩失败时保留原始上下文

### Usability

- Token 消耗显示格式友好直观
- 管理员可配置质量模式和阈值
- 支持按用户查看 token 消耗统计
