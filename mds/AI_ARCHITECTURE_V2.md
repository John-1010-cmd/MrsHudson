# MrsHudson 后端 AI 架构设计文档 V2

> 版本：V2.0
> 更新时间：2026-03-29
> 状态：演进中（Phase 1~3）

---

# 一、设计目标（Design Goals）

## 1.1 核心目标

本架构旨在从传统“聊天驱动系统”升级为**可扩展 AI Agent 平台**：

* 支持多轮对话 + 工具调用
* 支持复杂任务规划（Agent）
* 控制成本（模型 / token / cache）
* 提供稳定、可观测的流式体验（SSE）

---

## 1.2 非功能性指标（SLO）

| 指标             | 目标                 |
| -------------- | ------------------ |
| P95 首 token 延迟 | < 1.5s             |
| P95 完整响应时间     | < 6s               |
| 成功率            | > 99.5%            |
| 平均 Token 成本    | 降低 30%（通过缓存 + 小模型） |
| Tool 调用失败率     | < 2%               |

---

## 1.3 设计原则

```text
1. Pipeline 优于 if-else
2. 策略集中化（统一决策层）
3. 状态显式化（避免隐式行为）
4. 可降级优先（Graceful Degradation）
5. 成本与质量可调
```

---

# 二、总体架构（Overview）

```text
                ┌──────────────────────────┐
                │      API Layer           │
                │  (Controller / SSE)     │
                └──────────┬──────────────┘
                           │
                           ▼
                ┌──────────────────────────┐
                │   Request Orchestrator   │
                └──────────┬──────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        ▼                  ▼                  ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│ ContextEngine│   │ StrategyEngine│   │ SafetyGuard  │
└──────┬───────┘   └──────┬───────┘   └──────┬───────┘
       │                  │                  │
       └──────────┬───────┴──────────┬───────┘
                  ▼                  ▼
         ┌──────────────────────────────────┐
         │        Execution Pipeline        │
         │ Cache → Intent → Plan → LLM → ToolLoop │
         └──────────────────────────────────┘
                           │
                           ▼
                ┌──────────────────────────┐
                │  Response Assembler (SSE)│
                └──────────────────────────┘
```

---

# 三、核心模块设计

---

## 3.1 Request Orchestrator（请求调度器）

### 职责

* 串联所有核心模块
* 控制执行生命周期
* 统一异常处理

### 核心流程

```text
1. 构建上下文（ContextEngine）
2. 生成执行策略（StrategyEngine）
3. 安全检查（SafetyGuard）
4. 执行 Pipeline
5. 输出 SSE
```

---

## 3.2 ContextEngine（上下文系统）

### 设计目标

替代 V1 的 `buildMessageList` + `ContextManager`，实现统一上下文构建。

---

### 结构

```text
ContextEngine
 ├── HistoryLoader（加载历史）
 ├── ContextCompressor（压缩）
 ├── MemoryInjector（长期记忆）
 └── PromptAssembler（拼接 prompt）
```

---

### 压缩策略

| 对话长度   | 策略                      |
| ------ | ----------------------- |
| < 10轮  | 保留全部                    |
| 10~30轮 | 最近8轮 + 历史摘要             |
| > 30轮  | 分层摘要（session + rolling） |

---

### 输出结构

```json
{
  "messages": [...],
  "summary": "...",
  "tokenEstimate": 1234
}
```

---

## 3.3 StrategyEngine（统一策略决策层）

### 设计目标

统一以下能力：

* 模型选择
* 参数控制
* 工具策略
* 缓存策略

---

### 输入

```text
- 用户输入
- 上下文
- 复杂度评分
- 成本策略
```

---

### 输出（ExecutionPlan）

```json
{
  "model": "moonshot-v1-32k",
  "temperature": 0.7,
  "maxTokens": 2048,
  "toolPolicy": {
    "maxDepth": 4,
    "maxCalls": 8,
    "timeoutMs": 10000
  },
  "cachePolicy": {
    "enabled": true
  }
}
```

---

## 3.4 Execution Pipeline（执行管线）

### 核心思想

用“可扩展 Stage 链”替代 if-else 流程。

---

### Pipeline 结构

```text
[1] CacheStage
[2] IntentStage
[3] PlanningStage（可选）
[4] LLMStage
[5] ToolExecutionLoop
[6] PostProcessStage
```

---

### Stage 接口

```java
interface Stage {
    StageResult execute(Context ctx, ExecutionPlan plan);
}
```

---

## 3.5 ToolExecutionLoop（工具执行引擎）

### 设计目标

替代递归调用，提供可控 Agent 能力。

---

### 执行逻辑

```java
for (int i = 0; i < maxDepth; i++) {

    output = llm.generate(context);

    if (!output.hasToolCall()) break;

    result = toolExecutor.execute(output.toolCall);

    result = validator.validateAndFix(result);

    context.append(result);
}
```

---

### 限制策略（必须）

| 参数       | 默认值 |
| -------- | --- |
| maxDepth | 4   |
| maxCalls | 8   |
| timeout  | 10s |

---

### 异常处理

```text
- 工具不存在 → fallback
- 执行失败 → retry + fallback
- 超限 → 强制终止
```

---

## 3.6 Response Assembler（SSE 输出层）

### 职责

* 统一事件格式
* 控制输出顺序
* 处理流式中断

---

### SSE 状态机（新增）

```text
START
 → content*
 → (tool_call → tool_result → content*)*
 → content_done
 → audio_done
 → done
```

---

### 保证

* done 一定发送
* content_done 最多一次
* tool_call 必有 tool_result

---

## 3.7 SafetyGuard（安全与保护）

### 功能

* 限流（QPS / 用户）
* 熔断（AI / 工具）
* 防止死循环

---

### 策略

```text
- 工具调用超限 → 终止
- token 超限 → 截断
- provider 连续失败 → fallback
```

---

# 四、缓存体系（统一规范）

---

## 4.1 缓存分层

| 层级 | 类型    | TTL |
| -- | ----- | --- |
| L1 | 本地内存  | 5分钟 |
| L2 | Redis | 7天  |
| L3 | 向量缓存  | 30天 |

---

## 4.2 缓存类型

| 类型            | 用途     |
| ------------- | ------ |
| SemanticCache | AI响应缓存 |
| ToolCache     | 工具结果缓存 |
| IntentCache   | 意图识别缓存 |

---

## 4.3 Key 规范

```text
hash(userId + normalizedQuery + contextHash)
```

---

# 五、完整调用链（V2）

```text
POST /api/chat/stream
    │
    ▼
Controller
    │
    ▼
RequestOrchestrator
    │
    ├── ContextEngine.build()
    ├── StrategyEngine.resolve()
    ├── SafetyGuard.check()
    │
    ├── Pipeline.execute()
    │       ├─ CacheStage
    │       ├─ IntentStage
    │       ├─ PlanningStage
    │       ├─ LLMStage
    │       ├─ ToolExecutionLoop
    │       └─ PostProcess
    │
    └── SSE 输出
```

---

# 六、与 V1 架构差异

| 维度      | V1         | V2               |
| ------- | ---------- | ---------------- |
| 主流程     | if-else    | Pipeline         |
| 上下文     | 截断 + 未接入压缩 | 统一 ContextEngine |
| 工具调用    | 递归         | 有界循环             |
| 策略      | 分散         | StrategyEngine   |
| 扩展性     | 中          | 高                |
| Agent能力 | 弱          | 强                |

---

# 七、演进路线

---

## Phase 1（基础稳定）

* 引入 ContextEngine
* ToolLoop 去递归
* 增加限制策略

---

## Phase 2（架构重构）

* 引入 StrategyEngine
* Pipeline 替换主流程

---

## Phase 3（能力升级）

* IntentCache 接入
* PlanningStage（Agent能力）
* 多模型协作

---

# 八、未来扩展能力

---

## 8.1 Agent 任务规划

```text
用户：帮我安排出差
→ 分解：
  - 查天气
  - 订机票
  - 创建日程
```

---

## 8.2 多模型协同

```text
Planner Model + Executor Model
```

---

## 8.3 长期记忆系统

```text
用户偏好 / 历史行为
```

---

# 九、结论

V2 架构实现了从：

```text
聊天系统 → AI Agent 平台
```

核心提升：

* ✅ 可扩展（Pipeline）
* ✅ 可控（Strategy）
* ✅ 可稳定运行（ToolLoop + SafetyGuard）
* ✅ 可持续优化（缓存 + 成本体系）

---
