# 任务列表：意图向量缓存

**分支**: `001-intent-cache` | **规范**: [spec.md](./spec.md) | **计划**: [plan.md](./plan.md)

---

## Phase 0: 技术调研 (P0)

### 任务 0.1: L1 缓存方案调研
**优先级**: P0 | **预估**: 1小时
- [ ] 对比 Caffeine 与 Guava Cache
- [ ] 运行性能基准测试
- [ ] 在 research.md 中记录决策

**输出**: research.md#L1-缓存选择

---

### 任务 0.2: L3 Redis 向量方案调研
**优先级**: P0 | **预估**: 1小时
- [ ] 评估 Redis Vector HNSW 索引能力
- [ ] 检查 RediSearch 模块要求
- [ ] 在 research.md 中记录决策

**输出**: research.md#L3-向量数据库选择

---

### 任务 0.3: 嵌入 API 调研
**优先级**: P0 | **预估**: 1小时
- [ ] 对比 MiniMax 与 Kimi 嵌入 API
- [ ] 检查定价和延迟
- [ ] 在 research.md 中记录决策

**输出**: research.md#嵌入服务选择

---

## Phase 1: 设计 (P0)

### 任务 1.1: 设计数据模型
**优先级**: P0 | **预估**: 1小时
- [ ] 定义 IntentCacheEntry 字段
- [ ] 定义 IntentFingerprint 结构
- [ ] 定义 Redis Hash 键模式
- [ ] 创建 data-model.md

**输出**: data-model.md

---

### 任务 1.2: 设计缓存存储接口
**优先级**: P0 | **预估**: 1小时
- [ ] 定义 IntentCacheStore API (主门面)
- [ ] 定义 L1/L2/L3 缓存存储接口
- [ ] 在 contracts/ 中记录文档

**输出**: contracts/IntentCacheStore.md

---

### 任务 1.3: 设计查询规范化
**优先级**: P0 | **预估**: 1小时
- [ ] 定义时间规范化规则 (具体日期)
- [ ] 定义口语规范化 (可选)
- [ ] 记录指纹生成逻辑

**输出**: contracts/QueryNormalizer.md

---

### 任务 1.4: 设计缓存熔断器
**优先级**: P0 | **预估**: 1小时
- [ ] 定义 CacheCircuitBreaker 接口
- [ ] 配置 Resilience4j
- [ ] 记录降级策略

**输出**: contracts/CacheCircuitBreaker.md

---

## Phase 2: 核心实现 (P0)

### 任务 2.1: 添加依赖
**优先级**: P0 | **预估**: 30分钟
- [ ] 添加 Caffeine 到 pom.xml
- [ ] 添加 Resilience4j 到 pom.xml
- [ ] 验证无冲突

**文件**: mrshudson-backend/pom.xml

---

### 任务 2.2: 创建配置属性
**优先级**: P0 | **预估**: 1小时
- [ ] 创建 IntentCacheProperties 类
- [ ] 添加到 OptimProperties
- [ ] 添加 application.yml 默认值

**文件**:
- optim/intent/cache/IntentCacheProperties.java
- optim/config/OptimProperties.java
- application.yml

---

### 任务 2.3: 实现 DTO
**优先级**: P0 | **预估**: 1小时
- [ ] IntentCacheEntry
- [ ] IntentFingerprint
- [ ] CacheStatistics

**文件**: optim/intent/cache/dto/*.java

---

### 任务 2.4: 实现查询规范化
**优先级**: P0 | **预估**: 2小时
- [ ] QueryNormalizer (中文时间词 → 具体日期)
- [ ] IntentFingerprintGenerator (MD5 哈希)
- [ ] 单元测试

**文件**:
- optim/intent/cache/QueryNormalizer.java
- optim/intent/cache/IntentFingerprint.java
- *Test.java

---

### 任务 2.5: 实现 L1 缓存存储
**优先级**: P0 | **预估**: 2小时
- [ ] L1CacheStore 使用 ConcurrentHashMap (通过 Caffeine)
- [ ] LRU 淘汰
- [ ] 5分钟 TTL
- [ ] 单元测试

**文件**:
- optim/intent/cache/L1CacheStore.java
- L1CacheStoreTest.java

---

### 任务 2.6: 实现 L2 缓存存储
**优先级**: P0 | **预估**: 2小时
- [ ] L2CacheStore 使用 Redis
- [ ] Hash 结构
- [ ] 7天 TTL
- [ ] 使用 Testcontainers 的单元测试

**文件**:
- optim/intent/cache/L2CacheStore.java
- L2CacheStoreTest.java

---

### 任务 2.7: 实现熔断器
**优先级**: P0 | **预估**: 2小时
- [ ] 使用 Resilience4j 的 CacheCircuitBreaker
- [ ] 状态管理 (CLOSED/OPEN/HALF_OPEN)
- [ ] 指标集成
- [ ] 单元测试

**文件**:
- optim/intent/cache/CacheCircuitBreaker.java
- CacheCircuitBreakerTest.java

---

### 任务 2.8: 实现意图缓存存储
**优先级**: P0 | **预估**: 3小时
- [ ] IntentCacheStore (主门面)
- [ ] L1→L2→L3 级联查找
- [ ] 自动回填
- [ ] 指标收集
- [ ] 单元测试

**文件**:
- optim/intent/cache/IntentCacheStore.java
- IntentCacheStoreTest.java

---

### 任务 2.9: 集成到 HybridIntentRouter
**优先级**: P0 | **预估**: 2小时
- [ ] 在 HybridIntentRouter 中添加 IntentCacheStore 查找
- [ ] 处理缓存未命中流程
- [ ] 意图识别后存储结果
- [ ] 集成测试

**文件**:
- optim/intent/HybridIntentRouter.java
- IntentCacheIntegrationTest.java

---

### 任务 2.10: 实现缓存指标
**优先级**: P0 | **预估**: 1小时
- [ ] CacheMetrics 类
- [ ] 每层命中/未命中计数器
- [ ] 延迟百分位
- [ ] 内存使用追踪

**文件**: optim/intent/cache/CacheMetrics.java

---

## Phase 3: L3 向量缓存 (P1 - 可选)

### 任务 3.1: 验证 Redis Vector 要求
**优先级**: P1 | **预估**: 30分钟
- [ ] 验证 Redis 7.2+ 与 RediSearch 模块
- [ ] 记录配置要求

**文件**: pom.xml

---

### 任务 3.2: 实现嵌入集成
**优先级**: P1 | **预估**: 2小时
- [ ] 复用现有嵌入机制
- [ ] 嵌入客户端包装器
- [ ] 嵌入缓存

**文件**:
- optim/intent/cache/EmbeddingService.java
- optim/intent/cache/MiniMaxEmbeddingClient.java

---

### 任务 3.3: 实现 L3 缓存存储
**优先级**: P1 | **预估**: 3小时
- [ ] L3CacheStore 使用 Redis Vector
- [ ] 向量相似度搜索 (HNSW 索引)
- [ ] 可配置阈值
- [ ] 异步写入 (通过 IntentCacheStore)

**文件**:
- optim/intent/cache/L3CacheStore.java
- L3CacheStoreTest.java

---

## Phase 4: 生产加固 (P1)

### 任务 4.1: 添加监控指标
**优先级**: P1 | **预估**: 2小时
- [ ] Micrometer 指标集成
- [ ] 缓存命中率仪表盘
- [ ] 延迟直方图
- [ ] 熔断器状态

**文件**: optim/intent/cache/CacheMetrics.java, MeterRegistry 配置

---

### 任务 4.2: 负载测试
**优先级**: P1 | **预估**: 3小时
- [ ] JMeter/K6 测试场景
- [ ] 1万并发用户模拟
- [ ] 性能基准报告

**文件**: load-tests/

---

### 任务 4.3: 文档编写
**优先级**: P1 | **预估**: 2小时
- [ ] 架构决策记录 (ADR)
- [ ] 运维手册
- [ ] 故障排查指南
- [ ] 更新 CLAUDE.md

**文件**:
- docs/adr/001-intent-cache.md
- docs/runbooks/intent-cache.md

---

## 任务汇总

| 阶段 | 任务数 | 预估工时 | 优先级 |
|------|--------|----------|--------|
| Phase 0: 技术调研 | 3 | 3小时 | P0 |
| Phase 1: 设计 | 4 | 4小时 | P0 |
| Phase 2: 核心 (L1+L2) | 10 | 16.5小时 | P0 |
| Phase 3: L3 向量 | 3 | 5.5小时 | P1 |
| Phase 4: 生产加固 | 3 | 7小时 | P1 |
| **总计** | **23** | **~36小时** | - |

**P0 (MVP)**: ~23.5 小时
**P0 + P1 (完整版)**: ~36 小时

---

## 准备就绪

✅ **规范**: 已完成
✅ **计划**: 已完成
✅ **任务**: 已完成

**下一步**: 运行 `/speckit.implement` 或从任务 0.1 开始

**建议**: 从 Phase 0 调研任务开始，然后进入 Phase 2 核心实现。
