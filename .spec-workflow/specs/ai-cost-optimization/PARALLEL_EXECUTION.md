# AI调用成本优化 - 多终端并行执行指南

> 本文档指导如何在多个终端并行执行任务以提高实施效率

## 📊 执行概览

- **总任务数**: 43个
- **执行波次**: 6个Wave
- **预计并行终端**: 2-4个（根据团队规模）
- **单Wave最短完成时间**: 约2-3小时（4终端并行）

## 🚀 快速开始

### 终端分配建议

| 团队规模 | 终端数 | 分配方案 |
|---------|-------|---------|
| 1人 | 1终端 | 按Wave顺序执行 |
| 2人 | 2终端 | 终端A负责1-3-5，终端B负责2-4-6 |
| 3人 | 3终端 | 每Wave分配3个终端，轮询执行 |
| 4人+ | 4终端 | 每Wave最大化并行 |

---

## 📋 详细执行计划

### Wave 1: 基础设施搭建 ⏱️ 预计2小时

**目标**: 建立最基础配置，所有后续模块依赖

| 终端 | 任务 | 文件 | 依赖 |
|-----|------|------|------|
| A | 1.1 OptimProperties | `optim/config/OptimProperties.java` | 无 |
| A | 1.2 OptimException | `optim/exception/OptimException.java` | 1.1 |
| A | 1.3 OptimUtils | `optim/util/OptimUtils.java` | 1.1 |
| B | 2.1 AiCostRecord | `domain/entity/AiCostRecord.java` | 无 |
| C | 4.1 IntentType | `optim/intent/IntentType.java` | 无 |
| C | 4.2 IntentHandler | `optim/intent/IntentHandler.java` | 4.1 |

**Wave 1 完成后检查点**:
- [ ] OptimProperties 能被 Spring 正确加载
- [ ] AiCostRecord 能被 MyBatis Plus 映射
- [ ] IntentType 枚举定义完整

---

### Wave 2: 基础实现 ⏱️ 预计3小时

**目标**: 实现基础服务，建立模块间协作基础

| 终端 | 任务 | 文件 | 依赖 |
|-----|------|------|------|
| A | 1.4 VectorStore | `optim/cache/VectorStore.java` | 无 |
| A | 1.5 EmbeddingService | `optim/cache/EmbeddingService.java` | 无 |
| A | 1.6 VectorStoreConfig | `optim/config/VectorStoreConfig.java` | 1.4, 1.5 |
| B | 2.2 CostMonitorService | `optim/monitor/CostMonitorService.java` | 2.1 |
| C | 3.1 ToolCacheManager | `optim/cache/ToolCacheManager.java` | 1.1 |
| D | 4.3 WeatherHandler | `optim/intent/handler/WeatherIntentHandler.java` | 4.2 |
| D | 4.6 SmallTalkHandler | `optim/intent/handler/SmallTalkHandler.java` | 4.2 |

**Wave 2 完成后检查点**:
- [ ] VectorStore Bean 按需创建
- [ ] CostMonitorService 能记录AI调用
- [ ] ToolCacheManager 缓存能正常存取
- [ ] WeatherHandler 能识别天气查询

---

### Wave 3: 核心功能 ⏱️ 预计4小时

**目标**: 实现各模块核心功能

| 终端 | 任务 | 文件 | 依赖 |
|-----|------|------|------|
| A | 2.3 CostStatsController | `controller/CostStatsController.java` | 2.2 |
| A | 2.4 定时任务 | `CostMonitorScheduler.java` | 2.2 |
| B | 3.2 WeatherService缓存 | 修改 `WeatherServiceImpl.java` | 3.1 |
| B | 3.3 DataChangeListener | `optim/cache/listener/DataChangeListener.java` | 3.1 |
| B | 3.4 发布变更事件 | 修改 `CalendarServiceImpl.java`, `TodoServiceImpl.java` | 3.3 |
| C | 4.4 CalendarHandler | `optim/intent/handler/CalendarIntentHandler.java` | 4.2 |
| C | 4.5 TodoHandler | `optim/intent/handler/TodoIntentHandler.java` | 4.2 |
| C | 4.5b RouteHandler | `optim/intent/handler/RouteIntentHandler.java` | 4.2 |
| C | 4.8 ParameterExtractor | `optim/intent/extract/ParameterExtractor.java` | 4.1 |
| D | 5.1 CacheEntry | `optim/cache/CacheEntry.java` | 无 |
| D | 5.2 KeywordEmbedding | `optim/cache/impl/KeywordEmbeddingService.java` | 1.5 |

**Wave 3 完成后检查点**:
- [ ] 成本统计API能返回数据
- [ ] 天气缓存重复查询命中
- [ ] 数据变更后缓存被清除
- [ ] 日历/待办/路线处理器能正确路由

---

### Wave 4: 高级功能 ⏱️ 预计5小时

**目标**: 实现意图路由三层混合模式和语义缓存

| 终端 | 任务 | 文件 | 依赖 |
|-----|------|------|------|
| A | 4.7 IntentRouter | `optim/intent/IntentRouter.java` | 4.1-4.6, 4.5b |
| A | 4.9 RuleBasedExtractor | `optim/intent/extract/RuleBasedExtractor.java` | 4.8 |
| A | 4.12 RouterFactory | `optim/intent/IntentRouterFactory.java` | 4.7 |
| B | 4.10 LightweightAiExtractor | `optim/intent/extract/LightweightAiExtractor.java` | 4.8 |
| B | 4.11 HybridIntentRouter | `optim/intent/impl/HybridIntentRouter.java` | 4.9, 4.10 |
| C | 5.3 RedisVectorStore | `optim/cache/impl/RedisVectorStore.java` | 1.4, 1.5 |
| C | 5.6 SemanticCacheService | `optim/cache/SemanticCacheService.java` | 5.1, 5.2, 5.3 |

**Wave 4 完成后检查点**:
- [ ] 意图路由能正确处理各类查询
- [ ] 规则提取覆盖80%以上常见表达
- [ ] 轻量AI提取能处理模糊表达
- [ ] 语义缓存相似问题能命中

---

### Wave 5: 完善和优化 ⏱️ 预计4小时

**目标**: 完善语义缓存、对话压缩和系统优化

| 终端 | 任务 | 文件 | 依赖 |
|-----|------|------|------|
| A | 5.4 ChromaVectorStore | `optim/cache/impl/ChromaVectorStore.java` (可选) | 1.4, 5.5 |
| A | 5.5 ChromaClient | `optim/cache/chroma/ChromaClient.java` (可选) | 无 |
| A | 5.7 CacheCleanup | `optim/cache/scheduler/CacheCleanupScheduler.java` | 5.6 |
| B | 6.1 CompressionConfig | `optim/compress/CompressionConfig.java` | 无 |
| B | 6.2 ConversationSummarizer | `optim/compress/ConversationSummarizer.java` | 6.1 |
| B | 6.3 摘要提示词 | 在 6.2 中添加 | 无 |
| C | 7.3 提示词优化 | 修改 `ChatServiceImpl.buildSystemPrompt` | 无 |
| C | 7.4 KimiClient参数 | 修改 `KimiClient.java` | 1.1 |
| C | 7.5 标题生成优化 | 修改 `ChatServiceImpl.generateConversationTitle` | 无 |

**Wave 5 完成后检查点**:
- [ ] Chroma集成可用（可选）
- [ ] 对话压缩能正确触发
- [ ] 提示词精简后token减少30%+
- [ ] 标题生成对简单查询零AI调用

---

### Wave 6: 最终集成 ⏱️ 预计3小时

**目标**: 整合所有优化层，完成集成测试

| 终端 | 任务 | 文件 | 依赖 |
|-----|------|------|------|
| A | 7.1 OptimChatService | `optim/wrapper/OptimChatService.java` | 全部 |
| A | 7.2 AIServiceFactory | 修改 `AIServiceFactory.java` | 7.1 |
| B | 7.6 CacheManageController | `controller/CacheManageController.java` | 5.6, 3.1 |
| B | 7.7 集成测试 | `test/optim/OptimIntegrationTest.java` | 全部 |

**Wave 6 完成后检查点**:
- [ ] 所有优化层按顺序执行
- [ ] Controller无感知使用优化服务
- [ ] 集成测试全部通过
- [ ] 达到预期成本优化效果

---

## 🔧 终端执行指南

### 单个终端执行脚本模板

```bash
# 进入项目目录
cd /Users/huangzhuangcan/Documents/GitHub/John-1010-cmd/Mrs-Hudson/mrshudson-backend

# Wave X - 终端Y
echo "=== 开始执行 Wave X 任务 ==="

# 任务A.B - 任务描述
echo "执行任务A.B..."
# 具体实现代码...

# 编译检查
mvn clean compile -q
if [ $? -eq 0 ]; then
    echo "✅ Wave X 编译通过"
else
    echo "❌ Wave X 编译失败"
    exit 1
fi

# 单元测试（如有）
mvn test -Dtest=相关测试类 -q
echo "=== Wave X 完成 ==="
```

### 多终端协作流程

```
终端A          终端B          终端C          终端D
 |              |              |              |
├─ Wave 1 ─────┼──────────────┼──────────────┤
 |              |              |              |
 |  同步点: 所有人完成后才能进入Wave 2
 |              |              |              |
├─ Wave 2 ─────┼──────────────┼──────────────┤
 |              |              |              |
 |  同步点: 所有人完成后才能进入Wave 3
 |              |              |              |
└─ Wave 3-6 ───┴──────────────┴──────────────┘
```

---

## 📈 进度跟踪

### 团队每日站会检查项

1. **Wave完成情况**: 当前Wave是否全部完成？
2. **阻塞问题**: 是否有任务被阻塞需要协助？
3. **编译状态**: 所有代码是否能编译通过？
4. **测试状态**: 新增功能是否有单元测试覆盖？

### 任务状态标记

在每个Wave的任务文档中标记状态：
```
- [-] 任务编号 - 进行中
- [x] 任务编号 - 已完成
- [ ] 任务编号 - 待开始
- [!] 任务编号 - 有阻塞问题
```

---

## ⚡ 加速技巧

### 1. 代码模板复用

同类任务使用统一模板：
- Service实现类模板
- Controller模板
- Handler模板

### 2. 接口先行

定义好接口后，实现可以并行：
```java
// 先定义接口（终端A）
public interface IntentRouter { ... }

// 再并行实现（终端B/C/D）
public class IntentRouterImpl implements IntentRouter { ... }
public class HybridIntentRouter implements IntentRouter { ... }
```

### 3. Mock依赖

未完成依赖可用Mock代替：
```java
// 当EmbeddingService未完成时
@MockBean
private EmbeddingService embeddingService;

when(embeddingService.embed("测试")).thenReturn(new float[]{0.1f, 0.2f});
```

### 4. 配置开关

所有优化层支持配置开关，可独立验证：
```yaml
optim:
  semantic-cache:
    enabled: true  # 开发时设为false跳过
  intent-router:
    enabled: true
```

---

## 🐛 常见问题处理

### 问题1: 编译依赖冲突
**解决**: 统一在Wave 1完成后执行 `mvn clean compile` 确保基础代码正确

### 问题2: 接口变更
**解决**: Wave N的接口变更需同步给Wave N+1的负责人

### 问题3: 资源竞争（如修改同一文件）
**解决**: 提前规划文件分配，避免多人修改同一文件

### 问题4: 测试数据依赖
**解决**: 创建共享测试数据文件 `src/test/resources/test-data.sql`

---

## ✅ 验收标准

### Wave级别验收

| Wave | 验收标准 |
|-----|---------|
| Wave 1 | 配置加载成功 + 实体映射正确 + 枚举可用 |
| Wave 2 | 服务Bean创建成功 + 缓存读写正常 + 处理器识别正确 |
| Wave 3 | API响应正确 + 缓存命中/清除正常 + 处理器覆盖主要场景 |
| Wave 4 | 路由正确 + 分层降级正常 + 语义缓存匹配相似问题 |
| Wave 5 | 压缩触发正确 + 提示词精简验证 + 标题零AI生成 |
| Wave 6 | 集成测试通过 + 达到预期成本优化效果 |

### 最终验收指标

| 指标 | 目标值 |
|-----|-------|
| 工具查询（规则层） | 85%查询零AI调用 |
| 工具查询（轻量AI层） | 额外10%仅需1轮轻量AI |
| 工具查询（完整AI层） | 仅5%需要2轮AI |
| 语义缓存命中率 | 20-30% |
| 对话压缩节省token | 长对话减少50% |
| **整体AI调用减少** | **75%+** |

---

## 📝 版本历史

| 版本 | 日期 | 说明 |
|-----|------|------|
| v1.0 | 2026-03-04 | 初始版本，6 Wave并行执行计划 |

---

*本文档与 tasks.md 配合使用，执行任务时请参考 tasks.md 中的详细_Prompt说明*
