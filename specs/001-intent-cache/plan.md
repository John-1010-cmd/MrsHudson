# 实现计划：意图向量缓存

**分支**: `001-intent-cache` | **日期**: 2026-04-04 | **规范**: [spec.md](./spec.md)
**输入**: 来自 `/specs/001-intent-cache/spec.md` 的功能规范

---

## 概要

为 MrsHudson AI 助手构建三级意图识别缓存系统。系统将在 L1（内存，5分钟）、L2（Redis，7天）和 L3（向量数据库，30天）层缓存意图识别结果，将常见查询延迟从约 200ms 降至约 50ms。集成点是现有的 HybridIntentRouter，带熔断器回退。

**技术方案**: 按 AI_ARCHITECTURE.md 使用 **ConcurrentHashMap**（通过 Caffeine 包装）作为 L1 内存缓存，Redis Hash 作为 L2 持久化，**Redis Vector（HNSW 索引）**作为 L3 语义相似度搜索。实现时间规范化以处理中文时间表达（今天/明天 → 具体日期）。

---

## 技术背景

**语言/版本**: Java 17+ (Spring Boot 3.2)  
**主要依赖**:
- Caffeine 3.x (L1 缓存)
- Spring Data Redis (L2 缓存)
- **Redis Vector / RediSearch** (L3 缓存 - HNSW 索引)
- Resilience4j (熔断器)

**存储**: Redis（现有），**L3 使用 Redis Vector**  
**测试**: JUnit 5, Mockito, Spring Boot Test  
**目标平台**: Spring Boot 后端服务  
**项目类型**: 库/服务（后端优化层）  
**性能目标**:
- L1 命中: < 10ms
- L2 命中: < 50ms
- L3 命中: < 100ms
- 回退开销: < 10ms

**约束**:
- L1 内存 < 100MB
- Redis 已用于其他缓存
- 不得阻塞现有意图路由器流程

**规模/范围**:
- 支持 10k+ 并发用户
- 每实例 500 L1 条目
- 无限 L2 条目（Redis）

---

## 架构检查

*门槛：Phase 0 研究前必须通过。Phase 1 设计后重新检查。*

| 原则 | 状态 | 说明 |
|------|------|------|
| AI 优先架构 | ✅ 通过 | 优化 AI 交互延迟 |
| 不可变数据 | ✅ 通过 | 缓存条目创建后不可变 |
| 分层架构 | ✅ 通过 | 添加优化层 |
| 文件组织 | ✅ 通过 | 缓存组件新包 |
| 错误处理 | ✅ 通过 | 优雅回退的熔断器 |
| 流优先 | ✅ 通过 | 异步缓存写入 |
| TDD | ✅ 通过 | 需要单元 + 集成测试 |
| 配置驱动 | ✅ 通过 | TTL 值可配置 |
| 安全 | ✅ 通过 | 通过 userId 用户隔离 |
| 性能 | ✅ 通过 | 功能核心目标 |

**架构合规性**: ✅ 已批准

---

## 项目结构

### 文档（本功能）

```text
specs/001-intent-cache/
├── spec.md              # 功能规范
├── plan.md              # 本文件 - 实现计划
├── research.md          # 技术研究
├── data-model.md        # 缓存条目模式
├── contracts/           # 接口定义
│   ├── IntentCacheStore.md      # 主缓存门面 API
│   ├── QueryNormalizer.md       # 时间规范化
│   └── CacheCircuitBreaker.md   # Resilience4j 熔断器
└── tasks.md             # 由 /speckit.tasks 生成
```

### 源代码（仓库根目录）

```text
mrshudson-backend/src/main/java/com/mrshudson/
├── optim/intent/cache/           # 新增: 意图缓存包（与 AI_ARCHITECTURE.md 对齐）
│   ├── IntentCacheStore.java     # 主缓存门面（L1→L2→L3 级联）
│   ├── L1CacheStore.java         # L1: 基于 ConcurrentHashMap
│   ├── L2CacheStore.java         # L2: Redis Hash
│   ├── L3CacheStore.java         # L3: Redis Vector
│   ├── QueryNormalizer.java      # 时间规范化（具体日期）
│   ├── IntentFingerprint.java    # 指纹生成
│   ├── CacheCircuitBreaker.java  # Resilience4j 熔断器
│   ├── CacheMetrics.java         # 指标收集
│   └── dto/
│       ├── IntentCacheEntry.java
│       └── CacheStatistics.java
├── optim/config/
│   └── IntentCacheProperties.java # 新增: 配置属性
└── optim/intent/
    └── HybridIntentRouter.java   # 修改: 添加 IntentCacheStore 查找

mrshudson-backend/src/test/java/com/mrshudson/
├── optim/intent/cache/           # 新增: 测试包
│   ├── IntentCacheStoreTest.java
│   ├── L1CacheStoreTest.java
│   ├── L2CacheStoreTest.java
│   ├── QueryNormalizerTest.java
│   └── CacheCircuitBreakerTest.java
└── integration/
    └── IntentCacheIntegrationTest.java
```

**结构决策**: 单一后端项目，新增 `optim.intent.cache` 包，遵循 AI_ARCHITECTURE.md 结构（在 `optim.intent` 下）。

---

## 阶段

### Phase 0: 研究与技术选型

**目标**: 评估并选择各缓存层的具体技术

**任务**:
1. **L1 缓存**: 对比 Caffeine vs Guava Cache
   - 性能基准测试
   - 内存效率
   - Spring Boot 集成

2. **L3 向量搜索**: 评估 Redis Vector 选项
   - **Redis Vector** 与 HNSW 索引（推荐，与 AI_ARCHITECTURE.md 对齐）
   - RediSearch 模块用于向量相似度
   - 决策: Redis Vector（统一 Redis 技术栈）

3. **嵌入服务**: 对比 MiniMax vs Kimi
   - API 延迟
   - 每 1k 请求成本
   - 中文语言支持

**输出**: `research.md` 包含技术决策和理由

**预估**: 2-3 小时

---

### Phase 1: 设计与契约

**目标**: 定义数据模型、接口和集成点

**任务**:
1. **数据模型设计**
   - `IntentCacheEntry` 字段和序列化
   - `IntentFingerprint` 结构
   - Redis Hash 键模式

2. **接口定义**
   - `IntentCacheStore`（主门面 API）
   - `L1/L2/L3CacheStore`（分层接口）
   - `QueryNormalizer`
   - `CacheCircuitBreaker`

3. **集成设计**
   - HybridIntentRouter 如何调用缓存
   - 缓存未命中流程
   - 回填机制（L3→L2→L1）

4. **配置设计**
   - `OptimProperties` 扩展
   - 每层的功能标志

**输出**:
- `data-model.md`
- `contracts/*.md`
- `quickstart.md`（开发者设置指南）

**预估**: 3-4 小时

---

### Phase 2: 核心实现（MVP - L1 + L2）

**目标**: 实现 L1 和 L2 缓存层，完整测试覆盖

**任务**:
1. **配置** (`IntentCacheProperties.java`)
2. **L1 缓存** (`L1CacheStore`，通过 Caffeine 使用 ConcurrentHashMap)
3. **L2 缓存** (`L2CacheStore`，使用 Redis Hash)
4. **查询规范化** (`QueryNormalizer`，中文时间词 → 具体日期)
5. **指纹生成器**（规范化输入的 MD5 哈希）
6. **熔断器** (`CacheCircuitBreaker`，使用 Resilience4j)
7. **主服务** (`IntentCacheStore` - L1→L2→L3 级联)
8. **指标** (`CacheMetrics`，用于监控)
9. **集成**（修改 `HybridIntentRouter` 调用 `IntentCacheStore`）

**测试**:
- 所有服务的单元测试
- 使用 Testcontainers 的集成测试（Redis）
- 性能基准测试

**预估**: 8-10 小时

---

### Phase 3: 高级功能（L3 Redis Vector 缓存）

**目标**: 使用 Redis Vector 实现 L3 向量相似度搜索 (IntentCacheStore.L3)

**任务**:
1. **嵌入集成**（复用现有嵌入机制）
2. **L3 缓存存储** (`L3CacheStore`，使用 Redis Vector HNSW 索引)
3. **相似度阈值** 配置（默认 0.92）
4. **异步 L3 写入**（非阻塞，通过 `IntentCacheStore`）
5. **向量索引** 管理（Redis Vector 索引生命周期）

**测试**:
- 使用 Redis Vector 的 L3 集成测试
- 语义相似度准确性测试

**预估**: 6-8 小时

---

### Phase 4: 生产加固

**目标**: 监控、文档和部署就绪

**任务**:
1. **指标与监控**
   - 按层缓存命中/未命中率
   - 延迟百分位
   - 内存使用追踪
   - 熔断器状态

2. **文档**
   - 架构决策记录 (ADR)
   - 运维手册
   - 故障排查指南

3. **负载测试**
   - 模拟 10k 并发用户
   - 验证性能目标

**预估**: 4-6 小时

---

## 依赖

### 现有基础设施
- Redis（已用于语义缓存、工具缓存）
- Spring Boot 响应式栈 (Flux/Mono)
- MyBatis Plus, MySQL

### 新依赖
```xml
<!-- L1 缓存 - Caffeine 提供基于 ConcurrentHashMap 的缓存，带 LRU 淘汰 -->
<!-- 注意: AI_ARCHITECTURE.md 指定 ConcurrentHashMap；Caffeine 包装此功能并添加额外特性 -->
<dependency>
    <groupId>com.github.ben-manes</groupId>
    <artifactId>caffeine</artifactId>
    <version>3.1.8</version>
</dependency>

<!-- 熔断器 -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.1.0</version>
</dependency>

<!-- Redis Vector (L3) - 通过现有 Redis/Lettuce 客户端 -->
<!-- 注意: Redis Vector/RediSearch 功能需要 Redis 7.2+ with RediSearch 模块 -->
```

---

## 风险缓解

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| L3 向量数据库增加过多复杂度 | 中 | 高 | 使 L3 可选，先实现 L1+L2 |
| 缓存失效逻辑过于复杂 | 低 | 中 | 从基于 TTL 开始，后期添加显式失效 |
| 规范化导致错误匹配 | 中 | 高 | 可配置规范化，提供跳过缓存的逃生通道 |
| L1 缓存内存压力 | 低 | 中 | 严格大小限制、LRU 淘汰、内存指标 |
| Redis 连接问题 | 中 | 高 | 带自动回退的熔断器 |

---

## 下一步

1. ✅ **规范完成**: 功能规范已批准
2. ✅ **计划完成**: 本实现计划
3. **下一步**: 运行 `/speckit.tasks` 生成可执行任务列表
4. **然后**: 运行 `/speckit.implement` 开始编码

**准备好生成任务了吗？** 运行 `/speckit.tasks` 将 Phase 2 分解为具体、可分配的任务。
