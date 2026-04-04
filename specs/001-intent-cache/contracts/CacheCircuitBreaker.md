# 契约：CacheCircuitBreaker

**对齐**: AI_ARCHITECTURE.md v3.5
**包**: `com.mrshudson.optim.intent.cache`

---

## 概述

`CacheCircuitBreaker` 使用 Resilience4j 为意图缓存系统提供弹性保护。当缓存服务 (Redis) 不可用时，它保护系统免受级联故障影响。

---

## 接口

```java
public interface CacheCircuitBreaker {

    /**
     * 使用熔断器保护执行操作
     * @param operation 要执行的缓存操作
     * @param fallback 失败时的降级操作
     * @return 操作结果或降级结果
     */
    <T> T execute(Supplier<T> operation, Supplier<T> fallback);

    /**
     * 获取当前熔断状态
     */
    State getState();

    enum State { CLOSED, OPEN, HALF_OPEN }
}
```

---

## 配置

| 参数 | 值 | 说明 |
|------|-----|------|
| **故障阈值** | 10 | 开启前的连续失败次数 |
| **开启状态等待时间** | 60秒 | 过渡到 HALF_OPEN 前的等待时间 |
| **半开状态成功阈值** | 3 | 关闭熔断所需的成功调用次数 |
| **滑动窗口大小** | 100 | 故障率计算的窗口大小 |

---

## 行为

### CLOSED 状态
- 正常运行
- 计数失败次数
- 如失败次数超过阈值 → 转换到 OPEN

### OPEN 状态
- 跳过缓存操作
- 使用降级方案 (直接调用 IntentRouter)
- 等待超时 → 转换到 HALF_OPEN

### HALF_OPEN 状态
- 允许有限调用测试恢复
- 如达到成功阈值 → 转换到 CLOSED
- 如任何失败 → 转换到 OPEN

---

## 与 IntentCacheStore 集成

```
IntentCacheStore.lookup()
    │
    ▼
CacheCircuitBreaker.execute(
    operation: () -> { L1/L2/L3 查找 },
    fallback: () -> { return null }  // 让 IntentRouter 处理
)
```

---

## 指标

- 熔断器状态转换
- 按异常类型的故障计数
- 半开状态的成功/失败率

