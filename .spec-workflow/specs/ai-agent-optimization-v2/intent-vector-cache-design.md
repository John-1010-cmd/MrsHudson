# 意图识别向量缓存优化 - 设计文档

## Overview

本文档定义意图识别层的向量缓存优化方案，旨在通过将 AI 意图识别结果持久化到向量数据库，实现：

1. **降低 AI 调用成本**：相同/相似意图直接复用缓存结果
2. **提升识别准确率**：缓存数据越多，模式识别越准确
3. **支持时效性表达**：正确处理"今天"、"明天"等动态日期表达

---

## 目录

- [技术背景](#技术背景)
- [架构设计](#架构设计)
- [核心组件](#核心组件)
- [数据模型](#数据模型)
- [查询归一化](#查询归一化)
- [缓存策略](#缓存策略)
- [熔断与降级](#熔断与降级)
- [配置说明](#配置说明)
- [实施计划](#实施计划)

---

## 技术背景

### 现有架构

```
用户消息 → HybridIntentRouter → 三层混合
                              ├─ Rule Layer (规则)
                              ├─ Lightweight AI (轻量AI)
                              └─ Full AI (完整AI)
```

**现状问题**：
- 每次意图识别都需要调用 AI（即使是常见表达）
- 相同意图的重复识别浪费成本
- 规则匹配无法覆盖复杂多变的人类表达

### 优化方向

```
用户消息
    │
    ├─→ [AI优先模式] AI识别 → 缓存结果 → 返回
    │                      ↓
    │                   向量存储
    │
    └─→ [缓存优先模式] 向量搜索 → 命中 → 直接返回
                        ↓ 未命中
                     AI识别（降级）
```

---

## 架构设计

### 三层意图识别 + 向量缓存

```
┌─────────────────────────────────────────────────────────────────┐
│                    Intent Recognition Flow                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  用户输入: "北京今天天气怎么样"                                   │
│      │                                                         │
│      ▼                                                         │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ QueryNormalizer.normalize()                              │   │
│  │ "北京今天天气怎么样" → "北京 2026-03-23 天气怎么样"       │   │
│  └─────────────────────────────────────────────────────────┘   │
│      │                                                         │
│      ▼                                                         │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ IntentCacheStore.lookup()                                │   │
│  │ 1. L1: ConcurrentHashMap (热点)                         │   │
│  │ 2. L2: Redis Hash (用户私有缓存)                         │   │
│  │ 3. L3: Redis Vector (语义相似搜索)                       │   │
│  └─────────────────────────────────────────────────────────┘   │
│      │                              │                          │
│      │ 命中                         │ 未命中                   │
│      ▼                              ▼                          │
│  ┌──────────────┐          ┌──────────────────────┐           │
│  │ 直接返回缓存   │          │ HybridIntentRouter   │           │
│  │ 节省AI调用   │          │ AI识别意图            │           │
│  └──────────────┘          └──────────────────────┘           │
│                                   │                            │
│                                   ▼                            │
│                           ┌──────────────────┐                 │
│                           │ IntentCacheStore │                 │
│                           │ .save() 存储结果 │                 │
│                           └──────────────────┘                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 模式切换

| 模式 | 说明 | 触发条件 |
|-----|------|---------|
| AI_FIRST | AI优先识别并学习 | 冷启动、缓存不足50条 |
| CACHE_FIRST | 缓存优先，AI降级 | 缓存数据 > 50条，用户主动开启 |
| RULE_FIRST | 规则优先，AI最后 | 高频简单查询场景 |

---

## 核心组件

### 1. IntentCacheStore（意图缓存存储）

**职责**：管理意图识别结果的存储和查询

```java
public interface IntentCacheStore {

    /**
     * 查询缓存
     * @param normalizedQuery 归一化后的查询
     * @param userId 用户ID
     * @return 缓存结果
     */
    Optional<IntentCacheEntry> lookup(String normalizedQuery, Long userId);

    /**
     * 保存缓存
     * @param entry 缓存条目
     */
    void save(IntentCacheEntry entry);

    /**
     * 批量查询（用于批量学习）
     */
    List<IntentCacheEntry> batchLookup(List<String> queries, Long userId);

    /**
     * 删除用户缓存
     */
    void invalidateUser(Long userId);

    /**
     * 获取缓存统计
     */
    CacheStatistics getStatistics();
}
```

### 2. QueryNormalizer（查询归一化）

**职责**：处理时效性表达和查询标准化

```java
public class QueryNormalizer {

    /**
     * 时序表达归一化
     * "今天" → "2026-03-23"
     * "明天" → "2026-03-24"
     */
    public String normalize(String query) {
        String normalized = query;

        // 1. 日期归一化
        normalized = normalizeDate(normalized);

        // 2. 时间归一化
        normalized = normalizeTime(normalized);

        // 3. 星期归一化
        normalized = normalizeWeekday(normalized);

        return normalized;
    }

    /**
     * 还原归一化（用于展示）
     */
    public String denormalize(String normalized, String original) {
        // 将标准化日期还原为用户友好的表达
        return original;  // 保持原始表达
    }
}
```

### 3. VectorBasedRecognizer（向量识别器）

**职责**：基于向量相似度进行意图识别

```java
public class VectorBasedRecognizer {

    private final EmbeddingService embeddingService;
    private final IntentCacheStore cacheStore;

    /**
     * 向量相似度搜索
     */
    public RecognizerResult recognize(String normalizedQuery) {
        // 1. 获取查询向量
        float[] queryVector = embeddingService.getEmbedding(normalizedQuery);

        // 2. 向量搜索 Top-K
        List<CachedEntry> candidates = cacheStore.vectorSearch(queryVector, topK: 10);

        // 3. 双维度排序（向量相似度 + 意图类型一致性）
        List<ScoredEntry> ranked = hybridRanking(queryVector, candidates);

        // 4. 阈值判断
        if (ranked.get(0).getScore() >= threshold) {
            return RecognizerResult.hit(ranked.get(0));
        }

        return RecognizerResult.miss();
    }
}
```

### 4. HybridIntentCacheRouter（混合缓存路由）

**职责**：协调各层缓存和识别器

```java
public class HybridIntentCacheRouter {

    private final QueryNormalizer normalizer;
    private final ConcurrentHashMap<String, IntentCacheEntry> L1Cache;  // 内存热点
    private final IntentCacheStore L2Cache;  // Redis
    private final VectorBasedRecognizer vectorRecognizer;
    private final CircuitBreaker circuitBreaker;

    public RecognitionResult route(Long userId, String query) {
        String normalized = normalizer.normalize(query);

        // Level 0: 精确匹配（L1内存）
        IntentCacheEntry l1Result = L1Cache.get(buildKey(userId, normalized));
        if (l1Result != null) {
            return RecognitionResult.cacheHit(l1Result, CacheLevel.L1);
        }

        // Level 1: Redis Hash
        Optional<IntentCacheEntry> l2Result = L2Cache.lookup(normalized, userId);
        if (l2Result.isPresent()) {
            // 回填 L1
            L1Cache.put(buildKey(userId, normalized), l2Result.get());
            return RecognitionResult.cacheHit(l2Result.get(), CacheLevel.L2);
        }

        // Level 2: 向量搜索
        if (!circuitBreaker.isOpen()) {
            RecognizerResult vectorResult = vectorRecognizer.recognize(normalized);
            if (vectorResult.isHit()) {
                // 回填 L2
                L2Cache.save(vectorResult.getEntry());
                return RecognitionResult.cacheHit(vectorResult.getEntry(), CacheLevel.L3);
            }
        }

        // Level 3: AI 识别（降级）
        return RecognitionResult.aiRecognitionRequired(normalized);
    }
}
```

---

## 数据模型

### IntentCacheEntry

```java
@Data
@Builder
public class IntentCacheEntry {

    /** 唯一标识 */
    private String id;

    /** 用户ID（0表示公共模板） */
    private Long userId;

    /** 原始用户输入 */
    private String originalQuery;

    /** 归一化后的查询（用于向量存储） */
    private String normalizedQuery;

    /** 向量维度（根据Embedding模型） */
    private float[] vector;

    /** 识别到的意图类型 */
    private IntentType intentType;

    /** 提取的参数（JSON格式） */
    private Map<String, Object> extractedParams;

    /** 置信度 */
    private double confidence;

    /** 识别模型版本 */
    private String modelVersion;

    /** 识别层来源（rule/lightweight_ai/full_ai） */
    private String recognizedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 过期时间 */
    private LocalDateTime expiresAt;

    /** 使用次数 */
    private AtomicInteger hitCount;

    /** 最后命中时间 */
    private LocalDateTime lastHitAt;
}
```

### CacheStatistics

```java
@Data
public class CacheStatistics {

    /** L1 命中率 */
    private double l1HitRate;

    /** L2 命中率 */
    private double l2HitRate;

    /** L3 命中率 */
    private double l3HitRate;

    /** 总体命中率 */
    private double totalHitRate;

    /** 总缓存条目数 */
    private long totalEntries;

    /** 用户私有条目数 */
    private long userEntries;

    /** 公共模板条目数 */
    private long publicEntries;

    /** AI 调用节省次数 */
    private long aiCallsSaved;

    /** 预估成本节省（元） */
    private BigDecimal costSaved;
}
```

---

## 查询归一化

### 时序表达处理

```java
public class TemporalExpressionNormalizer {

    private static final Map<Pattern, Function<String, String>> DATE_PATTERNS;

    static {
        DATE_PATTERNS = new LinkedHashMap<>();

        // 相对日期（按添加顺序匹配）
        DATE_PATTERNS.put(Pattern.compile("大后天"), d -> d.plusDays(3).toString());
        DATE_PATTERNS.put(Pattern.compile("后天"), d -> d.plusDays(2).toString());
        DATE_PATTERNS.put(Pattern.compile("明天"), d -> d.plusDays(1).toString());
        DATE_PATTERNS.put(Pattern.compile("今天"), d -> d.toString());
        DATE_PATTERNS.put(Pattern.compile("昨天"), d -> d.minusDays(1).toString());
        DATE_PATTERNS.put(Pattern.compile("前天"), d -> d.minusDays(2).toString());
        DATE_PATTERNS.put(Pattern.compile("大前天"), d -> d.minusDays(3).toString());

        // 星期相关（需要运行时计算）
        // "周一" ~ "周日"
        // "本周"、"下周"、"上周"
    }

    public String normalizeDate(String query) {
        LocalDate today = LocalDate.now();
        String result = query;

        for (Map.Entry<Pattern, Function<String, String>> entry : DATE_PATTERNS.entrySet()) {
            Matcher matcher = entry.getKey().matcher(result);
            if (matcher.find()) {
                result = matcher.replaceAll(entry.getValue().apply(today));
            }
        }

        // 星期处理
        result = normalizeWeekday(result, today);

        return result;
    }

    private String normalizeWeekday(String query, LocalDate today) {
        String[] weekdays = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};

        for (int i = 0; i < weekdays.length; i++) {
            if (query.contains("本周" + weekdays[i])) {
                LocalDate target = today.with(java.time.DayOfWeek.of(i + 1));
                if (target.isBefore(today)) {
                    target = target.plusWeeks(1);
                }
                return query.replace("本周" + weekdays[i], target.toString());
            }
            if (query.contains("下周" + weekdays[i])) {
                LocalDate target = today.with(java.time.DayOfWeek.of(i + 1)).plusWeeks(1);
                return query.replace("下周" + weekdays[i], target.toString());
            }
        }

        return query;
    }
}
```

### 归一化示例

| 原始输入 | 归一化结果 | 说明 |
|---------|-----------|-----|
| 北京今天天气怎么样 | 北京 2026-03-23 天气怎么样 | 今天 → 日期 |
| 明天上海温度 | 2026-03-24 上海 温度 | 明天 → 日期 |
| 帮我查下周三的日程 | 帮我查下 2026-03-25 的日程 | 周三 → 具体日期 |
| 下周一的会议 | 下周周一 → 计算后的周一 | 下周周一 |

---

## 缓存策略

### 分层缓存设计

```
┌─────────────────────────────────────────────────────────────┐
│                         请求入口                            │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ L1: ConcurrentHashMap<String, IntentCacheEntry>              │
│ - 热点意图（最近100次命中）                                    │
│ - 延迟: < 1ms                                               │
│ - TTL: 5分钟                                                 │
│ - 最大条目: 500                                               │
└─────────────────────────────────────────────────────────────┘
                            │ 未命中
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ L2: Redis Hash                                               │
│ Key: intent_cache:user:{userId}:{intentType}:{paramHash}    │
│ - 全量缓存（按用户分片）                                       │
│ - 延迟: 1-5ms                                                │
│ - TTL: 7天                                                   │
└─────────────────────────────────────────────────────────────┘
                            │ 未命中
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ L3: Redis Search + Vector (HNSW Index)                      │
│ - 语义相似匹配                                               │
│ - 延迟: 10-50ms                                              │
│ - 容量: 百万级                                               │
└─────────────────────────────────────────────────────────────┘
                            │ 未命中
                            ▼
                      AI识别（降级）
```

### 缓存 Key 设计

```
# L1/L2 Key 格式
intent_cache:user:{userId}:{intentType}:{paramFingerprint}

# L3 向量索引 Key
intent_vector:user:{userId}:{entryId}

# 公共模板（跨用户共享）
intent_cache:public:{intentType}:{queryFingerprint}
intent_vector:public:{entryId}

# 参数指纹生成
paramFingerprint = MD5(sorted(params.json()))
  - "city=北京&date=2026-03-23" → "a1b2c3d4"
  - "city=上海&date=2026-03-23" → "e5f6g7h8"  # 参数不同，指纹不同
```

### 冷启动策略

```java
public class ColdStartStrategy {

    /** 最小样本数 */
    private static final int MIN_SAMPLES = 50;

    /** 公共模板列表 */
    private static final List<IntentTemplate> PUBLIC_TEMPLATES = List.of(
        IntentTemplate.of("北京天气怎么样", IntentType.WEATHER_QUERY, Map.of("city", "北京")),
        IntentTemplate.of("上海天气怎么样", IntentType.WEATHER_QUERY, Map.of("city", "上海")),
        IntentTemplate.of("今天天气", IntentType.WEATHER_QUERY, Map.of("dateType", "今天")),
        IntentTemplate.of("明天天气", IntentType.WEATHER_QUERY, Map.of("dateType", "明天")),
        IntentTemplate.of("我的待办", IntentType.TODO_QUERY, Map.of()),
        IntentTemplate.of("待办事项", IntentType.TODO_QUERY, Map.of()),
        IntentTemplate.of("今天日程", IntentType.CALENDAR_QUERY, Map.of("dateType", "今天")),
        IntentTemplate.of("明天日程", IntentType.CALENDAR_QUERY, Map.of("dateType", "明天")),
        IntentTemplate.of("怎么去天安门", IntentType.ROUTE_QUERY, Map.of("destination", "天安门")),
        IntentTemplate.of("你好", IntentType.SMALL_TALK, Map.of()),
        IntentTemplate.of("在吗", IntentType.SMALL_TALK, Map.of()),
        // ... 可预置 50-100 条
    );

    /**
     * 预热公共模板
     */
    @PostConstruct
    public void preloadPublicTemplates() {
        for (var template : PUBLIC_TEMPLATES) {
            IntentCacheEntry entry = IntentCacheEntry.builder()
                .userId(0L)  // 公共
                .originalQuery(template.getQuery())
                .normalizedQuery(normalizer.normalize(template.getQuery()))
                .intentType(template.getIntentType())
                .extractedParams(template.getParams())
                .confidence(0.95)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

            entry.setVector(embeddingService.getEmbedding(entry.getNormalizedQuery()));
            cacheStore.savePublic(entry);
        }
    }
}
```

---

## 熔断与降级

### 熔断器配置

```java
public class IntentCacheCircuitBreaker {

    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile State state = State.CLOSED;
    private volatile LocalDateTime lastFailureTime;

    private static final int FAILURE_THRESHOLD = 10;
    private static final Duration HALF_OPEN_INTERVAL = Duration.ofMinutes(1);
    private static final int SUCCESS_THRESHOLD = 5;

    public enum State {
        CLOSED,     // 正常
        OPEN,       // 熔断
        HALF_OPEN   // 半开
    }

    public boolean allowRequest() {
        switch (state) {
            case CLOSED:
                return true;
            case OPEN:
                if (Duration.between(lastFailureTime, LocalDateTime.now())
                    .compareTo(HALF_OPEN_INTERVAL) > 0) {
                    state = State.HALF_OPEN;
                    return true;
                }
                return false;
            case HALF_OPEN:
                return true;
            default:
                return true;
        }
    }

    public void recordFailure() {
        failureCount.incrementAndGet();
        lastFailureTime = LocalDateTime.now();

        if (failureCount.get() >= FAILURE_THRESHOLD) {
            state = State.OPEN;
            log.warn("意图缓存熔断打开，失败数: {}", failureCount.get());
        }
    }

    public void recordSuccess() {
        if (state == State.HALF_OPEN) {
            if (successCount.incrementAndGet() >= SUCCESS_THRESHOLD) {
                state = State.CLOSED;
                failureCount.set(0);
                log.info("意图缓存熔断关闭");
            }
        } else {
            failureCount.set(0);
        }
    }
}
```

### 分级降级策略

```
┌──────────────────────────────────────────────────────┐
│ Priority 1: L1 内存缓存 (最快)                       │
│ └─→ 精确 Key 匹配，< 1ms                            │
├──────────────────────────────────────────────────────┤
│ Priority 2: L2 Redis Hash                          │
│ └─→ 用户私有缓存，1-5ms                              │
├──────────────────────────────────────────────────────┤
│ Priority 3: L3 向量搜索                              │
│ └─→ 语义相似度匹配，10-50ms                          │
│     └─→ 熔断打开时跳过此层                           │
├──────────────────────────────────────────────────────┤
│ Priority 4: Rule-Based (规则)                        │
│ └─→ 纯本地计算，零成本                               │
│     └─→ 高置信度时直接返回                           │
├──────────────────────────────────────────────────────┤
│ Priority 5: AI Recognition                           │
│ └─→ 最终降级兜底                                    │
└──────────────────────────────────────────────────────┘
```

---

## 配置说明

### application.yml 新增配置

```yaml
# Intent Recognition Cache Configuration
intent-recognition:
  # 模式: ai-first / cache-first / rule-first
  mode: ai-first

  # 缓存配置
  cache:
    # L1 内存缓存
    l1:
      enabled: true
      max-size: 500
      ttl-minutes: 5
    # L2 Redis 缓存
    l2:
      enabled: true
      ttl-days: 7
    # L3 向量缓存
    l3:
      enabled: true
      index-type: hnsw  # hnsw / ivf
      top-k: 10
      similarity-threshold: 0.92

  # 归一化配置
  normalization:
    # 时序表达归一化
    temporal-enabled: true
    # 口语化归一化（暂不支持）
    colloquial-enabled: false

  # 冷启动配置
  cold-start:
    # 最小样本数
    min-samples: 50
    # 公共模板预热
    preload-enabled: true

  # 熔断配置
  circuit-breaker:
    enabled: true
    failure-threshold: 10
    half-open-interval-minutes: 1

  # Embedding 服务配置
  embedding:
    # 提供商: minimax / kimi / openai / local
    provider: ${INTENT_EMBEDDING_PROVIDER:minimax}
    # 模型名
    model: ${INTENT_EMBEDDING_MODEL:text-embedding-3}
    # 向量维度
    dimension: 1024
    # API Key
    api-key: ${INTENT_EMBEDDING_API_KEY:}
    # 批量大小
    batch-size: 100
```

### Embedding 服务接口

```java
public interface EmbeddingService {

    /**
     * 获取单个查询的向量
     */
    float[] getEmbedding(String text);

    /**
     * 批量获取向量
     */
    List<float[]> batchGetEmbedding(List<String> texts);

    /**
     * 计算余弦相似度
     */
    default double cosineSimilarity(float[] a, float[] b) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
```

---

## 实施计划

### Phase 1: 基础设施（1-2天）

- [ ] 创建 IntentCacheEntry 数据模型
- [ ] 创建 IntentCacheStore 接口
- [ ] 实现 Redis Hash 缓存存储
- [ ] 配置 Embedding 服务

### Phase 2: 核心功能（2-3天）

- [ ] 实现 QueryNormalizer（时序归一化）
- [ ] 实现 VectorBasedRecognizer
- [ ] 实现 HybridIntentCacheRouter
- [ ] 集成到现有 HybridIntentRouter

### Phase 3: 高级功能（1-2天）

- [ ] 实现 L1 内存缓存
- [ ] 实现熔断降级机制
- [ ] 实现冷启动预热
- [ ] 添加统计指标

### Phase 4: 优化与测试（1天）

- [ ] 性能测试
- [ ] 缓存命中率优化
- [ ] 文档更新

---

## 风险与缓解

| 风险 | 影响 | 缓解措施 |
|-----|------|---------|
| Embedding 服务不可用 | 向量搜索完全失败 | 降级到 Rule-Based，熔断器保护 |
| 向量维度不匹配 | 相似度计算错误 | 启动时校验，不匹配则报错 |
| 冷启动数据不足 | 缓存命中率低 | 公共模板预热 + AI-First 模式 |
| 用户隐私泄露 | 缓存数据被滥用 | 按用户隔离，敏感数据脱敏 |

---

## 预估收益

| 指标 | 优化前 | 优化后（预估） |
|-----|-------|---------------|
| AI 调用次数 | 100% | 减少 40-60% |
| 意图识别延迟 | 500ms | < 100ms（缓存命中） |
| 成本 | ¥0.02/次 | ¥0.008-0.012/次 |
| 缓存命中率 | 0% | 40-60% |

---

## 相关文档

- [design.md](./design.md) - AI Agent 二次优化设计文档
- [requirements.md](./requirements.md) - 需求文档
- [tasks.md](./tasks.md) - 任务文档

---

**文档版本**: v1.0
**最后更新**: 2026-03-23
**维护者**: MrsHudson Team
