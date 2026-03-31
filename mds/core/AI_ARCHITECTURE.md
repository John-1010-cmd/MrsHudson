# MrsHudson 后端 AI 架构设计文档

## 文档信息

| 属性 | 内容 |
|------|------|
| **文档标题** | MrsHudson 后端 AI 架构设计文档 |
| **版本** | 3.3 |
| **状态** | 正式版 |
| **最后更新** | 2026-03-31 |

---

## 修订历史

| 版本 | 日期 | 修订内容 |
|------|------|----------|
| 1.0 | 2026-03-25 | 初始版本，基于代码实现梳理基础架构 |
| 2.0 | 2026-03-29 | 增加优化层详细设计、SSE 协议规范 |
| 3.0 | 2026-03-29 | 完善调用链、缓存体系、待完善项，形成正式文档 |
| 3.1 | 2026-03-31 | 审阅修正：调用链步骤 ⑤⑦ 描述对齐代码、质量模式数值补充、API 鉴权分层、ADR-001 技术约束、缓存 Key/TTL 描述修正 |
| 3.2 | 2026-03-31 | 二轮审阅：audio_done 四种状态补充、cache_hit/clarification 字段口径明确、L3 描述修正、数据模型图去重、IntentConfidenceEvaluator 归属明确化、附录 B 链接修正、QueryNormalizer 日期占位符化 |
| 3.3 | 2026-03-31 | 三轮审阅：组件依赖关系图补充 IntentConfidenceEvaluator、修订历史补充 3.3 版本条目 |

---

## 目录

1. [执行摘要](#一执行摘要)
2. [架构概述](#二架构概述)
3. [架构原则与约束](#三架构原则与约束)
4. [逻辑架构视图](#四逻辑架构视图)
5. [进程架构视图](#五进程架构视图)
6. [数据架构视图](#六数据架构视图)
7. [核心优化层设计](#七核心优化层设计)
8. [MCP 工具架构](#八mcp-工具架构)
9. [SSE 通信协议](#九sse-通信协议)
10. [管理后台 API](#十管理后台-api)
11. [非功能性需求](#十一非功能性需求)
12. [风险与缓解措施](#十二风险与缓解措施)
13. [附录](#十三附录)

---

## 一、执行摘要

### 1.1 文档目的

本文档旨在全面描述 MrsHudson 后端 AI 子系统的软件架构，为开发团队、架构师和运维人员提供系统设计的完整视图。

### 1.2 系统范围

本文档覆盖以下子系统：

| 子系统 | 范围说明 | 状态 |
|--------|----------|------|
| 流式对话服务 | SSE 流式响应、TTS 语音合成 | 已上线 |
| 意图路由系统 | 三层混合路由架构 | 已上线 |
| 响应缓存系统 | 语义缓存、工具缓存 | 已上线 |
| 上下文管理系统 | 消息压缩、历史管理 | 部分实现（压缩路径尚未接入主调用链） |
| 自纠错系统 | 工具结果验证与纠错 | 已上线 |
| MCP 工具中心 | 天气、日程、待办、路线规划 | 已上线 |
| 多 Provider 支持 | Kimi / MiniMax 双引擎 | 已上线 |
| 意图向量缓存 | 三级缓存架构 | 规划中 |

### 1.3 关键架构决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 通信协议 | SSE (Server-Sent Events) | 支持流式响应、前端兼容性好、实现简单 |
| 意图路由 | 三层混合架构 (规则+轻量AI+完整AI) | 成本与质量的平衡 |
| AI Provider | Kimi + MiniMax 双引擎 | 降低单点依赖、模型互补 |
| 工具调用 | MCP 协议 | 标准化工具调用模式、易于扩展 |
| 缓存策略 | 语义相似度匹配 | 提高缓存命中率、支持模糊匹配 |

---

## 二、架构概述

### 2.1 系统上下文

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              MrsHudson AI 系统                           │
│                                                                          │
│   ┌──────────┐    ┌──────────┐    ┌─────────────────────────────────┐   │
│   │   Web    │    │ Android  │    │         后端服务层               │   │
│   │  Frontend│    │   App    │    │                                  │   │
│   └────┬─────┘    └────┬─────┘    │  ┌─────────────────────────┐    │   │
│        │               │          │  │    StreamChatController  │   │   │
│        │  POST /api/   │          │  └───────────┬─────────────┘   │   │
│        │  chat/stream  │          │              │                 │   │
│        │  (SSE)        │          │  ┌───────────▼─────────────┐    │   │
│        └───────────────┘          │  │    StreamChatService     │   │   │
│                                   │  └───────────┬─────────────┘   │   │
│                                   │              │                 │   │
│                                   │  ┌───────────▼─────────────┐    │   │
│                                   │  │    AI 优化层 (optim)      │   │   │
│                                   │  │  - IntentRouter           │   │   │
│                                   │  │  - CostOptimizer          │   │   │
│                                   │  │  - ContextManager         │   │   │
│                                   │  └───────────┬─────────────┘   │   │
│                                   │              │                 │   │
│                                   │  ┌───────────▼─────────────┐    │   │
│                                   │  │    AI Provider 层         │   │   │
│                                   │  │  - KimiClient             │   │   │
│                                   │  │  - MiniMaxClient          │   │   │
│                                   │  └───────────┬─────────────┘   │   │
│                                   └──────────────┼─────────────────┘   │
│                                                  │                      │
│   ┌──────────┐    ┌──────────┐    ┌───────────▼─────────────┐         │
│   │  MySQL   │    │  Redis   │    │    外部 AI 服务          │         │
│   │  (主存)   │    │  (缓存)   │    │  - Kimi API             │         │
│   └──────────┘    └──────────┘    │  - MiniMax API          │         │
│                                   └─────────────────────────┘         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.2 架构目标

| 目标 | 优先级 | 说明 |
|------|--------|------|
| **成本优化** | P0 | 通过多层拦截和缓存减少 AI 调用成本 |
| **响应速度** | P0 | 流式响应、缓存命中时快速返回 |
| **系统稳定性** | P0 | 自纠错、降级兜底、熔断保护 |
| **可扩展性** | P1 | 易于添加新工具、新 Provider |
| **可观测性** | P1 | 完善的监控指标和管理后台 |

### 2.3 核心指标

| 指标 | 目标值 | 当前状态 |
|------|--------|----------|
| 语义缓存命中率 | > 30% | 待观察 |
| 意图路由拦截率 | > 40% | 待观察 |
| 平均响应时间 (P95) | < 2s | 待观察 |
| AI 调用成本降低 | > 50% | 待观察 |
| 系统可用性 | 99.9% | 待观察 |

---

## 三、架构原则与约束

### 3.1 设计原则

| 原则 | 说明 | 应用 |
|------|------|------|
| **分层隔离** | 每层只依赖下层，禁止跨层调用 | Controller → Service → Optim → Client |
| **防御式编程** | 假设外部系统不可靠 | AI 异常降级、工具失败兜底 |
| **缓存优先** | 优先从缓存获取，减少 AI 调用 | 语义缓存、意图缓存 |
| **渐进增强** | 基础功能保底，高级功能增强 | 规则路由 → AI 路由 |
| **响应式编程** | 流式处理、背压控制 | Reactor SSE 流 |

### 3.2 技术约束

| 约束 | 影响 | 缓解措施 |
|------|------|----------|
| AI 调用成本敏感 | 需要多层拦截 | 五层拦截架构（见下文） |
| AI 响应时间波动 | 需要流式输出 | SSE 实时推送 |
| 上下文长度限制 | 需要压缩策略 | ContextManager 自动压缩 |
| TTS 合成耗时 | 需要异步处理 | 后台异步合成 |

**五层拦截架构**

```
用户请求
  │
  ▼
┌─────────────────────────────────────────────────┐
│ L1 响应缓存 (SemanticCacheService)               │  ← 命中直接返回，跳过全部后续
│   语义相似度匹配                                 │
└──────────────┬──────────────────────────────────┘
               │ 未命中
  ▼
┌─────────────────────────────────────────────────┐
│ L2 意图向量缓存 (IntentCacheStore)    [规划中]   │  ← 命中直接返回意图结果
│   三级缓存：内存 → Redis → 向量搜索              │
└──────────────┬──────────────────────────────────┘
               │ 未命中
  ▼
┌─────────────────────────────────────────────────┐
│ L3 意图路由 (IntentRouter)                       │  ← 规则/轻量AI 拦截简单查询
│   三层混合：规则 → 轻量AI → 完整AI               │
└──────────────┬──────────────────────────────────┘
               │ 未拦截
  ▼
┌─────────────────────────────────────────────────┐
│ L4 置信度评估 (IntentConfidenceEvaluator)        │  ← 置信度不足返回澄清提示
│   低置信度 → clarification，不调用 AI             │
└──────────────┬──────────────────────────────────┘
               │ 置信度充足
  ▼
┌─────────────────────────────────────────────────┐
│ L5 AI 调用 (AIClientFactory)                     │  ← 兜底：前四层均未拦截时执行
│   Kimi / MiniMax 双引擎                          │
└─────────────────────────────────────────────────┘
```

> **当前状态**：L2（意图向量缓存）尚未实现，实际生效的为 L1 + L3 + L4 + L5 四层。

### 3.3 关键限制

1. **上下文压缩阈值**: 15 条消息触发压缩，保留最近 6 条
2. **TTS 超时**: 10 秒超时保护，超时后后台继续合成
3. **缓存 TTL**: 语义缓存由 `VectorStore` 过期策略管理，工具缓存按工具类型定制（通过 `OptimProperties` 配置）
4. **递归深度**: 工具调用当前无硬限制（规划中）

---

## 四、逻辑架构视图

### 4.1 分层架构

```
┌────────────────────────────────────────────────────────────────┐
│                         表现层 (Presentation)                   │
│  ┌──────────────────┐              ┌──────────────────┐       │
│  │ StreamChatCtrl   │              │ ChatController   │       │
│  │ POST /stream     │              │ POST /send       │       │
│  │ SSE 流式响应      │              │ 同步响应          │       │
│  └────────┬─────────┘              └────────┬─────────┘       │
└───────────┼─────────────────────────────────┼─────────────────┘
            │                                 │
┌───────────▼─────────────────────────────────▼─────────────────┐
│                       应用层 (Application)                    │
│                    ┌──────────────────┐                      │
│                    │ StreamChatService │                      │
│                    │ - 协调优化层       │                      │
│                    │ - 管理 AI 调用流   │                      │
│                    └────────┬─────────┘                      │
└─────────────────────────────┼─────────────────────────────────┘
                              │
┌─────────────────────────────▼─────────────────────────────────┐
│                      优化层 (Optimization)                    │
│  ┌──────────────┬──────────────┬──────────────┬──────────────┐│
│  │ IntentRouter │CostOptimizer │ContextManager│QualityOptim. ││
│  │ 意图路由      │ 响应缓存      │ 上下文压缩    │ 质量优化      ││
│  ├──────────────┼──────────────┼──────────────┼──────────────┤│
│  │SemanticCache │ ToolCache    │TokenTracker  │IntentEval.   ││
│  │ 语义缓存      │ 工具缓存      │ Token 统计   │ 置信度评估    ││
│  └──────────────┴──────────────┴──────────────┴──────────────┘│
└─────────────────────────────┬─────────────────────────────────┘
                              │
┌─────────────────────────────▼─────────────────────────────────┐
│                        领域层 (Domain)                        │
│  ┌──────────────┬──────────────┬──────────────┬──────────────┐│
│  │ ToolRegistry │SelfCorrecting│FallbackHandler│ModelRouter  ││
│  │ 工具注册中心  │    Agent     │   降级兜底    │ 模型路由     ││
│  └──────────────┴──────────────┴──────────────┴──────────────┘│
└─────────────────────────────┬─────────────────────────────────┘
                              │
┌─────────────────────────────▼─────────────────────────────────┐
│                       基础设施层 (Infrastructure)               │
│  ┌──────────────┬──────────────┬──────────────┬──────────────┐│
│  │  KimiClient  │MiniMaxClient │  MySQL       │   Redis      ││
│  │  Kimi API    │ MiniMax API  │  主存储       │   缓存       ││
│  └──────────────┴──────────────┴──────────────┴──────────────┘│
└────────────────────────────────────────────────────────────────┘
```

### 4.2 核心组件职责

| 组件 | 职责 | 关键接口/类 |
|------|------|-------------|
| **StreamChatController** | 处理 SSE 流式请求，管理响应输出流 | `streamSendMessage()` |
| **StreamChatService** | 编排完整对话流程，协调各优化层 | `streamSendMessage()` |
| **IntentRouter** | 三层意图路由，拦截简单请求 | `route()` |
| **CostOptimizer** | 语义缓存检查与保存 | `checkCache()`, `saveCache()` |
| **ContextManager** | 上下文压缩与历史管理 | `compress()`, `needsCompression()` |
| **ToolRegistry** | 工具注册、发现、执行 | `executeTool()`, `getTool()` |
| **SelfCorrectingAgent** | 工具结果验证与纠错 | `validate()`, `correctAndRetry()` |
| **AIClientFactory** | AI Provider 工厂 | `getClient()` |
| **SseFormatter** | SSE 事件格式化 | `formatEvent()` |
| **IntentConfidenceEvaluator** | 意图置信度评估，低置信度返回澄清 | `evaluate()` |
| **ModelRouter** | 根据消息复杂度选择模型 | `routeModel()` |
| **TokenTrackerService** | Token 消耗统计 | `startTracking()`, `getUsage()` |

### 4.3 组件依赖关系

```
StreamChatController
    ↓ 依赖
StreamChatService
    ↓ 依赖
    ├─→ IntentRouter ──→ IntentHandler (多个)
    ├─→ CostOptimizer ──→ SemanticCacheService
    ├─→ ContextManager
    ├─→ TokenTrackerService
    ├─→ QualityOptimizer
    ├─→ ModelRouter
    ├─→ IntentConfidenceEvaluator
    ├─→ AIClientFactory ──→ KimiClient / MiniMaxClient
    ├─→ ToolRegistry ──→ BaseTool (多个)
    ├─→ SelfCorrectingAgent ──→ ToolResultValidator / FallbackHandler
    └─→ SseFormatter
```

---

## 五、进程架构视图

### 5.1 运行时调用链

```
用户消息 POST /api/chat/stream
  │
  ▼
[Controller Layer]
StreamChatController.streamSendMessage()
  ├── JWT 认证 → 失败 → 401 错误响应
  └── 调用 StreamChatService.streamSendMessage()
        │
        ├─ ① 保存用户消息到 DB
        │     失败 → 记录日志，继续（非阻断）
        │
        ├─ ② 缓存检查 (CostOptimizer)
        │     命中 → cache_hit SSE + content_done + TTS(audio_done) + done，结束
        │     注：缓存命中无实际 AI 调用，不发 token_usage
        │     未命中 → 继续
        │
        ├─ ③ 意图路由 (IntentRouter — 三层混合架构)
        │     ├─ L1 规则层：关键词匹配 + 置信度评分
        │     │     命中 → content SSE + content_done + TTS + done，结束
        │     ├─ L2 轻量AI层：L1 未处理时升级
        │     │     命中 → content SSE + content_done + TTS + done，结束
        │     └─ L3 完整AI层：返回路由结果（isHandled=true），继续步骤 ④
        │
        ├─ ④ 意图置信度评估 (IntentConfidenceEvaluator)
        │     置信度不足 → clarification SSE + done，结束
        │     置信度充足 → 继续
        │
        ├─ ⑤ 构建消息列表 (ContextManager.buildOptimizedContext)
        │     从 DB 取历史消息，判断是否需要压缩：
        │       消息数 < 15 → 直接使用完整历史 + system prompt + 当前消息
        │       消息数 ≥ 15 → 保留最近 6 条原始消息，其余压缩为摘要
        │                      → 摘要 + 最近 6 条 + system prompt + 当前消息
        │     压缩失败 → 降级：截断保留最近 N 条，记录 WARN 日志
        │     ⚠ ContextManager 压缩路径接入中，当前降级为截取最后 N 条
        │
        ├─ ⑥ Token 计数开始 (TokenTrackerService)
        │
        ├─ ⑦ 质量优化检测 (QualityOptimizer)
        │     当前：检测复杂度并记录日志，参数尚未传入 AI 调用（见已知问题）
        │     预期：根据质量模式（SPEED/BALANCED/QUALITY）调整 maxTokens
        │            和 temperature，传递给步骤 ⑧ 的 AI 调用请求
        │     ⚠ QualityOptimizer 与 ModelRouter 当前无联动，见附录 C P1 待办
        │
        └─ ⑧ 执行流式 AI 调用 (executeStreamingAiCallWithTools)
              │
              ├── 选择 AI Provider (AIProvider.KIMI / MINIMAX)
              │     Provider 异常 → error SSE + done，结束
              │
              ├── 流式解析 AI 响应，按 chunk 类型分发：
              │     ├─ [TOOL_CALL] → 工具调用流程
              │     ├─ [THINKING]  → thinking SSE
              │     └─ 普通文本    → content SSE
              │
              ├── 工具调用流程（支持多轮递归）：
              │     1. tool_call SSE 事件
              │     2. ToolRegistry.executeTool() 执行工具
              │     3. SelfCorrectingAgent.validate() 验证结果
              │     4. 工具结果注入 messages 历史
              │     5. tool_result SSE 事件
              │     6. continueAiStream() 递归下一轮
              │
              ├── 客户端断开（doOnCancel）：
              │     cancelSink.tryEmitEmpty() → 终止 AI 流
              │
              └── AI 流正常完成 → 收尾流程：
                    ├─ content_done SSE
                    ├─ 异步 TTS 语音合成（10s 超时保护）
                    ├─ token_usage SSE
                    ├─ done SSE
                    ├─ 异步：CostOptimizer.saveCache
                    └─ 异步：ConversationMapper 更新时间 + 标题生成
```

### 5.2 并发与线程模型

| 组件 | 线程模型 | 说明 |
|------|----------|------|
| **Controller** | Tomcat NIO 线程 | 每个请求独立线程 |
| **Service** | Reactor 异步流 | `Flux<SseEvent>` 流式处理 |
| **TTS 合成** | 异步线程池 | `@Async` 或 `CompletableFuture` |
| **缓存保存** | 异步线程池 | 不阻塞主响应流 |
| **标题生成** | 异步线程池 | 后台异步执行 |

### 5.3 取消与背压

| 场景 | 处理机制 | 实现 |
|------|----------|------|
| 客户端断开 | 终止 AI 流，保存已生成内容 | `doOnCancel()` |
| AI 流超时 | 降级返回友好提示 | `timeout()` |
| TTS 超时 | 10s 超时，后台继续合成 | `CompletableFuture` |
| 背压控制 | Reactor 自动背压 | `Flux` 流控制 |

---

## 六、数据架构视图

### 6.1 数据模型

```
┌─────────────────────────────────────────────────────────────────┐
│                         实体关系图                               │
│                                                                  │
│   ┌──────────┐         ┌──────────────────┐                     │
│   │   User   │1       *│   Conversation   │                     │
│   ├──────────┤─────────┼──────────────────┤                     │
│   │ id (PK)  │         │ id (PK)          │                     │
│   │ username │         │ user_id (FK)     │                     │
│   │ password │         │ title            │                     │
│   │ ...      │         │ provider         │                     │
│   └──────────┘         │ last_message_at  │                     │
│                        └────────┬─────────┘                     │
│                                 │                               │
│                                 │ 1                             │
│                                 │                               │
│                                 ▼ *                             │
│                        ┌──────────────────┐                     │
│                        │   ChatMessage    │                     │
│                        ├──────────────────┤                     │
│                        │ id (PK)          │                     │
│                        │ user_id (FK)     │                     │
│                        │ conversation_id  │                     │
│                        │ role             │                     │
│                        │ content          │                     │
│                        │ function_call    │                     │
│                        │ audio_url        │                     │
│                        │ created_at       │                     │
│                        └──────────────────┘                     │
│                                                                  │
│   ┌──────────────┐    ┌──────────────┐                        │
│   │CalendarEvent │    │   TodoItem   │                        │
│   │   (日程表)    │    │   (待办表)   │                        │
│   └──────────────┘    └──────────────┘                        │
└─────────────────────────────────────────────────────────────────┘
```

### 6.2 数据库表结构

**chat_message（消息表）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 消息ID |
| user_id | BIGINT FK | 用户ID |
| conversation_id | BIGINT FK | 会话ID |
| role | VARCHAR(20) | 角色：user/assistant/system/tool |
| content | TEXT | 消息内容 |
| function_call | JSON | 函数调用信息（可选）|
| audio_url | VARCHAR(500) | TTS 音频URL（可选）|
| created_at | TIMESTAMP | 创建时间 |

**conversation（会话表）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 会话ID |
| user_id | BIGINT FK | 用户ID |
| title | VARCHAR(200) | 会话标题 |
| provider | VARCHAR(50) | AI Provider |
| last_message_at | TIMESTAMP | 最后消息时间 |

### 6.3 缓存数据结构

**语义响应缓存（VectorStore）**

```
按 userId 隔离，通过向量相似度匹配查询：
  VectorStore.store(userId, query, response, embedding)
  VectorStore.search(userId, queryEmbedding, similarityThreshold)
Value: {
  "query": "原始查询",
  "response": "AI 响应内容",
  "embedding": [向量表示]
}
TTL: 由 VectorStore 实现的过期策略管理（通过 cleanup() 清理）
```

**工具结果缓存（Redis String）**

```
Key: tool:{toolName}:{paramsHash}
Value: JSON 序列化的工具结果
TTL: 按工具类型定制（通过 OptimProperties 配置）
```

**意图识别缓存（规划中）**

```
L1 (内存): ConcurrentHashMap<userId+query, IntentResult>
L2 (Redis): intent:cache:{userId}:{hash(query)}
L3 (向量): intent:vector:{hash(normalizedQuery)}
```

---

## 七、核心优化层设计

### 7.1 优化层子系统

| 子系统 | 包路径 | 职责 | 状态 |
|--------|--------|------|------|
| **意图路由** | `optim.intent` | 三层路由：规则 → 轻量AI → 完整AI | 已上线 |
| **意图向量缓存** | `optim.intent` | 意图识别结果持久化，三级缓存 | 规划中 |
| **响应缓存** | `optim.cache` + `optim.cost` | 语义缓存、工具结果缓存 | 已上线 |
| **上下文压缩** | `optim.compress` + `optim.context` | 消息压缩、历史管理 | 部分实现（压缩路径尚未接入主调用链） |
| **自纠错** | `optim.correction` | 工具结果验证、纠错重试 | 已上线 |
| **降级兜底** | `optim.fallback` | 工具失败、AI 异常时的兜底 | 已上线 |
| **质量/成本** | `optim.quality` + `optim.cost` | 质量模式、模型路由、Token 统计 | 已上线 |

### 7.2 意图路由三层架构

```java
// 第1层：规则提取（IntentRouterImpl / HybridIntentRouter.tryRuleLayer）
RouteResult ruleResult = tryRuleLayer(userId, query);
if (ruleResult != null && ruleResult.isHandled()) {
    return ruleResult;
}
// ruleResult 中已含初步 intentType，传递给下层
IntentType intentType = ruleResult != null ? ruleResult.getIntentType() : IntentType.GENERAL_CHAT;

// 第2层：轻量AI提取（intentType 作为上下文提示）
RouteResult lightweightResult = tryLightweightAiLayer(userId, query, intentType);
if (lightweightResult != null && lightweightResult.isHandled()) {
    return lightweightResult;
}

// 第3层：完整AI调用（兜底）
return tryFullAiLayer(query, intentType);
```

**三层路由配置**

| 模式 | 类 | 说明 | 适用场景 |
|------|-----|------|----------|
| `rule-only` | IntentRouterImpl | 纯规则路由 | 高频简单查询 |
| `hybrid` | HybridIntentRouter | 三层混合（默认） | 通用场景 |
| `ai-only` | (直接调用AI) | 无路由拦截 | 冷启动、测试 |

### 7.3 意图向量缓存（规划中）

```
用户输入
    │
    ▼
QueryNormalizer
    示例："今天" → "{current_date}"
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

### 7.4 质量模式与模型路由

**质量模式**

| 模式 | maxTokens | temperature | 适用场景 |
|------|-----------|-------------|----------|
| SPEED | 400 | 0.2 | 简单问答、闲聊 |
| BALANCED | 800 | 0.3 | 通用场景（默认） |
| QUALITY | 2000 | 0.7 | 复杂分析、创意写作 |

> 以上为 `QualityProperties` 中的默认值，可通过 `ai.quality.max-tokens` / `ai.quality.temperature` 配置覆盖。

**模型路由策略**

| 条件 | 选择模型 | 示例 |
|------|----------|------|
| 消息 ≤ 20字 / 问候语 / 简单问答 | 小模型 (moonshot-v1-8k) | "你好"、"谢谢" |
| 消息 > 100字 / 含"为什么/分析/对比" | 大模型 (moonshot-v1-32k) | 长文本分析 |
| 默认 | 中等模型 | 一般对话 |

**已知问题**: `QualityOptimizer` 与 `ModelRouter` 当前无联动，V2 由 `StrategyEngine` 统一。

### 7.5 缓存策略

| 缓存类型 | 实现类 | 用途 | TTL |
|---------|--------|------|-----|
| 语义响应缓存 | SemanticCacheService | AI 完整响应缓存，向量相似度匹配 | 由 `VectorStore` 过期策略管理 |
| 工具结果缓存 | ToolCacheManager | 工具调用结果缓存 | 按工具类型定制（`OptimProperties` 配置） |
| 意图识别缓存 | IntentCacheStore | 意图识别结果缓存（规划中） | L1: 5分钟 / L2: 7天 / L3: 30天 |

---

## 八、MCP 工具架构

### 8.1 工具注册中心

```
[ToolRegistry]
     │
  ┌──┴──┬────────┬────────┬────────┐
  ▼     ▼        ▼        ▼        ▼
Weather Calendar   Todo    Route   (扩展)
 Tool    Tool    Tool    Tool
```

### 8.2 工具列表

| 工具名 | 类 | 功能 | 必要入参 |
|--------|-----|------|----------|
| `get_weather` | WeatherTool | 天气查询 | `city` |
| `create_calendar_event` | CalendarTool | 创建日程 | `title`, `startTime` |
| `get_calendar_events` | CalendarTool | 查询日程列表 | `date`（可选）|
| `delete_calendar_event` | CalendarTool | 删除日程 | `eventId` |
| `create_todo` | TodoTool | 创建待办 | `title` |
| `list_todos` | TodoTool | 查询待办列表 | 无 |
| `complete_todo` | TodoTool | 标记完成 | `todoId` |
| `delete_todo` | TodoTool | 删除待办 | `todoId` |
| `plan_route` | RouteTool | 路线规划 | `origin`, `destination` |

### 8.3 工具调用流程

```
AI 响应包含 [TOOL_CALL]
        │
        ▼
┌───────────────┐
│  tool_call    │──→ SSE 事件通知客户端
│  SSE 事件     │
└───────────────┘
        │
        ▼
ToolRegistry.executeTool()
  ├─ 工具不存在 → error SSE
  └─ 执行工具 → 获取结果
        │
        ▼
SelfCorrectingAgent.validate()
  ├─ 验证失败 → correctAndRetry()
  │   ├─ 重试成功 → 继续
  │   └─ 重试失败 → error SSE（但继续）
  └─ 验证通过 → 继续
        │
        ▼
┌───────────────┐
│  tool_result  │──→ SSE 事件通知客户端
│  SSE 事件     │
└───────────────┘
        │
        ▼
结果注入 messages 历史
        │
        ▼
continueAiStream() 递归调用 AI
```

---

## 九、SSE 通信协议

### 9.1 事件类型定义

| 事件 | 用途 | payload 示例 |
|------|------|-------------|
| `thinking` | AI 思考推理过程（增量） | `{"type":"thinking","text":"..."}` |
| `content` | 流式文本内容（增量） | `{"type":"content","text":"今天天气"}` |
| `content_done` | AI 文本生成完毕 | `{"type":"content_done"}` |
| `tool_call` | 工具调用通知 | `{"type":"tool_call","toolCall":{...}}` |
| `tool_result` | 工具执行结果 | `{"type":"tool_result","toolResult":{...}}` |
| `audio_done` | TTS 合成结束（详见 [SSE+TTS 规范 §3.4](./SSE_TTS_UNIFIED_SPEC.md)） | 成功: `{"type":"audio_done","url":"...","conversationId":1,"messageId":2}` / 超时: `{"type":"audio_done","timeout":true,...}` / 无音频: `{"type":"audio_done","noaudio":true,...}` / 异常: `{"type":"audio_done","error":"...","conversationId":1,"messageId":2}` |
| `token_usage` | Token 消耗统计 | `{"type":"token_usage","tokenUsage":{"inputTokens":10,"outputTokens":50,"duration":1200,"model":"moonshot-v1-8k"}}` |
| `cache_hit` | 响应缓存命中 | `{"type":"cache_hit","content":"..."}` |
| `clarification` | 意图澄清提示 | `{"type":"clarification","content":"..."}` |
| `error` | 错误信息 | `{"type":"error","message":"..."}` |
| `done` | 流结束（必发） | `{"type":"done"}` |

> **conversationId / messageId 说明**：`content`、`content_done`、`audio_done` 必须包含这两个字段；`cache_hit` 和 `clarification` 在路由阶段产生，此时尚未创建 AI 消息，不携带这两个字段；其余事件按需携带。完整字段定义见 [SSE+TTS 规范](./SSE_TTS_UNIFIED_SPEC.md)。

### 9.2 合法事件序列

**① 正常对话流**
```
[thinking*] → content+ → content_done → audio_done → token_usage → done
```

**② 工具调用流**
```
[thinking*] → (tool_call → tool_result)+ → content+ → content_done → audio_done → token_usage → done
```

**③ 缓存命中流**（无实际 AI 调用，不发 token_usage）
```
cache_hit → content_done → audio_done → done
```

**④ 意图澄清流**
```
clarification → done
```

**⑤ 错误流**
```
[content*] → error → done
```

### 9.3 跨端 ID 关联

```
┌─────────────┐         ┌─────────────┐         ┌─────────────┐
│   后端       │         │   前端      │         │  Android    │
│             │         │             │         │             │
│ conversation│────────→│ conversation│────────→│ conversation│
│     Id      │         │     Id      │         │     Id      │
│             │         │             │         │             │
│  user_msg   │────────→│  user_msg   │────────→│  user_msg   │
│  messageId  │         │  messageId  │         │  messageId  │
│             │         │             │         │             │
│  ai_msg     │────────→│  ai_msg     │────────→│  ai_msg     │
│  messageId  │         │  messageId  │         │  messageId  │
└─────────────┘         └─────────────┘         └─────────────┘
```

---

## 十、管理后台 API

### 10.1 接口列表

| 路径 | 方法 | 说明 |
|------|------|------|
| `/api/admin/metrics/current` | GET | 当前优化效果指标快照 |
| `/api/admin/metrics/trend` | GET | 指标趋势（`?days=7`）|
| `/api/admin/metrics/comparison` | GET | 优化前后对比 |
| `/api/admin/metrics/reset` | POST | 重置统计 |
| `/api/admin/cost/stats` | GET | 系统成本统计 |
| `/api/admin/cost/daily` | GET | 每日成本趋势 |
| `/api/admin/cost/today` | GET | 今日成本快速查询 |
| `/api/admin/cache/clear/semantic` | POST | 清除语义缓存 |
| `/api/admin/cache/clear/tool` | POST | 清除工具缓存 |
| `/api/admin/cache/clear/all` | POST | 清除全部缓存 |
| `/api/admin/cache/stats` | GET | 缓存统计 |
| `/api/quality/mode` | GET | 查询当前质量模式 |
| `/api/quality/mode` | PUT | 切换质量模式 |
| `/api/quality/metrics` | GET | 质量模式使用统计 |

### 10.2 鉴权

| 前缀 | 权限要求 | 说明 |
|------|---------|------|
| `/api/admin/**` | 管理员角色 | 系统级操作：缓存清理、成本统计、指标重置 |
| `/api/quality/**` | 管理员角色 | 质量模式管理（当前要求管理员，后续可降为用户级） |

当前通过 JWT 解析 `userId` 判断管理员身份。生产环境建议改为数据库角色表或配置文件白名单。

---

## 十一、非功能性需求

### 11.1 性能需求

| 指标 | 目标 | 测试方法 |
|------|------|----------|
| P50 响应时间 | < 500ms | 缓存命中场景 |
| P95 响应时间 | < 2s | 正常 AI 调用 |
| P99 响应时间 | < 5s | 复杂查询场景 |
| 并发用户 | 1000+ | 负载测试 |
| 系统吞吐量 | 100 QPS | 压力测试 |

### 11.2 可用性需求

| 指标 | 目标 | 措施 |
|------|------|------|
| 系统可用性 | 99.9% | 多 Provider 降级 |
| 故障恢复时间 | < 5分钟 | 自动熔断与恢复 |
| 数据持久化 | 100% | 主从复制 |

### 11.3 安全需求

| 需求 | 实现 |
|------|------|
| 身份认证 | JWT Token |
| 传输安全 | HTTPS |
| 输入验证 | 参数校验、SQL 注入防护 |
| 速率限制 | API 限流 |
| 敏感数据 | 加密存储 |

---

## 十二、风险与缓解措施

### 12.1 技术风险

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|--------|------|----------|
| 工具调用无限递归 | 中 | 高 | 增加递归深度限制（规划中）|
| 上下文压缩未接入 | 低 | 中 | P1 待办，长对话可能丢失上下文 |
| 质量与模型路由割裂 | 中 | 中 | V2 由 StrategyEngine 统一 |
| AI Provider 故障 | 低 | 高 | 双 Provider 自动降级 |
| 缓存数据不一致 | 低 | 中 | 完善缓存失效机制 |

### 12.2 业务风险

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|--------|------|----------|
| AI 成本超预算 | 中 | 高 | 多层拦截、缓存、监控 |
| 响应速度不达标 | 中 | 中 | 流式输出、缓存优化 |
| 用户意图识别错误 | 中 | 中 | 置信度评估、澄清机制 |

### 12.3 合规风险

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|--------|------|----------|
| 数据隐私 | 低 | 高 | 数据脱敏、加密存储 |
| AI 生成内容合规 | 中 | 中 | 内容审核、敏感词过滤 |

---

## 十三、附录

### 附录 A：术语表

| 术语 | 说明 |
|------|------|
| SSE | Server-Sent Events，服务器推送事件 |
| MCP | Model Context Protocol，模型上下文协议 |
| TTS | Text-to-Speech，文本转语音 |
| Provider | AI 服务提供商（如 Kimi、MiniMax）|
| 意图路由 | 根据用户意图将请求分发到不同处理器的机制 |
| 语义缓存 | 基于语义相似度的响应缓存 |

### 附录 B：参考文档

| 文档 | 路径 |
|------|------|
| SSE+TTS 统一规范 | [SSE_TTS_UNIFIED_SPEC.md](./SSE_TTS_UNIFIED_SPEC.md) |
| 意图向量缓存设计 | [.spec-workflow/specs/ai-agent-optimization-v2/intent-vector-cache-design.md](../.spec-workflow/specs/ai-agent-optimization-v2/intent-vector-cache-design.md) |
| CLAUDE.md | [CLAUDE.md](../CLAUDE.md) |

### 附录 C：待办事项

| 优先级 | 事项 | 状态 | 预计完成时间 |
|--------|------|------|--------------|
| P0 | 意图向量缓存落地 | 规划中 | 待定 |
| P0 | 自纠错阈值调优 | 规划中 | 待定 |
| P1 | 接通 ContextManager 压缩路径 | 待开发 | 待定 |
| P1 | 工具调用递归深度限制 | 待开发 | 待定 |
| P1 | QualityOptimizer 与 ModelRouter 联动 | 待开发 | V2 |
| P1 | 缓存策略精细化 | 待开发 | 待定 |
| P2 | 前端接入管理后台数据接口 | 待开发 | 待定 |

### 附录 D：架构决策记录 (ADR)

#### ADR-001: 选择 SSE 而非 WebSocket

**背景**: 需要支持 AI 流式响应

**决策**: 使用 SSE (Server-Sent Events)

**理由**:
1. **通信方向匹配**：AI 流式响应是纯服务器推送，用户无需在流式传输中途发送新消息；SSE 的单向模型与此完全匹配。
2. **基础设施兼容**：SSE 基于标准 HTTP，可直接通过现有 Nginx 反向代理和 CDN，无需额外配置 WebSocket 升级握手和长连接保持。
3. **实现复杂度低**：WebSocket 需要管理连接生命周期、心跳、Session 状态；Spring WebFlux 对 SSE 有原生支持（`ServerSentEvent<T>`），代码量更少。
4. **自动重连**：浏览器原生 EventSource 支持 last-event-id 自动重连，无需手动实现。
5. **未来可扩展**：如后续需要双向实时通道（如推送通知），可通过独立的 WebSocket 端点叠加，不影响当前流式对话的 SSE 实现。

**替代方案**: WebSocket

#### ADR-002: 三层意图路由架构

**背景**: 需要降低 AI 调用成本

**决策**: 采用三层路由（规则 → 轻量AI → 完整AI）

**理由**:
- 简单查询快速拦截
- 渐进式升级降低平均成本
- 规则层可覆盖 40%+ 高频查询

**替代方案**: 纯 AI 路由（成本高）、纯规则路由（覆盖率低）

#### ADR-003: Kimi + MiniMax 双 Provider

**背景**: 降低单点依赖，模型能力互补

**决策**: 支持 Kimi 和 MiniMax 双引擎

**理由**:
- Kimi 代码能力强
- MiniMax 支持 reasoning_content
- 自动降级提高可用性

