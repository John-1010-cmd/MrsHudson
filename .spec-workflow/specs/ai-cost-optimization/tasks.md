# AI调用成本优化 - 任务文档

## 任务总览

本规格包含7个主要功能模块，共**43个实施任务**。建议按依赖关系分组并行实施，每个模块可独立部署验证。

**任务统计:**
- 模块1 (基础设施): 6个任务
- 模块2 (成本监控): 4个任务
- 模块3 (工具缓存): 4个任务
- 模块4 (意图路由): **12个任务**（含三层混合模式）
- 模块5 (语义缓存): 7个任务（含向量数据库）
- 模块6 (对话压缩): 3个任务
- 模块7 (集成): 7个任务

**总计: 43个任务**

---

## 模块1: 基础设施与配置

- [ ] 1.1 创建优化模块基础包结构和配置类
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/config/OptimProperties.java`
  - 创建配置属性类，包含各优化层的开关和参数
  - 参考现有`AIProperties`模式，支持application.yml配置
  - _Leverage: `AIProperties.java`, `application.yml`
  - _Requirements: 所有优化模块的配置需求
  - _Prompt: 角色：Java配置专家 | 任务：创建OptimProperties配置类，支持语义缓存、意图路由、对话压缩、工具缓存、成本监控的开关和参数配置 | 限制：使用Lombok@Data，提供默认值，支持@ConfigurationProperties | 成功：配置类能被Spring正确加载，所有配置项有默认值

- [ ] 1.2 创建优化层核心异常类
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/exception/OptimException.java`
  - 创建优化层专用异常体系，支持降级处理
  - _Leverage: 现有全局异常处理`GlobalExceptionHandler`
  - _Requirements: 错误处理需求
  - _Prompt: 角色：Java异常处理专家 | 任务：创建OptimException及其子类（CacheException, IntentException等），支持异常降级标记 | 限制：继承RuntimeException，提供错误码机制 | 成功：异常类能被全局异常处理器捕获

- [ ] 1.3 创建优化层工具类（相似度计算、Token估算）
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/util/OptimUtils.java`
  - 实现余弦相似度计算、简单token估算（按字符数）
  - _Leverage: 无（纯数学计算）
  - _Requirements: 语义缓存、对话压缩需求
  - _Prompt: 角色：算法工程师 | 任务：实现向量相似度计算（余弦相似度）、简单token估算（中文2字符=1token，英文4字符=1token） | 限制：纯静态方法，无状态，线程安全 | 成功：单元测试通过，相似度计算精度正确

- [ ] 1.4 创建向量存储通用接口
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/cache/VectorStore.java`
  - 定义向量存储抽象接口，支持多种后端实现
  - _Leverage: CacheEntry, CacheMetadata
  - _Requirements: Requirement 1
  - _Prompt: 角色：Java接口设计专家 | 任务：创建VectorStore接口，定义store、search、delete、cleanup、getStats方法 | 限制：接口设计通用，支持Redis、Chroma、Milvus多种实现 | 成功：接口定义清晰，可扩展

- [ ] 1.5 创建文本嵌入服务接口
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/cache/EmbeddingService.java`
  - 定义文本向量化接口
  - _Leverage: 无
  - _Requirements: Requirement 1
  - _Prompt: 角色：Java接口设计专家 | 任务：创建EmbeddingService接口，定义embed和embedBatch方法 | 限制：支持同步和批量嵌入 | 成功：接口可支持多种嵌入实现（关键词/API/本地模型）

- [ ] 1.6 创建向量数据库配置类
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/config/VectorStoreConfig.java`
  - 配置不同向量存储类型的Bean创建
  - _Leverage: OptimProperties, VectorStore, EmbeddingService
  - _Requirements: Requirement 1
  - _Prompt: 角色：Spring配置专家 | 任务：创建VectorStoreConfig，根据vector.store.type配置创建对应的VectorStore实现Bean | 限制：使用@ConditionalOnProperty，支持redis/chroma/milvus/none | 成功：配置正确，Bean按需创建

---

## 模块2: 成本监控服务

- [ ] 2.1 创建成本记录实体和Mapper
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/domain/entity/AiCostRecord.java`
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/mapper/AiCostRecordMapper.java`
  - 创建成本记录数据库表对应的实体类和MyBatis Plus Mapper
  - _Leverage: `ChatMessage.java`, `ChatMessageMapper.java`
  - _Requirements: Requirement 7
  - _Prompt: 角色：MyBatis Plus开发者 | 任务：创建AiCostRecord实体（字段：id, userId, conversationId, inputTokens, outputTokens, cost, cacheHit, model, createdAt）和对应Mapper | 限制：使用@TableName注解，继承BaseMapper | 成功：实体类能被MyBatis Plus正确映射

- [ ] 2.2 创建成本监控服务接口和实现
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/monitor/CostMonitorService.java`
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/monitor/impl/CostMonitorServiceImpl.java`
  - 实现AI调用记录、缓存命中记录、统计查询、告警检查
  - _Leverage: `AiCostRecordMapper`, `RedisTemplate`
  - _Requirements: Requirement 7
  - _Prompt: 角色：Java服务开发者 | 任务：实现CostMonitorService，包含recordAiCall、recordCacheHit、getStatistics、checkAndAlert方法 | 限制：使用@Service，异步记录（@Async），告警阈值从配置读取 | 成功：所有方法正常可用，统计数据准确

- [ ] 2.3 创建成本统计Controller
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/controller/CostStatsController.java`
  - 提供成本查询API接口（管理员权限）
  - _Leverage: `CostMonitorService`, `SecurityConfig`
  - _Requirements: Requirement 7
  - _Prompt: 角色：Spring Boot开发者 | 任务：创建CostStatsController，提供GET /api/admin/cost/stats和GET /api/admin/cost/user/{userId}接口 | 限制：需要管理员权限，返回统一Result包装 | 成功：接口能正确返回成本统计数据

- [ ] 2.4 添加成本监控定时任务
  - 文件: 修改`MrshudsonApplication.java`或新建`CostMonitorScheduler.java`
  - 每小时统计成本，每日检查告警阈值
  - _Leverage: `@Scheduled`, `CostMonitorService`
  - _Requirements: Requirement 7
  - _Prompt: 角色：Spring调度专家 | 任务：创建定时任务，每小时执行成本统计，每日0点检查昨日成本是否超过阈值 | 限制：使用@Scheduled，异常时记录日志不影响主流程 | 成功：定时任务正常执行，统计数据写入日志

---

## 模块3: 工具结果缓存

- [ ] 3.1 创建工具缓存管理器
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/cache/ToolCacheManager.java`
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/cache/impl/ToolCacheManagerImpl.java`
  - 实现工具结果缓存的存取、过期、清除逻辑
  - _Leverage: `RedisTemplate`, `OptimProperties`
  - _Requirements: Requirement 4
  - _Prompt: 角色：缓存开发专家 | 任务：实现ToolCacheManager，支持get/put/invalidate方法，key格式：tool:{name}:{paramsHash}，不同工具不同TTL | 限制：使用RedisTemplate，处理序列化异常，支持降级 | 成功：缓存能正常存取，过期自动清理

- [ ] 3.2 修改天气服务添加缓存支持
  - 文件: 修改`mrshudson-backend/src/main/java/com/mrshudson/service/impl/WeatherServiceImpl.java`
  - 在查询天气前检查缓存，查询后存入缓存
  - _Leverage: `ToolCacheManager`, 现有`WeatherServiceImpl`
  - _Requirements: Requirement 4
  - _Prompt: 角色：Java服务优化专家 | 任务：修改WeatherServiceImpl，在getWeather方法中添加缓存逻辑，查询前检查缓存，查询后存入缓存（TTL=10分钟） | 限制：使用@Autowire注入ToolCacheManager，缓存key使用城市名 | 成功：重复查询同一城市命中缓存，不调用天气API

- [ ] 3.3 创建数据变更监听和缓存清除
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/cache/listener/DataChangeListener.java`
  - 监听日历、待办数据变更事件，清除相关缓存
  - _Leverage: `ApplicationEventPublisher`, `@EventListener`
  - _Requirements: Requirement 4
  - _Prompt: 角色：Spring事件驱动专家 | 任务：创建DataChangeListener，监听CalendarChangeEvent和TodoChangeEvent，事件触发时清除对应用户的缓存 | 限制：异步处理，异常不抛出 | 成功：数据变更后缓存被清除，下次查询重新加载

- [ ] 3.4 在日历和待办服务中发布变更事件
  - 文件: 修改`CalendarServiceImpl.java`, `TodoServiceImpl.java`
  - 在增删改操作后发布数据变更事件
  - _Leverage: `ApplicationEventPublisher`
  - _Requirements: Requirement 4
  - _Prompt: 角色：Spring开发者 | 任务：修改CalendarServiceImpl和TodoServiceImpl，在create/update/delete方法后发布对应的ChangeEvent | 限制：事件包含userId和操作类型 | 成功：事件正确发布并被监听

---

## 模块4: 意图识别路由

- [ ] 4.1 创建意图类型枚举和路由结果类
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/IntentType.java`
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/RouteResult.java`
  - 定义意图类型枚举和路由结果数据结构
  - _Leverage: 无
  - _Requirements: Requirement 2
  - _Prompt: 角色：Java开发者 | 任务：创建IntentType枚举（WEATHER_QUERY, CALENDAR_QUERY, TODO_QUERY, SMALL_TALK, GENERAL_CHAT）和RouteResult类 | 限制：使用Lombok，RouteResult包含handled, response, intentType, confidence字段 | 成功：枚举和类定义完整

- [ ] 4.2 创建意图处理器接口和基础实现
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/IntentHandler.java`
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/handler/AbstractIntentHandler.java`
  - 定义处理器接口，提供基础实现
  - _Leverage: 无
  - _Requirements: Requirement 2
  - _Prompt: 角色：Java接口设计专家 | 任务：创建IntentHandler接口（handle方法）和AbstractIntentHandler抽象类（提供日志记录等通用功能） | 限制：接口简单清晰，抽象类实现通用逻辑 | 成功：处理器框架可扩展

- [ ] 4.3 实现天气查询意图处理器
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/handler/WeatherIntentHandler.java`
  - 识别天气相关查询，调用WeatherService
  - _Leverage: `WeatherService`, `AbstractIntentHandler`
  - _Requirements: Requirement 2
  - _Prompt: 角色：Java业务逻辑开发者 | 任务：实现WeatherIntentHandler，识别"天气"、"温度"、"下雨"等关键词，调用WeatherService查询并返回结果 | 限制：提取查询中的城市名，默认北京，置信度固定0.95 | 成功：天气查询被正确路由

- [ ] 4.4 实现日历查询意图处理器
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/handler/CalendarIntentHandler.java`
  - 识别日历相关查询，调用CalendarService
  - _Leverage: `CalendarService`, `AbstractIntentHandler`
  - _Requirements: Requirement 2
  - _Prompt: 角色：Java业务逻辑开发者 | 任务：实现CalendarIntentHandler，识别"日程"、"会议"、"今天有什么"等关键词，查询用户日历并返回格式化结果 | 限制：查询范围今天到未来7天，格式化输出 | 成功：日历查询被正确路由

- [ ] 4.5 实现待办查询意图处理器
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/handler/TodoIntentHandler.java`
  - 识别待办相关查询，调用TodoService
  - _Leverage: `TodoService`, `AbstractIntentHandler`
  - _Requirements: Requirement 2
  - _Prompt: 角色：Java业务逻辑开发者 | 任务：实现TodoIntentHandler，识别"待办"、"任务"、"提醒"等关键词，查询用户待办列表并返回 | 限制：只返回未完成的待办，按优先级排序 | 成功：待办查询被正确路由

- [ ] 4.6 实现闲聊问候处理器
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/handler/SmallTalkHandler.java`
  - 识别简单问候，返回预设回复
  - _Leverage: `AbstractIntentHandler`
  - _Requirements: Requirement 2
  - _Prompt: 角色：Java开发者 | 任务：实现SmallTalkHandler，识别"你好"、"在吗"、"hello"等问候，从预设列表中随机返回友好回复 | 限制：零AI调用，纯本地处理 | 成功：问候消息被快速响应

- [ ] 4.7 创建意图路由器实现
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/IntentRouter.java`
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/impl/IntentRouterImpl.java`
  - 实现意图分类和路由分发逻辑
  - _Leverage: 所有IntentHandler实现
  - _Requirements: Requirement 2
  - _Prompt: 角色：Java架构师 | 任务：实现IntentRouter，包含classify方法（关键词匹配）和route方法（分发到对应处理器），置信度低于0.7返回UNKNOWN | 限制：使用ConcurrentHashMap存储处理器，线程安全 | 成功：各种意图被正确分类和路由

- [ ] 4.8 创建参数提取器接口和提取结果类
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/extract/ParameterExtractor.java`
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/extract/ExtractionResult.java`
  - 定义参数提取的标准接口和结果封装
  - _Leverage: IntentType
  - _Requirements: Requirement 2
  - _Prompt: 角色：Java接口设计专家 | 任务：创建ParameterExtractor接口（extract方法）和ExtractionResult类（success/failed状态、参数Map、失败原因） | 限制：设计通用，支持规则、AI等多种实现 | 成功：接口清晰可扩展

- [ ] 4.9 实现规则参数提取器（第1层）
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/extract/RuleBasedExtractor.java`
  - 实现基于正则和关键词的参数提取
  - _Leverage: ParameterExtractor, IntentType
  - _Requirements: Requirement 2
  - _Prompt: 角色：Java业务逻辑专家 | 任务：实现RuleBasedExtractor，为天气/日历/待办意图分别实现提取规则，支持城市名、日期、优先级等参数提取 | 限制：预定义常见城市列表，支持相对日期（今天/明天），失败时返回明确原因 | 成功：能提取80%以上常见表达

- [ ] 4.10 实现轻量AI参数提取器（第2层）
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/extract/LightweightAiExtractor.java`
  - 使用极简提示词调用AI仅提取参数
  - _Leverage: KimiClient, ParameterExtractor
  - _Requirements: Requirement 2
  - _Prompt: 角色：AI集成专家 | 任务：实现LightweightAiExtractor，构建极简提示词（只要求返回JSON参数），使用maxTokens=100限制输出，解析AI返回的JSON | 限制：只提取参数不生成回复，超时2秒，异常时返回失败 | 成功：能处理规则无法覆盖的模糊表达

- [ ] 4.11 实现三层混合意图路由器
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/impl/HybridIntentRouter.java`
  - 整合规则层、轻量AI层、完整AI层的降级流程
  - _Leverage: RuleBasedExtractor, LightweightAiExtractor, FullAiToolHandler
  - _Requirements: Requirement 2
  - _Prompt: 角色：系统架构师 | 任务：实现HybridIntentRouter，按顺序尝试规则提取->轻量AI提取->完整AI调用，每层失败进入下一层，记录每层的使用统计 | 限制：支持配置开关各层，每层超时保护，最终必须返回结果 | 成功：95%以上查询无需完整AI调用

- [ ] 4.12 创建路由器工厂和配置
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/intent/IntentRouterFactory.java`
  - 根据配置创建不同的路由器实现（纯规则/混合/纯AI）
  - _Leverage: OptimProperties
  - _Requirements: Requirement 2
  - _Prompt: 角色：工厂模式专家 | 任务：创建IntentRouterFactory，根据optim.intent.router.mode配置（rule-only/hybrid/ai-only）创建对应的路由器实例 | 限制：使用Spring @Configuration，支持运行时切换 | 成功：配置驱动，灵活切换

---

## 模块5: 语义缓存系统

- [ ] 5.1 创建缓存条目实体和数据结构
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/cache/CacheEntry.java`
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/cache/CacheMetadata.java`
  - 定义语义缓存的数据结构
  - _Leverage: Lombok
  - _Requirements: Requirement 1
  - _Prompt: 角色：Java数据建模专家 | 任务：创建CacheEntry（id, query, response, embedding, userId, timestamps）和CacheMetadata（tokens, cost等） | 限制：使用@Builder，字段完整 | 成功：数据结构满足缓存需求

- [ ] 5.2 实现关键词嵌入服务（简化版）
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/cache/impl/KeywordEmbeddingService.java`
  - 实现简化版文本嵌入（基于关键词的one-hot编码）
  - _Leverage: EmbeddingService接口
  - _Requirements: Requirement 1
  - _Prompt: 角色：NLP算法工程师 | 任务：实现KeywordEmbeddingService，将文本分词后生成one-hot向量（预定义100个关键词），用于相似度计算 | 限制：纯Java实现，无外部依赖，向量维度固定 | 成功：相似文本的向量相似度高

- [ ] 5.3 实现Redis向量存储（简化版）
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/cache/impl/RedisVectorStore.java`
  - 使用Redis Hash存储向量，遍历计算相似度
  - _Leverage: RedisTemplate, EmbeddingService, OptimUtils（余弦相似度）
  - _Requirements: Requirement 1
  - _Prompt: 角色：Java缓存开发专家 | 任务：实现RedisVectorStore，使用Redis Hash存储向量数据，search方法遍历用户所有缓存计算余弦相似度，返回最匹配的条目 | 限制：key格式semantic_cache:{userId}:{id}，只搜索同一用户的缓存 | 成功：数据正确存取，相似度搜索准确

- [ ] 5.4 实现Chroma向量存储（可选高级版）
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/cache/impl/ChromaVectorStore.java`
  - 集成Chroma向量数据库，使用HNSW索引
  - _Leverage: VectorStore接口, ChromaClient（需创建）
  - _Requirements: Requirement 1
  - _Prompt: 角色：向量数据库集成专家 | 任务：实现ChromaVectorStore，使用Chroma Java客户端进行向量存储和相似度搜索 | 限制：使用官方Chroma HTTP API，集合名为"semantic_cache"，自动创建索引 | 成功：高性能向量搜索，支持大规模数据

- [ ] 5.5 创建Chroma Java客户端
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/cache/chroma/ChromaClient.java`
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/cache/chroma/ChromaConfig.java`
  - 封装Chroma REST API调用
  - _Leverage: RestTemplate
  - _Requirements: Requirement 1
  - _Prompt: 角色：Java HTTP客户端开发 | 任务：创建ChromaClient，封装createCollection、addDocuments、query等方法，使用RestTemplate调用Chroma HTTP API | 限制：处理HTTP异常，支持连接池配置 | 成功：能正确调用Chroma服务进行向量操作

- [ ] 5.6 创建语义缓存服务实现
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/cache/SemanticCacheService.java`
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/cache/impl/SemanticCacheServiceImpl.java`
  - 实现完整的语义缓存服务
  - _Leverage: `VectorStore`, `EmbeddingService`, `CacheEntry`
  - _Requirements: Requirement 1
  - _Prompt: 角色：Java服务开发者 | 任务：实现SemanticCacheService，get方法查询相似缓存（阈值0.92），put方法存入新缓存，cleanup清理过期数据 | 限制：使用@Value注入相似度阈值，用户隔离（只查同一用户缓存） | 成功：语义相似的查询能命中缓存

- [ ] 5.7 创建缓存清理定时任务
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/cache/scheduler/CacheCleanupScheduler.java`
  - 定期清理过期缓存数据
  - _Leverage: `@Scheduled`, `SemanticCacheService`
  - _Requirements: Requirement 1
  - _Prompt: 角色：Spring调度开发者 | 任务：创建定时任务，每天凌晨3点执行，清理超过7天未访问的缓存数据 | 限制：使用@Scheduled(cron = "0 0 3 * * ?")，异步执行 | 成功：定时任务正常执行，过期数据被清理

---

## 模块6: 对话历史压缩

- [ ] 6.1 创建对话压缩配置类
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/compress/CompressionConfig.java`
  - 定义压缩触发阈值、保留消息数等配置
  - _Leverage: 无
  - _Requirements: Requirement 3
  - _Prompt: 角色：Java配置专家 | 任务：创建CompressionConfig类，包含triggerThreshold=10, keepRecentMessages=4, summaryMaxLength=100 | 限制：使用@Component，支持配置覆盖 | 成功：配置类可被注入使用

- [ ] 6.2 创建对话压缩器实现
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/compress/ConversationSummarizer.java`
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/compress/impl/ConversationSummarizerImpl.java`
  - 实现对话历史压缩逻辑
  - _Leverage: `CompressionConfig`, `OptimUtils`（token估算）
  - _Requirements: Requirement 3
  - _Prompt: 角色：Java算法开发者 | 任务：实现ConversationSummarizer，needsCompression检查消息数，compress方法保留最近4条，压缩前面消息为摘要 | 限制：摘要生成使用轻量级提示词，直接调用KimiClient（后期可降级为规则摘要） | 成功：长对话被正确压缩，token数减少

- [ ] 6.3 创建轻量级摘要生成提示词
  - 文件: 在`ConversationSummarizerImpl`中添加私有方法
  - 设计简洁的摘要生成提示词
  - _Leverage: `KimiClient`
  - _Requirements: Requirement 3
  - _Prompt: 角色：提示词工程师 | 任务：设计generateSummaryPrompt方法，生成提示词："用50字以内概括以下对话主题：[对话内容]" | 限制：中文提示词，明确要求字数限制 | 成功：生成的摘要在50字以内，准确概括主题

---

## 模块7: 优化层集成与AOP

- [ ] 7.1 创建优化服务包装器
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/wrapper/OptimChatService.java`
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/optim/wrapper/impl/OptimChatServiceImpl.java`
  - 包装原始ChatService，插入优化逻辑
  - _Leverage: 所有优化服务（SemanticCacheService, IntentRouter等）
  - _Requirements: 所有Requirements
  - _Prompt: 角色：Java架构师 | 任务：实现OptimChatService，包装原始ChatService.sendMessage，按顺序执行：查语义缓存->意图路由->对话压缩->调用AI->更新缓存 | 限制：保持与原接口兼容，支持配置开关各优化层 | 成功：所有优化层按顺序执行

- [ ] 7.2 修改AIServiceFactory返回优化后的服务
  - 文件: 修改`mrshudson-backend/src/main/java/com/mrshudson/ai/AIServiceFactory.java`
  - 替换原始服务为优化后的服务
  - _Leverage: `OptimChatService`, `ChatServiceImpl`
  - _Requirements: 所有Requirements
  - _Prompt: 角色：Java依赖注入专家 | 任务：修改AIServiceFactory.getService方法，返回OptimChatService（包装原始ChatServiceImpl） | 限制：保持原有接口不变，对Controller透明 | 成功：Controller无感知使用优化后的服务

- [ ] 7.3 创建系统提示词优化（精简版本）
  - 文件: 修改`ChatServiceImpl`中的`buildSystemPrompt`方法
  - 精简系统提示词，删除冗余说明
  - _Leverage: 现有`buildSystemPrompt`
  - _Requirements: Requirement 5
  - _Prompt: 角色：提示词优化专家 | 任务：精简buildSystemPrompt，保留核心身份定义和工具说明，删除示例和冗余描述，控制在500字以内 | 限制：保留必要信息，不降低AI理解 | 成功：提示词精简，token数减少30%以上

- [ ] 7.4 修改KimiClient参数配置
  - 文件: 修改`mrshudson-backend/src/main/java/com/mrshudson/mcp/kimi/KimiClient.java`
  - 调整temperature和maxTokens参数
  - _Leverage: `AIProperties`
  - _Requirements: Requirement 5
  - _Prompt: 角色：Java配置开发者 | 任务：修改KimiClient.chatCompletion，设置temperature=0.3，maxTokens=800，从配置读取 | 限制：使用配置值，提供默认值 | 成功：AI参数优化，回复更简洁

- [ ] 7.5 优化会话标题生成逻辑
  - 文件: 修改`mrshudson-backend/src/main/java/com/mrshudson/service/impl/ChatServiceImpl.java`
  - 简化标题生成，对简单查询使用模板
  - _Leverage: 现有`generateConversationTitle`
  - _Requirements: Requirement 6
  - _Prompt: 角色：Java业务优化专家 | 任务：修改generateConversationTitle，如果是问候/工具查询，使用预设标题（"关于天气的咨询"/"新对话"），只有复杂对话才调用AI | 限制：异步执行，不影响主流程 | 成功：简单查询零AI调用生成标题

- [ ] 7.6 创建缓存管理Controller（管理员接口）
  - 文件: `mrshudson-backend/src/main/java/com/mrshudson/controller/CacheManageController.java`
  - 提供手动清理缓存接口
  - _Leverage: `SemanticCacheService`, `ToolCacheManager`
  - _Requirements: Requirement 1, 4
  - _Prompt: 角色：Java API开发者 | 任务：创建CacheManageController，提供POST /api/admin/cache/clear/{type}接口，支持清除语义缓存/工具缓存 | 限制：需要管理员权限，记录操作日志 | 成功：管理员能手动清理缓存

- [ ] 7.7 编写优化层集成测试
  - 文件: `mrshudson-backend/src/test/java/com/mrshudson/optim/OptimIntegrationTest.java`
  - 验证各优化层协同工作
  - _Leverage: Spring Boot Test, `@SpringBootTest`
  - _Requirements: 所有Requirements
  - _Prompt: 角色：Java测试专家 | 任务：编写集成测试，验证完整流程：语义缓存命中、意图路由正确、对话压缩触发、成本统计准确 | 限制：使用@TestPropertySource关闭外部依赖，使用内存Redis | 成功：所有测试通过

---

## 实施建议

### 优先级排序

**P0（核心必做）:**
- 1.1 基础配置
- 3.1-3.2 工具缓存（效果最明显，零风险）
- 4.1-4.9 意图路由核心+规则提取（效果次明显，零风险）
- 7.3-7.5 系统提示词优化（简单有效）

**P1（重要优化 - 三层混合模式）:**
- 4.10 轻量AI提取器（处理规则无法覆盖的10%）
- 4.11 三层混合路由器（整合各层，智能降级）
- 4.12 路由器工厂（灵活配置）
- 5.1-5.4 语义缓存（需要调优）
- 6.1-6.3 对话压缩（长对话场景优化）
- 7.1-7.2 优化层集成

**P2（完善功能）:**
- 2.1-2.4 成本监控（监控和告警，含分层统计）
- 3.3-3.4 数据变更监听
- 5.5 缓存清理定时任务
- 7.6 管理员接口
- 7.7 集成测试

### 三层混合模式实施建议

**阶段1：规则层（Week 1）**
- 实现4.1-4.9：基础意图路由+规则提取
- 预期效果：85%工具查询零AI调用

**阶段2：轻量AI层（Week 2）**
- 实现4.10：轻量AI提取器
- 预期效果：额外覆盖10%模糊表达，成本仅增加5%

**阶段3：混合整合（Week 3）**
- 实现4.11-4.12：三层路由整合
- 预期效果：95%+查询无需完整AI调用

### 配置建议

```yaml
# 开发环境：纯规则
optim:
  intent:
    router:
      mode: rule-only

# 测试环境：混合（推荐）
optim:
  intent:
    router:
      mode: hybrid
      layers:
        rule:
          enabled: true
        lightweight-ai:
          enabled: true
          max-tokens: 100
          timeout: 2000ms
        full-ai:
          enabled: true

# 生产环境：观察数据后微调
# 如果规则层命中率>90%，可关闭轻量AI层
```

### 风险与回滚

- 所有优化层通过配置开关控制，可热关闭
- 保留原始ChatServiceImpl，随时可切换回退
- 建议灰度发布，先对10%用户启用

### 预期效果（三层混合模式）

| 场景 | 原方案（纯AI工具） | 新方案（三层混合） | 改进 |
|-----|-------------------|-------------------|------|
| "北京天气" | 2轮AI | 0轮（规则） | 省100% |
| "这里天气" | 2轮AI | 1轮轻量AI（提取） | 省80% |
| "最近城市天气" | 2轮AI | 2轮（完整AI） | 保底 |

**量化指标：**
- **工具查询（规则层）**: 85%查询零AI调用
- **工具查询（轻量AI层）**: 额外10%仅需1轮轻量AI（成本降低80%）
- **工具查询（完整AI层）**: 仅5%需要2轮AI（保底）
- **语义缓存**: 预计20-30%命中率（需要数据积累）
- **对话压缩**: 长对话token数减少50%
- **整体**: AI调用减少75%+，平均响应时间减少50%
