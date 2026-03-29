# MrsHudson 后端 AI 架构设计文档

> 文档基于当前代码实现（截至 2026-03-29）

## 一、入口层

提供两条 API 路径：

| 路径 | Controller | 特点 |
|------|-----------|------|
| **流式（主要）** | [StreamChatController](../mrshudson-backend/src/main/java/com/mrshudson/controller/StreamChatController.java) `POST /api/chat/stream` | SSE 流式响应，前端/Android 使用 |
| **非流式** | [ChatController](../mrshudson-backend/src/main/java/com/mrshudson/controller/ChatController.java) `POST /api/chat/send` | 同步返回完整结果，带可选 TTS |

核心流程在 [StreamChatService.streamSendMessage()](../mrshudson-backend/src/main/java/com/mrshudson/service/StreamChatService.java:104) 方法。

---

## 二、完整调用链

```
用户消息 POST /api/chat/stream
  │
  ▼
StreamChatController.streamSendMessage()
  ├── JWT 认证 → 失败 → 401 错误响应
  └── 调用 StreamChatService.streamSendMessage()
        │
        ├─ ① 保存用户消息到 DB
        │     失败 → 记录日志，继续（非阻断）
        │
        ├─ ② 缓存检查 (CostOptimizer)
        │     命中 → cache_hit SSE + content_done + TTS + done，结束
        │     未命中 → 继续
        │
        ├─ ③ 意图路由 (IntentRouter — 三层混合架构)
        │     ├─ L1 规则层：关键词匹配 + 置信度评分
        │     │     命中 → content SSE + content_done + TTS + done，结束
        │     ├─ L2 轻量AI层：L1 未处理时升级
        │     │     命中 → content SSE + content_done + TTS + done，结束
        │     └─ L3 完整AI层：兜底（isHandled=false 时继续往下）
        │
        ├─ ④ 意图置信度评估 (IntentConfidenceEvaluator)
        │     仅在路由未处理时执行
        │     置信度不足 → clarification SSE + done，结束
        │     置信度充足 → 继续
        │
        ├─ ⑤ 构建消息列表 (buildMessageList)
        │     从 DB 取最近20条历史，截取最后10条 + system prompt + 当前消息
        │     注：buildMessageList 最多输出 ~12 条，不会触发 ContextManager 压缩阈值（15条）
        │     ContextManager.compress() 的真正入口是 buildOptimizedContext()，主流程暂未调用
        │
        ├─ ⑥ 上下文压缩检查 (ContextManager.needsCompression)
        │     消息数 > 15 → 早期消息 AI 摘要（≤200字），保留最近6条
        │     消息数 ≤ 15 → 跳过（当前主流程实际不会触发，见上）
        │
        ├─ ⑦ Token 计数开始 (TokenTrackerService)
        │
        ├─ ⑧ 质量优化检测 (QualityOptimizer.isComplex)
        │     仅做复杂度判断 + 日志，不阻断流程
        │     （ModelRouter 在 AI 客户端内部根据消息选择模型）
        │
        └─ ⑨ 执行流式 AI 调用 (executeStreamingAiCallWithTools)
              │
              ├── 选择 AI Provider (AIProvider.KIMI / MINIMAX)
              │     KimiClient 或 MiniMaxClient 的 streamChatCompletion()
              │     Provider 异常 → error SSE + done，结束
              │
              ├── 流式解析 AI 响应，按 chunk 类型分发：
              │     ├─ [TOOL_CALL] → 工具调用流程（见下）
              │     ├─ [THINKING]  → thinking SSE（不累加到最终内容）
              │     └─ 普通文本    → 累加到 aiFinalContent，content SSE
              │
              ├── 工具调用流程（检测到 [TOOL_CALL] 时，支持多轮递归）：
              │     1. tool_call SSE 事件
              │     2. ToolRegistry.executeTool() 执行工具
              │        工具不存在 → error SSE，跳过后续验证
              │     3. SelfCorrectingAgent.validate() 验证结果
              │        ├─ 验证失败 → correctAndRetry() 纠错重试
              │        └─ 纠错也失败 → error SSE（错误描述），toolResult 保留原始值继续注入
              │     4. 工具调用 + 结果注入 messages 历史
              │     5. tool_result SSE 事件（始终发送，result 字段为最终 toolResult 值）
              │     6. continueAiStream() 递归下一轮
              │        （无最大深度硬限制，依赖 AI 不再返回 TOOL_CALL 自然终止）
              │
              ├── 客户端断开（doOnCancel）：
              │     cancelSink.tryEmitEmpty() → 终止 AI 流
              │     content_done 已发出 → TTS 静默后台合成，结果写 DB
              │     content_done 未发出 → 保存已累积的部分内容，不启动 TTS
              │
              └── AI 流正常完成 → 收尾流程：
                    ├─ content_done SSE
                    ├─ 异步 TTS 语音合成（10s 超时保护）
                    │     超时 → audio_done {timeout:true}，后台继续合成写 DB
                    │     失败 → audio_done {error:"TTS_FAILED"}
                    │     成功 → audio_done {url:"https://..."}
                    ├─ token_usage SSE
                    ├─ done SSE
                    ├─ 异步：CostOptimizer.saveCache（缓存本次响应）
                    └─ 异步：ConversationMapper 更新时间 + 标题生成
```

---

## 三、核心优化层（optim 包）

这是架构的灵魂，分为 **7 个子系统**：

| 子系统 | 包路径 | 职责 |
|--------|--------|------|
| **意图路由** | `optim.intent` | 三层路由：规则 → 轻量AI → 完整AI，直接处理已知意图，避免不必要的 AI 调用 |
| **意图向量缓存** | `optim.intent`（规划中） | 意图识别结果持久化到向量数据库，三级缓存（L1内存/L2 Redis/L3向量搜索），含查询归一化和熔断降级 |
| **响应缓存** | `optim.cache` + `optim.cost` | 语义缓存（向量相似度匹配），ToolCacheManager，缓存失效监听 |
| **上下文压缩** | `optim.compress` + `optim.context` | 消息 > 15条触发压缩，早期消息摘要化，保留最近6条 |
| **自纠错** | `optim.correction` | 工具执行结果验证 → 纠错重试 → 错误模式学习 |
| **降级兜底** | `optim.fallback` | 工具失败 / AI 异常时的 FallbackDecision + FallbackHandler |
| **质量/成本** | `optim.quality` + `optim.monitor` + `optim.cost` | 质量模式三档（SPEED/BALANCED/QUALITY）、模型动态路由、Token 统计、成本监控 |

### 3.1 意图路由三层架构

三层路由实现在 `HybridIntentRouter`（`optim.intent.impl`，`hybrid` 模式），`IntentRouterImpl` 为纯规则路由器（`rule-only` 模式专用）。

`intentType` 的来源：L1 规则层通过 `IntentRecognizer.recognize()` 静态关键词匹配得出初步 `IntentType`，即使规则层未能完整处理（`isHandled=false`），也会将该 `intentType` 传递给 L2 作为上下文提示。

```java
// 第1层：规则提取（IntentRouterImpl / HybridIntentRouter.tryRuleLayer）
RouteResult ruleResult = tryRuleLayer(userId, query);
if (ruleResult != null && ruleResult.isHandled()) {
    return ruleResult;
}
// ruleResult 中已含初步 intentType，传递给下层
IntentType intentType = ruleResult != null ? ruleResult.getIntentType() : IntentType.GENERAL_CHAT;

// 第2层：轻量AI提取（intentType 作为上下文提示，降低 AI 调用成本）
RouteResult lightweightResult = tryLightweightAiLayer(userId, query, intentType);
if (lightweightResult != null && lightweightResult.isHandled()) {
    return lightweightResult;
}

// 第3层：完整AI调用（兜底）
return tryFullAiLayer(query, intentType);
```

### 3.2 意图向量缓存（规划中）

> 设计详情见 [intent-vector-cache-design.md](../.spec-workflow/specs/ai-agent-optimization-v2/intent-vector-cache-design.md)

在现有三层路由之前增加向量缓存层，将 AI 意图识别结果持久化，相同/相似意图直接复用，预计减少 40-60% 的意图识别 AI 调用。

```
用户输入
    │
    ▼
QueryNormalizer（当前仅实现时序归一化，其他策略规划中）
    示例："今天" → "2026-03-23"，"下周三" → "2026-04-01"，"明天上午" → "2026-03-30 上午"
    规划中：同义词归一化、口语化表达标准化
    │
    ▼
IntentCacheStore（三级查找）
    ├─ L1: ConcurrentHashMap 热点缓存（< 1ms，TTL 5分钟，最多500条）
    ├─ L2: Redis Hash 用户私有缓存（1-5ms，TTL 7天）
    └─ L3: Redis Vector 语义相似搜索（10-50ms，HNSW 索引）
    │
    ├─ 命中 → 直接返回，跳过 AI 识别
    └─ 未命中 → 进入现有三层路由 → 结果回写缓存
```

**模式切换**：

| 模式 | 说明 | 适用场景 |
|------|------|---------|
| `ai-first` | AI 识别并学习，结果写入缓存 | 冷启动、缓存 < 50 条 |
| `cache-first` | 缓存优先，未命中降级 AI | 缓存充足后的常态 |
| `rule-first` | 规则优先，AI 最后兜底 | 高频简单查询 |

**熔断保护**：向量搜索失败 10 次后自动熔断，1 分钟后半开探测，熔断期间跳过 L3 直接降级规则层。

### 3.3 质量模式与模型路由

`QualityOptimizer` 支持三档质量模式，可通过 `PUT /api/quality/mode` 运行时切换：

| 模式 | maxTokens | temperature | 适用场景 |
|------|-----------|-------------|---------|
| SPEED | 较低 | 较低 | 简单问答、闲聊 |
| BALANCED | 默认 | 默认 | 通用场景 |
| QUALITY | 提升至 2x | 提升 0.2 | 复杂分析、创意写作 |

`ModelRouter`（`optim.cost`）根据问题复杂度自动选择模型：
- 短消息（≤ 20字）/ 问候语 / 简单问答 → 小模型（`moonshot-v1-8k`）
- 长文本（> 100字）/ 含"为什么/分析/对比"等关键词 → 大模型（`moonshot-v1-32k`）

---

## 四、MCP 工具注册中心

[ToolRegistry](../mrshudson-backend/src/main/java/com/mrshudson/mcp/ToolRegistry.java) 管理所有工具，当前注册：

| 工具名 | 类 | 功能 | 必要入参 |
|--------|-----|------|---------|
| `get_weather` | [WeatherTool](../mrshudson-backend/src/main/java/com/mrshudson/mcp/weather/WeatherTool.java) | 天气查询 | `city`（城市名） |
| `create_calendar_event` | [CalendarTool](../mrshudson-backend/src/main/java/com/mrshudson/mcp/calendar/CalendarTool.java) | 创建日程 | `title`, `startTime` |
| `get_calendar_events` | CalendarTool | 查询日程列表 | `date`（可选） |
| `delete_calendar_event` | CalendarTool | 删除日程 | `eventId` |
| `create_todo` | [TodoTool](../mrshudson-backend/src/main/java/com/mrshudson/mcp/todo/TodoTool.java) | 创建待办 | `title` |
| `list_todos` | TodoTool | 查询待办列表 | 无 |
| `complete_todo` | TodoTool | 标记完成 | `todoId` |
| `delete_todo` | TodoTool | 删除待办 | `todoId` |
| `plan_route` | [RouteTool](../mrshudson-backend/src/main/java/com/mrshudson/mcp/route/RouteTool.java) | 路线规划 | `origin`, `destination` |

---

## 五、SSE 事件类型

[SseFormatter](../mrshudson-backend/src/main/java/com/mrshudson/util/SseFormatter.java) 定义了完整的 SSE 事件协议，详细规范见 [SSE_TTS_UNIFIED_SPEC.md](./core/SSE_TTS_UNIFIED_SPEC.md)：

| 事件 | 用途 | payload 示例 |
|------|------|-------------|
| `thinking` | AI 思考推理过程（增量，仅 reasoning 模型） | `{"type":"thinking","text":"...","conversationId":1,"messageId":2}` |
| `content` | 流式文本内容（增量） | `{"type":"content","text":"今天天气","conversationId":1,"messageId":2}` |
| `content_done` | AI 文本生成完毕，TTS 开始计时 | `{"type":"content_done","conversationId":1,"messageId":2}` |
| `tool_call` | 工具调用通知 | `{"type":"tool_call","toolCall":{"name":"get_weather","arguments":"{\"city\":\"北京\"}"},"conversationId":1}` |
| `tool_result` | 工具执行结果 | `{"type":"tool_result","toolResult":{"name":"get_weather","result":"北京晴，25°C"},"conversationId":1}` |
| `audio_done` | TTS 合成结束（10s 超时保护） | 成功：`{"type":"audio_done","url":"https://...","conversationId":1,"messageId":2}` |
| | | 超时：`{"type":"audio_done","timeout":true,...}` 后台继续合成写 DB |
| | | 失败：`{"type":"audio_done","error":"TTS_FAILED",...}` |
| | | 无音频：`{"type":"audio_done","noaudio":true,...}` |
| `token_usage` | Token 消耗统计 | `{"type":"token_usage","tokenUsage":{"inputTokens":128,"outputTokens":64,"duration":1230,"model":"MiniMax-M2.7"}}` |
| `cache_hit` | 响应缓存命中 | `{"type":"cache_hit","content":"..."}` |
| `clarification` | 意图澄清提示 | `{"type":"clarification","content":"请问您想查询哪个城市的天气？"}` |
| `error` | 错误信息 | `{"type":"error","message":"AI 服务暂时不可用"}` |
| `done` | 流结束 | `{"type":"done"}` |
| ~~`audio_url`~~ | ~~已废弃，由 `audio_done` 替代~~ | — |

---

## 六、设计特点总结

**成本优化**

1. **五层拦截**：响应缓存 → 意图向量缓存（规划中）→ 意图路由 → 置信度评估 → AI 调用，尽量在早期拦截减少 AI 调用成本
2. **模型动态路由**：`ModelRouter` 根据问题复杂度自动选择小模型/大模型，简单问题降成本
3. **质量模式三档可调**：`QualityOptimizer` 支持 SPEED / BALANCED / QUALITY，`PUT /api/quality/mode` 运行时切换

**稳定性保障**

4. **工具调用闭环**：AI 决定调用工具 → 执行 → 验证 → 自纠错（`correctAndRetry`）→ 兜底（`FallbackHandler` 返回友好提示）→ 结果回注 → AI 继续生成；多轮递归依赖 AI 不再返回 `[TOOL_CALL]` 自然终止
5. **客户端断开处理**：`cancelSink` + `doOnCancel` 编织取消信号；`content_done` 已发出则 TTS 静默后台合成写 DB，未发出则保存已有内容不启动 TTS

**扩展性**

6. **多 Provider 支持**：`AIClientFactory` 根据配置选择 Kimi / MiniMax；两个客户端统一输出 `[THINKING]` / `[TOOL_CALL]` 标记，上层无需感知具体提供商
7. **意图路由可配置**：`IntentRouterFactory` 支持 `rule-only` / `hybrid` / `ai-only` 三种模式，通过 `optim.intent.router.mode` 配置切换

---

## 七、关键类图

```
[Controller]
    │
    ▼
[StreamChatService]
    │
    ├──→ [CostOptimizer] 响应缓存检查/保存
    │       └── [SemanticCacheService] 向量相似度匹配
    │
    ├──→ [IntentRouter] 意图路由（由 IntentRouterFactory 按配置创建）
    │       ├── [IntentRouterImpl] rule-only 模式（纯规则）
    │       └── [HybridIntentRouter] hybrid 模式（三层：规则→轻量AI→完整AI）
    │               └── [AbstractIntentHandler] 处理器链
    │       （规划中：前置 IntentCacheStore 向量缓存层）
    │
    ├──→ [IntentConfidenceEvaluator] 置信度评估（路由未处理时执行）
    │
    ├──→ [ContextManager] 上下文压缩（> 15条触发）
    │
    ├──→ [TokenTrackerService] Token 统计
    │
    ├──→ [QualityOptimizer] 质量模式（SPEED/BALANCED/QUALITY）
    │       注：当前仅做复杂度检测+日志，模式切换通过 /api/quality/mode 触发
    │
    ├──→ [ModelRouter] 模型动态路由（小模型/大模型）
    │       注：ModelRouter 独立判断，不受 QualityOptimizer 模式影响
    │
    ├──→ [AIClientFactory]
    │       ├──→ [KimiClient] Kimi API（moonshot-v1-8k/32k，支持 kimi-k2-thinking）
    │       └──→ [MiniMaxClient] MiniMax API（M2.7，支持 reasoning_content）
    │
    ├──→ [ConversationMapper] 更新会话时间 / 异步标题生成
    │
    └──→ executeStreamingAiCallWithTools()
            │
            ├──→ [ToolRegistry] 工具注册/执行
            │
            ├──→ [SelfCorrectingAgent] 自纠错
            │       ├──→ [ToolResultValidator] 结果验证
            │       ├──→ [CorrectionRetryStrategy] 纠错重试
            │       ├──→ [FallbackHandler] 兜底（返回友好提示文案）
            │       └──→ [ErrorPatternLearner] 错误模式学习
            │
            └──→ [SseFormatter] SSE 事件格式化（Service 层输出纯 JSON，Controller 层统一加 data: 前缀）
```

---

## 八、管理后台 API

以下接口仅供管理员使用（`/api/admin/**`），不对普通用户开放。

**鉴权方式**：当前通过 JWT 解析 `userId` 判断管理员身份，具体逻辑见 `CacheManageController.isAdmin()`。生产环境建议改为数据库角色表或配置文件白名单。

| 路径 | 方法 | 说明 | 关键响应字段 |
|------|------|------|------------|
| `/api/admin/metrics/current` | GET | 当前优化效果指标快照 | `semanticCacheHitRate`, `toolCacheHitRate`, `intentLayerStats` |
| `/api/admin/metrics/trend` | GET | 指标趋势（`?days=7`） | `List<DailyMetrics>` |
| `/api/admin/metrics/comparison` | GET | 优化前后对比 | `before`, `after`, `theoreticalSavings` |
| `/api/admin/metrics/reset` | POST | 重置统计 | — |
| `/api/admin/cost/stats` | GET | 系统成本统计（`?startDate&endDate`） | `totalCost`, `callTypeStats` |
| `/api/admin/cost/daily` | GET | 每日成本趋势（`?days=30`，最多90天） | `dailyStats[]`, `averageDailyCost` |
| `/api/admin/cost/today` | GET | 今日成本快速查询 | `cost` |
| `/api/admin/cache/clear/semantic` | POST | 清除语义缓存 | — |
| `/api/admin/cache/clear/tool` | POST | 清除工具缓存 | — |
| `/api/admin/cache/clear/all` | POST | 清除全部缓存 | — |
| `/api/admin/cache/stats` | GET | 缓存统计 | `semanticCacheHitRate`, `toolCacheEntries`, `redisMemoryUsage` |
| `/api/quality/mode` | GET | 查询当前质量模式 | `mode`, `maxTokens`, `temperature`, `metrics` |
| `/api/quality/mode` | PUT | 切换质量模式 | body: `{"mode":"SPEED\|BALANCED\|QUALITY"}` |
| `/api/quality/metrics` | GET | 质量模式使用统计 | `speedModeCount`, `balancedModeCount`, `qualityUpgradeCount` |

---

## 九、待完善项

| 优先级 | 事项 | 前置依赖 |
|--------|------|---------|
| P0 | 意图向量缓存落地（`IntentCacheStore` + `QueryNormalizer` + `VectorBasedRecognizer`） | Redis Vector / Chroma 环境就绪；设计见 [intent-vector-cache-design.md](../.spec-workflow/specs/ai-agent-optimization-v2/intent-vector-cache-design.md) |
| P0 | 自纠错阈值调优（当前为硬编码，需基于生产日志数据调整） | 生产日志采集完整 |
| P1 | 接通 ContextManager 压缩路径（当前 `buildMessageList` 硬截10条，长对话上下文静默丢失；`ContextManager.compress()` 已实现但主流程未调用） | 无 |
| P1 | 工具调用多轮递归深度限制（当前无硬限制，依赖 AI 自然终止） | 无 |
| P1 | 缓存策略精细化（按工具/场景定制 TTL） | 意图向量缓存落地后 |
| P1 | 意图路由模式验证（`rule-only` / `hybrid` / `ai-only` 切换，`IntentRouterFactory` 已支持） | 无 |
| P2 | 前端接入管理后台数据接口（`/api/admin/metrics`、`/api/admin/cost`） | 管理 API 接口稳定 |
