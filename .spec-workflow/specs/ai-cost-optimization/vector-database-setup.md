# 向量数据库配置指南

## 选型建议

根据MrsHudson的规模和需求，推荐以下方案：

### 方案对比

| 方案 | 适用场景 | 向量维度 | 性能 | 运维复杂度 | 推荐度 |
|-----|---------|---------|------|-----------|-------|
| **Redis Vector (简化版)** | 开发/测试，数据量<1万 | 固定100维 | 中等 | 低 | ⭐⭐⭐⭐⭐ |
| **Chroma** | 小型项目，数据量<10万 | 不限 | 高 | 低 | ⭐⭐⭐⭐ |
| **Milvus** | 中型项目，数据量>10万 | 不限 | 极高 | 中 | ⭐⭐⭐⭐ |
| **pgvector** | 已有PostgreSQL环境 | 不限 | 高 | 低 | ⭐⭐⭐ |

### 推荐选择

**开发环境**: Redis Vector (简化版)
- 复用现有Redis服务，无需额外部署
- 简化实现，满足开发测试需求
- 实现简单，易于调试

**生产环境**: Chroma 或 Milvus
- 专业的向量索引（HNSW）
- 高维向量支持（768/1536维）
- 高效的相似度搜索

---

## 方案一：Redis Vector 简化版（推荐用于开发）

### 实现原理

使用Redis Hash存储向量数据，查询时遍历计算相似度。

```java
// 存储结构
Key: semantic_cache:{userId}:{cacheId}
Field          Value
--------       -----
query          "今天北京天气怎么样？"
response       "北京今天晴，25°C..."
embedding      "[0.1, 0.2, 0.3, ...]"  // JSON序列化
userId         "123"
createdAt      "2024-01-15T10:30:00"
accessCount    "5"
```

### 优缺点

**优点：**
- 零额外部署成本
- 实现简单，代码可控
- 与现有架构无缝集成

**缺点：**
- 数据量大时查询性能下降（O(n)复杂度）
- 不适合高维向量（>200维）
- 无专业向量索引

### 适用数据规模

| 用户量 | 每用户缓存数 | 总数据量 | 查询延迟 | 建议 |
|-------|-------------|---------|---------|------|
| 100 | 100 | 1万 | <50ms | ✅ 适用 |
| 1000 | 100 | 10万 | 100-200ms | ⚠️ 勉强适用 |
| 10000 | 100 | 100万 | >500ms | ❌ 不适用 |

---

## 方案二：Chroma（推荐用于生产小规模）

### Docker Compose 配置

```yaml
# 在docker-compose.yml中添加
services:
  # ... 现有服务 ...

  # Chroma 向量数据库
  chroma:
    image: chromadb/chroma:0.4.22
    container_name: mrshudson-chroma
    restart: unless-stopped
    ports:
      - "8000:8000"
    volumes:
      - chroma_data:/chroma/chroma
    environment:
      - IS_PERSISTENT=TRUE
      - PERSIST_DIRECTORY=/chroma/chroma
      - ANONYMIZED_TELEMETRY=FALSE
    networks:
      - mrshudson-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8000/api/v1/heartbeat"]
      interval: 30s
      timeout: 10s
      retries: 3

volumes:
  # ... 现有卷 ...
  chroma_data:
    driver: local
```

### 特点

- **轻量级**: 单容器部署，资源占用低
- **易用性**: REST API，集成简单
- **持久化**: 数据持久化到本地卷
- **索引**: 自动创建HNSW索引

### Java客户端集成

```java
@Configuration
public class ChromaConfig {

    @Value("${chroma.host:localhost}")
    private String host;

    @Value("${chroma.port:8000}")
    private int port;

    @Bean
    public ChromaClient chromaClient() {
        return new ChromaClient("http://" + host + ":" + port);
    }
}

@Service
public class ChromaVectorStore implements VectorStore {

    @Autowired
    private ChromaClient client;

    @Autowired
    private EmbeddingService embeddingService;

    private static final String COLLECTION_NAME = "semantic_cache";

    @PostConstruct
    public void init() {
        // 创建集合（如果不存在）
        try {
            client.createCollection(COLLECTION_NAME);
        } catch (Exception e) {
            log.info("Collection already exists");
        }
    }

    @Override
    public void store(String id, String query, String response,
                      float[] embedding, CacheMetadata metadata) {
        Map<String, Object> document = new HashMap<>();
        document.put("id", id);
        document.put("query", query);
        document.put("response", response);
        document.put("userId", metadata.getUserId());
        document.put("createdAt", metadata.getCreatedAt().toString());

        client.addDocuments(COLLECTION_NAME,
            Collections.singletonList(document),
            Collections.singletonList(embedding),
            Collections.singletonList(id));
    }

    @Override
    public Optional<CacheEntry> search(float[] queryEmbedding,
                                        long userId,
                                        float threshold) {
        // Chroma自动处理相似度搜索
        SearchResult result = client.query(
            COLLECTION_NAME,
            Collections.singletonList(queryEmbedding),
            1,  // top_k
            threshold
        );

        if (result.getDistances().get(0) < threshold) {
            return Optional.of(convertToEntry(result));
        }
        return Optional.empty();
    }
}
```

---

## 方案三：Milvus（推荐用于生产大规模）

### Docker Compose 配置

```yaml
# 在docker-compose.yml中添加
services:
  # ... 现有服务 ...

  # Milvus向量数据库
  etcd:
    container_name: milvus-etcd
    image: quay.io/coreos/etcd:v3.5.5
    environment:
      - ETCD_AUTO_COMPACTION_MODE=revision
      - ETCD_AUTO_COMPACTION_RETENTION=1000
      - ETCD_QUOTA_BACKEND_BYTES=4294967296
      - ETCD_SNAPSHOT_COUNT=50000
    volumes:
      - etcd_data:/etcd
    command: etcd -advertise-client-urls=http://127.0.0.1:2379 -listen-client-urls http://0.0.0.0:2379 --data-dir /etcd
    networks:
      - mrshudson-network

  minio:
    container_name: milvus-minio
    image: minio/minio:RELEASE.2023-03-20T20-16-18Z
    environment:
      MINIO_ACCESS_KEY: minioadmin
      MINIO_SECRET_KEY: minioadmin
    ports:
      - "9001:9001"
      - "9000:9000"
    volumes:
      - minio_data:/minio_data
    command: minio server /minio_data --console-address ":9001"
    networks:
      - mrshudson-network

  milvus:
    container_name: milvus-standalone
    image: milvusdb/milvus:v2.3.3
    command: ["milvus", "run", "standalone"]
    environment:
      ETCD_ENDPOINTS: etcd:2379
      MINIO_ADDRESS: minio:9000
    ports:
      - "19530:19530"
      - "9091:9091"
    volumes:
      - milvus_data:/var/lib/milvus
    depends_on:
      - etcd
      - minio
    networks:
      - mrshudson-network

volumes:
  # ... 现有卷 ...
  etcd_data:
    driver: local
  minio_data:
    driver: local
  milvus_data:
    driver: local
```

### 特点

- **高性能**: 十亿级向量秒级搜索
- **分布式**: 支持水平扩展
- **多索引**: 支持FLAT、IVF、HNSW等多种索引
- **云原生**: Kubernetes原生支持

### 适用场景

- 日活用户 > 1万
- 向量数据 > 100万
- 需要毫秒级响应

---

## 方案四：pgvector（如果迁移到PostgreSQL）

### Docker Compose 配置

```yaml
# 替换现有的MySQL服务
services:
  postgres:
    image: ankane/pgvector:latest
    container_name: mrshudson-postgres
    restart: unless-stopped
    environment:
      POSTGRES_USER: ${POSTGRES_USER:-mrshudson}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-mrshudson_pass}
      POSTGRES_DB: ${POSTGRES_DB:-mrshudson}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    networks:
      - mrshudson-network

volumes:
  postgres_data:
    driver: local
```

### 建表语句

```sql
-- 启用pgvector扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 语义缓存表
CREATE TABLE semantic_cache (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    query TEXT NOT NULL,
    response TEXT NOT NULL,
    embedding vector(768),  -- 根据嵌入模型调整维度
    input_tokens INT,
    output_tokens INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_accessed TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    access_count INT DEFAULT 1
);

-- 创建向量索引（HNSW）
CREATE INDEX idx_semantic_cache_embedding ON semantic_cache
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- 用户隔离索引
CREATE INDEX idx_semantic_cache_user ON semantic_cache(user_id);

-- 相似度搜索查询
SELECT *, embedding <=> $1 AS distance
FROM semantic_cache
WHERE user_id = $2
ORDER BY embedding <=> $1
LIMIT 1;
```

---

## 推荐配置（开发环境）

### 更新后的 docker-compose.yml

```yaml
version: '3.8'

services:
  # MySQL 数据库服务
  mysql:
    image: mysql:8.0
    container_name: mrshudson-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-mrshudson_root}
      MYSQL_DATABASE: ${MYSQL_DATABASE:-mrshudson}
      MYSQL_USER: ${MYSQL_USER:-mrshudson}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:-mrshudson_pass}
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
      - ./mrshudson-backend/src/main/resources/db/migration:/docker-entrypoint-initdb.d
    networks:
      - mrshudson-network
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "root", "-p${MYSQL_ROOT_PASSWORD:-mrshudson_root}"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Redis 缓存服务
  redis:
    image: redis:7-alpine
    container_name: mrshudson-redis
    restart: unless-stopped
    command: redis-server --requirepass ${REDIS_PASSWORD:-mrshudson_redis}
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    networks:
      - mrshudson-network
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Chroma 向量数据库（可选，用于语义缓存）
  chroma:
    image: chromadb/chroma:0.4.22
    container_name: mrshudson-chroma
    restart: unless-stopped
    ports:
      - "8000:8000"
    volumes:
      - chroma_data:/chroma/chroma
    environment:
      - IS_PERSISTENT=TRUE
      - PERSIST_DIRECTORY=/chroma/chroma
      - ANONYMIZED_TELEMETRY=FALSE
    networks:
      - mrshudson-network
    profiles:
      - vector  # 使用 docker-compose --profile vector up 启动

  # 后端服务
  backend:
    build:
      context: ./mrshudson-backend
      dockerfile: Dockerfile
    container_name: mrshudson-backend
    restart: unless-stopped
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/${MYSQL_DATABASE:-mrshudson}?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
      SPRING_DATASOURCE_USERNAME: ${MYSQL_USER:-mrshudson}
      SPRING_DATASOURCE_PASSWORD: ${MYSQL_PASSWORD:-mrshudson_pass}
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: 6379
      SPRING_DATA_REDIS_PASSWORD: ${REDIS_PASSWORD:-mrshudson_redis}
      # 向量数据库配置
      VECTOR_STORE_TYPE: ${VECTOR_STORE_TYPE:-redis}  # redis/chroma/none
      CHROMA_HOST: chroma
      CHROMA_PORT: 8000
      KIMI_API_KEY: ${KIMI_API_KEY}
      WEATHER_API_KEY: ${WEATHER_API_KEY}
      JAVA_OPTS: ${JAVA_OPTS:--Xms512m -Xmx1024m}
    ports:
      - "8080:8080"
    networks:
      - mrshudson-network

  # 前端服务
  frontend:
    build:
      context: ./mrshudson-frontend
      dockerfile: Dockerfile
    container_name: mrshudson-frontend
    restart: unless-stopped
    depends_on:
      - backend
    ports:
      - "80:80"
    networks:
      - mrshudson-network

volumes:
  mysql_data:
    driver: local
  redis_data:
    driver: local
  chroma_data:
    driver: local

networks:
  mrshudson-network:
    driver: bridge
```

---

## 环境变量配置

在 `.env` 文件中添加：

```bash
# 向量数据库配置
VECTOR_STORE_TYPE=redis        # 选项: redis / chroma / none
CHROMA_HOST=localhost
CHROMA_PORT=8000

# 语义缓存配置
SEMANTIC_CACHE_ENABLED=true
SEMANTIC_CACHE_SIMILARITY_THRESHOLD=0.92
SEMANTIC_CACHE_MAX_ENTRIES_PER_USER=1000
SEMANTIC_CACHE_TTL_DAYS=7
```

---

## 启动命令

### 仅基础服务（使用Redis Vector简化版）
```bash
docker-compose up -d mysql redis
```

### 包含向量数据库（Chroma）
```bash
docker-compose --profile vector up -d
```

### 查看Chroma状态
```bash
curl http://localhost:8000/api/v1/heartbeat
```

---

## Java配置类

```java
@Configuration
public class VectorStoreConfig {

    @Value("${vector.store.type:redis}")
    private String vectorStoreType;

    @Bean
    @ConditionalOnProperty(name = "vector.store.type", havingValue = "redis")
    public VectorStore redisVectorStore(RedisTemplate<String, String> redisTemplate,
                                         EmbeddingService embeddingService) {
        return new RedisVectorStore(redisTemplate, embeddingService);
    }

    @Bean
    @ConditionalOnProperty(name = "vector.store.type", havingValue = "chroma")
    public VectorStore chromaVectorStore(ChromaClient chromaClient,
                                          EmbeddingService embeddingService) {
        return new ChromaVectorStore(chromaClient, embeddingService);
    }

    @Bean
    @ConditionalOnProperty(name = "vector.store.type", havingValue = "none")
    public VectorStore noopVectorStore() {
        return new NoopVectorStore();  // 空实现，禁用语义缓存
    }
}
```

---

## 迁移策略

1. **第一阶段（开发）**: 使用Redis Vector简化版，快速验证功能
2. **第二阶段（测试）**: 引入Chroma，对比性能和准确率
3. **第三阶段（生产）**: 根据数据量选择Chroma或Milvus

---

## 监控指标

| 指标 | 说明 | 告警阈值 |
|-----|------|---------|
| vector_search_latency | 向量搜索延迟 | P99 > 100ms |
| vector_db_connections | 向量数据库连接数 | > 80% 最大连接 |
| cache_hit_rate | 语义缓存命中率 | < 20% |
| vector_store_size | 向量存储大小 | > 10GB |

---

## 总结

| 环境 | 推荐方案 | 理由 |
|-----|---------|------|
| 本地开发 | Redis Vector | 零额外依赖，简单快速 |
| 集成测试 | Chroma | 接近生产，易于测试 |
| 生产小规模 | Chroma | 轻量，性能足够 |
| 生产大规模 | Milvus | 高性能，可扩展 |
