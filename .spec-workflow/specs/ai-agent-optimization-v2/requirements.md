# AI Agent 二次优化 - 需求文档

## Introduction

在完成 AI 调用成本优化（ai-cost-optimization）后，本规格文档定义第二轮优化，主要聚焦于：
1. **兜底回答机制**：当用户提问超出当前工具能力范围时，允许 AI 直接基于自身能力回答
2. **流式 Token 统计**：流式输出响应后，显示本次对话的 token 消耗
3. **质量与性能平衡**：在保证响应质量的前提下，接受优化带来的额外 token 消耗
4. **意图理解增强**：正确理解用户意图，包括模糊意图时的澄清机制（解决问题1）
5. **自纠错机制**：AI回复有误时自动纠正，避免重复犯错（解决问题2）
6. **成本优化**：通过语义缓存和模型路由降低AI调用成本

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
4. WHEN 兜底回答生成时 THEN 系统 SHALL 明确告知用户提示文案："💡 以下回答基于 AI 自身能力，可能不包含最新信息，仅供参考"
5. WHEN 兜底回答生成时 THEN 系统 SHALL 记录标记 `fallback=true`，用于统计非工具类对话占比
6. WHEN 用户发送通用知识问答（包含"什么是"、"为什么"、"如何"等关键词） THEN 系统 SHALL 直接使用 LLM 回答，无需尝试工具调用

### Requirement 2: 流式 Token 统计显示

**User Story:** 作为用户，我想知道每次 AI 回复消耗了多少 token，以便了解 AI 资源使用情况。

#### Acceptance Criteria

1. WHEN AI 完成响应时 THEN 系统 SHALL 在响应末尾显示本次消耗的 token 数量
2. WHEN 流式输出完成时 THEN 系统 SHALL 计算并显示 inputTokens、outputTokens、totalTokens
3. WHEN Token 统计显示时 THEN 系统 SHALL 使用以下友好格式：
   ```
   --- 💡 本次对话消耗 ---
   📥 输入: 120 tokens
   📤 输出: 380 tokens
   📊 总计: 500 tokens
   💰 预估成本: ¥0.005
   ------------------------
   ```
4. WHEN Token 统计显示时 THEN 系统 SHALL 同时显示预估成本（按 Kimi API 价格：输入¥12/1M，输出¥12/1M）
5. WHEN 响应通过 SSE 流式传输时 THEN Token 统计 SHALL 作为最后一个数据块 `[DONE]` 之后发送
6. WHEN 用户请求 API 时 THEN Token 统计 SHALL 同时记录到响应体 `data.tokenUsage` 字段中

### Requirement 3: 质量优化模式

**User Story:** 作为用户，我更看重回答的质量和完整性，愿意为此支付更多 token 消耗。

#### Acceptance Criteria

1. WHEN 用户未明确指定响应风格时 THEN 系统 SHALL 默认使用"平衡模式"（质量与速度平衡）
   - **具体参数**: maxTokens=800, temperature=0.3, enableFullContext=true
2. WHEN 配置为"质量优先"模式时 THEN 系统 SHALL 允许更长的 maxTokens（如 2000）
3. WHEN 配置为"质量优先"模式时 THEN 系统 SHALL 使用更高的 temperature（0.7-0.9）以获得更有创意 的回答
4. WHEN 配置为"质量优先"模式时 THEN 系统 SHALL 启用更全面的上下文记忆
5. WHEN 系统检测到复杂问题时（消息长度>200字或多问号/包含"首先...然后"） THEN 系统 SHALL 自动提升响应质量等级
6. WHEN 质量优化导致 token 超预算时 THEN 系统 SHALL 记录 WARN 级别日志，但不阻断响应

### Requirement 4: 智能上下文管理

**User Story:** 作为用户，我希望 AI 在长对话中既能记住重要信息，又不会因为历史过长而变慢或出错。

#### Acceptance Criteria

1. WHEN 对话消息超过 15 条时 THEN 系统 SHALL 触发智能上下文压缩
2. WHEN 执行上下文压缩时 THEN 系统 SHALL 保留最近 6 条完整消息
3. WHEN 执行上下文压缩时 THEN 系统 SHALL 将更早消息压缩为不超过 200 字的摘要
4. WHEN 生成摘要时 THEN 系统 SHALL 识别并保留关键实体（人名、地点、时间）
5. WHEN 用户切换会话时 THEN 系统 SHALL 重置上下文，从新会话开始
6. WHEN 上下文压缩失败时 THEN 系统 SHALL 保留原始消息，降级为截断策略

### Requirement 5: 智能缓存与成本优化

**User Story:** 作为系统管理员，我希望相同问题能够快速响应，同时降低AI调用成本。

#### Acceptance Criteria

1. WHEN 用户发送完全相同的消息 THEN 系统 SHALL 直接返回缓存结果，零 AI 调用
2. WHEN 用户发送语义相似的问题（相似度 > 0.9） THEN 系统 SHALL 可配置是否返回缓存结果（默认关闭）
3. WHEN 缓存命中 THEN 系统 SHALL 在响应中标记 "（缓存命中）"
4. WHEN 用户发送简单问题（<20字或纯问候语） THEN 系统 SHALL 使用小模型处理
5. WHEN 缓存命中时 THEN Token 消耗计为 0
6. WHEN 工具结果缓存时 THEN 系统 SHALL 区分不同工具设置不同 TTL（如天气1小时、日历24小时）
7. WHEN 用户数据变更时 THEN 系统 SHALL 自动清除相关缓存
8. WHEN 缓存查询失败时 THEN 系统 SHALL 降级为直接调用 AI，不阻断响应

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

---

## 新增需求：意图理解增强

### Requirement 6: 意图理解增强

**User Story:** 作为用户，我希望当我输入模糊或有歧义时，AI能够主动询问澄清，而不是随意猜测我的意图。

#### Acceptance Criteria

1. WHEN 用户发送模糊消息 AND 存在多个可能的意图 THEN 系统 SHALL 返回候选意图供用户确认
2. WHEN 意图置信度 < 0.8 AND 有主要意图 THEN 系统 SHALL 提示用户确认
3. WHEN 工具调用缺少必要参数 THEN 系统 SHALL 主动请求补充参数
4. WHEN 意图识别成功 AND 置信度 >= 0.8 THEN 系统 SHALL 直接执行
5. WHEN 意图识别失败 THEN 系统 SHALL 走兜底机制

### Requirement 7: 自纠错机制

**User Story:** 作为用户，我希望AI能够意识到自己的错误并尝试纠正，而不是坚持错误答案。

#### Acceptance Criteria

1. WHEN 工具返回结果 AND 结果格式异常 THEN 系统 SHALL 标记为验证失败
2. WHEN 工具返回结果 AND 结果包含错误标记（如"无法"、"失败"） THEN 系统 SHALL 触发纠错流程
3. WHEN 验证失败 AND 重试次数 < 2 THEN 系统 SHALL 使用纠错提示词重新请求 LLM
4. WHEN 验证失败 AND 重试次数 >= 2 THEN 系统 SHALL 放弃纠错，返回原结果并记录 ERROR 日志
5. WHEN 发生错误 THEN 系统 SHALL 记录错误模式到 Redis 用于后续规避

### Requirement 8: 意图识别向量缓存优化

**User Story:** 作为系统管理员，我希望意图识别结果能够被缓存复用，以降低 AI 调用成本；同时希望时效性表达（如"今天"）能够被正确处理。

#### Acceptance Criteria

1. WHEN 用户发送消息 AND 意图识别完成 THEN 系统 SHALL 将识别结果（意图类型+参数+向量）存储到缓存
2. WHEN 用户发送消息 AND 缓存命中 THEN 系统 SHALL 直接返回缓存结果，无需调用 AI
3. WHEN 缓存命中时 THEN 系统 SHALL 在 L1（内存）/ L2（Redis）/ L3（向量）各层记录统计
4. WHEN 用户输入包含时效性表达（今天/明天/后天/周一~周日） THEN 系统 SHALL 将其归一化为具体日期后再进行向量存储
5. WHEN 缓存数据不足 50 条时 THEN 系统 SHALL 默认使用 AI-FIRST 模式
6. WHEN 缓存数据 >= 50 条时 THEN 系统 SHALL 支持切换到 CACHE-FIRST 模式
7. WHEN 向量搜索相似度 >= 0.92 THEN 系统 SHALL 认为命中缓存
8. WHEN 向量搜索相似度 < 0.92 THEN 系统 SHALL 降级到 AI 识别
9. WHEN Embedding 服务不可用时 THEN 系统 SHALL 触发熔断，降级到规则匹配
10. WHEN 用户数据发生变更时 THEN 系统 SHALL 自动清除该用户的私有缓存

#### 技术约束

1. 向量存储使用 Redis Search + HNSW 索引（Redis 7.x 内置）
2. Embedding 服务配置化，支持 MiniMax / Kimi / OpenAI / 本地模型
3. 缓存 Key 格式：`intent_cache:user:{userId}:{intentType}:{paramFingerprint}`
4. 向量维度可配置，默认 1024 维（text-embedding-3）
5. 缓存 TTL：L1 5分钟，L2 7天，L3 与 L2 同步过期

### Requirement 9: 意图识别模式切换

**User Story:** 作为系统管理员，我希望能够灵活切换意图识别模式（AI优先/缓存优先/规则优先），以适应不同场景需求。

#### Acceptance Criteria

1. WHEN 系统配置 `intent-recognition.mode=ai-first` THEN AI 优先识别，并将结果学习到缓存
2. WHEN 系统配置 `intent-recognition.mode=cache-first` THEN 缓存优先，AI 作为降级
3. WHEN 系统配置 `intent-recognition.mode=rule-first` THEN 规则优先，AI 最后兜底
4. WHEN 管理员切换模式时 THEN 系统 SHALL 记录切换日志
5. WHEN 模式为 AI-FIRST 时 THEN 系统 SHALL 记录缓存学习统计
