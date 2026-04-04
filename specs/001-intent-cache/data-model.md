# 数据模型：意图向量缓存

**功能**: 意图向量缓存  
**版本**: 1.0.0  
**对齐**: AI_ARCHITECTURE.md v3.5

---

## 实体概览

```
┌─────────────────────┐      ┌─────────────────────┐      ┌─────────────────────┐
│   IntentCacheEntry  │      │  IntentFingerprint  │      │  CacheStatistics    │
├─────────────────────┤      ├─────────────────────┤      ├─────────────────────┤
│ userId: Long        │      │ rawInput: String    │      │ l1HitRate: double   │
│ fingerprint: String │◄─────│ normalizedInput:    │      │ l2HitRate: double   │
│ intentType: String  │      │   String            │      │ l3HitRate: double   │
│ confidence: double  │      │ fingerprintHash:    │      │ totalHits: long     │
│ needsClarification: │      │   String            │      │ totalMisses: long   │
│   boolean           │      │ concreteDate:       │      │ avgLatencyMs:       │
│ candidates: List    │      │   LocalDate         │      │   double            │
│ createdAt: DateTime │      └─────────────────────┘      │ memoryUsageBytes:   │
│ ttlSeconds: int     │                                    │   long              │
│ accessCount: long   │                                    │ lastReset: DateTime │
└─────────────────────┘                                    └─────────────────────┘
```

---

## IntentCacheEntry

表示跨 L1/L2/L3 层存储的缓存意图识别结果。

### 字段

| 字段 | 类型 | 描述 | 存储层 |
|------|------|------|--------|
| `userId` | Long | 用户 ID，用于缓存隔离 | L1, L2 |
| `fingerprint` | String | 规范化输入的 MD5 哈希 | L1, L2, L3 |
| `intentType` | String | 识别的意图类型（如 WEATHER_QUERY） | L1, L2 |
| `confidence` | double | 置信度分数 (0.0-1.0) | L1, L2 |
| `needsClarification` | boolean | 是否需要澄清 | L1, L2 |
| `candidates` | List<IntentCandidate> | 替代意图候选 | L1, L2 |
| `createdAt` | LocalDateTime | 缓存条目创建时间 | L1, L2 |
| `ttlSeconds` | int | 生存时间（秒） | L1, L2 |
| `accessCount` | long | 缓存命中次数 | L1, L2 |
| `embedding` | float[] | L3 搜索的向量嵌入 | 仅 L3 |

### Redis Hash 模式 (L2)

```
键: intent:L2:{userId}:{fingerprint}
TTL: 7 天 (604800 秒)

字段:
  - intentType: "WEATHER_QUERY"
  - confidence: "0.95"
  - needsClarification: "false"
  - candidates: "[\"WEATHER_QUERY\",\"CALENDAR_QUERY\"]"
  - createdAt: "2026-04-04T10:30:00"
  - accessCount: "5"
```

### L1 内存结构

```java
// Caffeine 缓存键
键: "{userId}:{fingerprint}"
值: IntentCacheEntry

// 配置
.maximumSize(500)
.expireAfterWrite(5, TimeUnit.MINUTES)
.recordStats()
```

---

## IntentFingerprint

用于一致缓存键生成的用户输入的不可变规范化表示。

### 字段

| 字段 | 类型 | 描述 |
|------|------|------|
| `rawInput` | String | 原始用户查询 |
| `normalizedInput` | String | 时间/口语规范化后 |
| `fingerprintHash` | String | normalizedInput 的 MD5 哈希 |
| `concreteDate` | LocalDate | 用于时间规范化的日期 |

### 规范化流程

```
用户输入: "今天北京天气怎么样"
    │
    ▼
时间规范化
    │
    ▼
"2026-04-04北京天气怎么样"  (concreteDate = 2026-04-04)
    │
    ▼
口语规范化（可选）
    │
    ▼
"2026-04-04北京天气查询"
    │
    ▼
MD5 哈希
    │
    ▼
fingerprintHash = "a1b2c3d4e5f6..."
```

---

## CacheStatistics

缓存性能监控的运行时指标。

### 字段

| 字段 | 类型 | 描述 |
|------|------|------|
| `l1HitRate` | double | L1 缓存命中率 (0.0-1.0) |
| `l2HitRate` | double | L2 缓存命中率 (0.0-1.0) |
| `l3HitRate` | double | L3 缓存命中率 (0.0-1.0) |
| `totalHits` | long | 跨所有层的总缓存命中 |
| `totalMisses` | long | 总缓存未命中 |
| `avgLatencyMs` | double | 平均查找延迟（毫秒） |
| `memoryUsageBytes` | long | L1 缓存内存使用估算 |
| `lastReset` | LocalDateTime | 上次统计重置时间 |

### 指标收集

```java
// 每层计数器
Counter l1Hits = Counter.builder("cache.intent.l1.hits").register(registry);
Counter l1Misses = Counter.builder("cache.intent.l1.misses").register(registry);
Timer lookupLatency = Timer.builder("cache.intent.lookup.latency").register(registry);
```

---

## L3 向量存储 (Redis Vector)

### 索引结构

```
索引: intent_l3_vector_idx
算法: HNSW (Hierarchical Navigable Small World)
距离度量: COSINE
维度: 768 (MiniMax 嵌入) 或 1536 (Kimi 嵌入)

文档字段:
  - fingerprint: TEXT (用于精确匹配回退)
  - userId: TAG (用于用户隔离)
  - intentType: TEXT
  - confidence: NUMERIC
  - vector: VECTOR
  - createdAt: NUMERIC (时间戳)
```

### 相似度搜索查询

```
FT.SEARCH intent_l3_vector_idx
  "*=>[KNN 5 @vector $query_vector AS score]"
  PARAMS 2 query_vector $embedding
  SORTBY score
  DIALECT 2
```

---

## TTL 配置

| 层 | TTL | 理由 |
|----|-----|------|
| L1 | 5 分钟 | 热数据，内存受限 |
| L2 | 7 天 | 用户会话持久化 |
| L3 | 30 天 | 长期语义模式 |

---

## 序列化

### JSON 格式（用于 Redis 存储）

```json
{
  "userId": 12345,
  "fingerprint": "a1b2c3d4e5f6...",
  "intentType": "WEATHER_QUERY",
  "confidence": 0.95,
  "needsClarification": false,
  "candidates": [
    {"type": "WEATHER_QUERY", "confidence": 0.95},
    {"type": "CALENDAR_QUERY", "confidence": 0.03}
  ],
  "createdAt": "2026-04-04T10:30:00",
  "ttlSeconds": 604800,
  "accessCount": 5
}
```

---

## 约束

1. **内存**: L1 最多 500 条目（约 100MB）
2. **键长度**: Redis 键 < 512 字节
3. **值大小**: Redis Hash 值 < 512MB（通常 < 10KB）
4. **向量维度**: 必须匹配嵌入模型输出
5. **用户隔离**: 所有缓存条目按 userId 作用域隔离

---

## 与 AI_ARCHITECTURE.md 对齐

| 组件 | 架构规范 | 本模型 |
|------|----------|--------|
| QueryNormalizer | 相对时间→具体日期 | concreteDate 字段 |
| L1 | ConcurrentHashMap, 500条, 5min | Caffeine 使用相同限制 |
| L2 | Redis Hash, 7d TTL | Redis Hash 使用 7d TTL |
| L3 | Redis Vector, HNSW | Redis Vector HNSW 索引 |
| IntentResult | intentType, confidence, needsClarification | IntentCacheEntry 中相同字段 |
