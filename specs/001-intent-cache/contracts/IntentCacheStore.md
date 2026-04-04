# 契约：IntentCacheStore

**对齐**: AI_ARCHITECTURE.md v3.5
**包**: `com.mrshudson.optim.intent.cache`

---

## 概述

`IntentCacheStore` 是三级意图识别缓存系统的主门面，如 AI_ARCHITECTURE.md 所规定。它提供对 L1 (ConcurrentHashMap)、L2 (Redis Hash) 和 L3 (Redis Vector) 缓存层的统一访问，支持自动级联查找和回填。

---

## 接口

```java
public interface IntentCacheStore {

    /**
     * 在缓存中查找意图 (L1 → L2 → L3 级联)
     * @param userInput 原始用户查询
     * @param userId 用户 ID，用于缓存隔离
     * @return 缓存的意图结果，未命中返回 null
     */
    IntentResult lookup(String userInput, Long userId);

    /**
     * 在所有缓存层中存储意图结果
     * @param userInput 原始用户查询
     * @param userId 用户 ID
     * @param result 要缓存的意图识别结果
     */
    void store(String userInput, Long userId, IntentResult result);

    /**
     * 使某用户的所有缓存条目失效
     * @param userId 用户 ID
     */
    void invalidate(Long userId);

    /**
     * 获取缓存统计信息
     * @return 所有层的统计信息
     */
    CacheStatistics getStatistics();
}
```

---

## 级联查找流程

```
lookup(userInput, userId)
    │
    ▼
QueryNormalizer.normalize(userInput)
    │
    ▼
fingerprint = MD5(normalizedInput)
    │
    ▼
┌─────────────────────────────────────────┐
│ L1: ConcurrentHashMap 查找              │
│ 键: "{userId}:{fingerprint}"            │
│ TTL: 5 分钟                             │
├─────────────────────────────────────────┤
│ 命中 → 返回 IntentResult                │
│ 未命中 → 继续到 L2                      │
└─────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────┐
│ L2: Redis Hash 查找                     │
│ 键: "intent:L2:{userId}:{fingerprint}"  │
│ TTL: 7 天                               │
├─────────────────────────────────────────┤
│ 命中 → 回填到 L1 → 返回                 │
│ 未命中 → 继续到 L3                      │
└─────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────┐
│ L3: Redis Vector 相似度搜索             │
│ 算法: HNSW                              │
│ 阈值: 0.92 (可配置)                     │
│ TTL: 30 天                              │
├─────────────────────────────────────────┤
│ 命中 → 回填到 L2, L1 → 返回             │
│ 未命中 → 返回 null                      │
└─────────────────────────────────────────┘
```

---

## 职责

- 编排 L1 → L2 → L3 → IntentRouter 查找级联
- 缓存命中时自动回填 (L3→L2→L1)
- 集成熔断器实现弹性
- 通过 CacheMetrics 收集指标

---

## 错误处理

- 缓存未命中: 返回 null (调用方继续执行 IntentRouter)
- 缓存服务故障: 记录错误，降级到 IntentRouter
- 熔断器开启: 跳过缓存，直接访问 IntentRouter

---

## 实现说明

本接口将之前的 `IntentRecognitionCacheService` 命名替换为与 AI_ARCHITECTURE.md 的 `IntentCacheStore` 术语对齐。

