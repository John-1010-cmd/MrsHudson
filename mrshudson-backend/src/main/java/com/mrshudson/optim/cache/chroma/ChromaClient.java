package com.mrshudson.optim.cache.chroma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * Chroma向量数据库Java客户端
 * 封装Chroma REST API调用
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "optim.vector-store.type", havingValue = "chroma")
public class ChromaClient {

    private final ChromaConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private String collectionId;

    public ChromaClient(ChromaConfig config) {
        this.config = config;
        this.restTemplate = createRestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 初始化时创建集合
     */
    @PostConstruct
    public void init() {
        try {
            createCollection(config.getCollectionName());
            log.info("Chroma client initialized with collection: {}", config.getCollectionName());
        } catch (Exception e) {
            log.warn("Failed to initialize Chroma collection: {}", e.getMessage());
        }
    }

    /**
     * 创建RestTemplate
     */
    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(config.getConnectTimeout());
        factory.setReadTimeout(config.getReadTimeout());
        return new RestTemplate(factory);
    }

    /**
     * 创建集合
     *
     * @param name 集合名称
     * @return 集合ID
     */
    public String createCollection(String name) {
        try {
            String url = config.getHost() + "/api/v1/collections";

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("name", name);
            requestBody.put("tenant", config.getTenant());
            requestBody.put("database", config.getDatabase());

            // 创建元数据
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("description", "Semantic cache collection");
            requestBody.set("metadata", metadata);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (!config.getApiKey().isEmpty()) {
                headers.set("Authorization", "Bearer " + config.getApiKey());
            }

            HttpEntity<ObjectNode> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode body = response.getBody();
                if (body.has("id")) {
                    this.collectionId = body.get("id").asText();
                    log.debug("Created collection {} with id: {}", name, collectionId);
                    return collectionId;
                }
            }

            // 如果创建失败，尝试获取已存在的集合
            return getCollectionId(name);

        } catch (RestClientException e) {
            log.error("Error creating Chroma collection: {}", e.getMessage());
            throw new ChromaException("Failed to create collection: " + name, e);
        }
    }

    /**
     * 获取集合ID
     *
     * @param name 集合名称
     * @return 集合ID
     */
    public String getCollectionId(String name) {
        try {
            String url = config.getHost() + "/api/v1/collections/" + name
                    + "?tenant=" + config.getTenant()
                    + "&database=" + config.getDatabase();

            HttpHeaders headers = new HttpHeaders();
            if (!config.getApiKey().isEmpty()) {
                headers.set("Authorization", "Bearer " + config.getApiKey());
            }

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode body = response.getBody();
                if (body.has("id")) {
                    this.collectionId = body.get("id").asText();
                    return collectionId;
                }
            }

            return null;

        } catch (RestClientException e) {
            log.error("Error getting Chroma collection: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 添加文档到集合
     *
     * @param ids        文档ID列表
     * @param embeddings 向量嵌入列表
     * @param documents  文档内容列表
     * @param metadatas  元数据列表
     * @return 是否成功
     */
    public boolean addDocuments(List<String> ids, List<float[]> embeddings,
                                 List<String> documents, List<Map<String, Object>> metadatas) {
        if (collectionId == null) {
            collectionId = getCollectionId(config.getCollectionName());
        }

        try {
            String url = config.getHost() + "/api/v1/collections/" + collectionId + "/add";

            ObjectNode requestBody = objectMapper.createObjectNode();

            // IDs
            ArrayNode idsArray = objectMapper.createArrayNode();
            ids.forEach(idsArray::add);
            requestBody.set("ids", idsArray);

            // Embeddings
            ArrayNode embeddingsArray = objectMapper.createArrayNode();
            for (float[] embedding : embeddings) {
                ArrayNode vec = objectMapper.createArrayNode();
                for (float v : embedding) {
                    vec.add(v);
                }
                embeddingsArray.add(vec);
            }
            requestBody.set("embeddings", embeddingsArray);

            // Documents
            if (documents != null && !documents.isEmpty()) {
                ArrayNode docsArray = objectMapper.createArrayNode();
                documents.forEach(docsArray::add);
                requestBody.set("documents", docsArray);
            }

            // Metadatas
            if (metadatas != null && !metadatas.isEmpty()) {
                ArrayNode metasArray = objectMapper.createArrayNode();
                for (Map<String, Object> metadata : metadatas) {
                    ObjectNode meta = objectMapper.createObjectNode();
                    metadata.forEach((k, v) -> meta.put(k, String.valueOf(v)));
                    metasArray.add(meta);
                }
                requestBody.set("metadatas", metasArray);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (!config.getApiKey().isEmpty()) {
                headers.set("Authorization", "Bearer " + config.getApiKey());
            }

            HttpEntity<ObjectNode> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);

            return response.getStatusCode().is2xxSuccessful();

        } catch (RestClientException e) {
            log.error("Error adding documents to Chroma: {}", e.getMessage());
            throw new ChromaException("Failed to add documents", e);
        }
    }

    /**
     * 添加单个文档
     *
     * @param id         文档ID
     * @param embedding  向量嵌入
     * @param document   文档内容
     * @param metadata   元数据
     * @return 是否成功
     */
    public boolean addDocument(String id, float[] embedding, String document, Map<String, Object> metadata) {
        return addDocuments(
                Collections.singletonList(id),
                Collections.singletonList(embedding),
                Collections.singletonList(document),
                metadata != null ? Collections.singletonList(metadata) : null
        );
    }

    /**
     * 向量相似度查询
     *
     * @param queryEmbedding 查询向量
     * @param nResults       返回结果数量
     * @param where          过滤条件
     * @return 查询结果
     */
    public ChromaQueryResult query(float[] queryEmbedding, int nResults, Map<String, Object> where) {
        if (collectionId == null) {
            collectionId = getCollectionId(config.getCollectionName());
        }

        try {
            String url = config.getHost() + "/api/v1/collections/" + collectionId + "/query";

            ObjectNode requestBody = objectMapper.createObjectNode();

            // Query embeddings
            ArrayNode queryEmbeddings = objectMapper.createArrayNode();
            ArrayNode vec = objectMapper.createArrayNode();
            for (float v : queryEmbedding) {
                vec.add(v);
            }
            queryEmbeddings.add(vec);
            requestBody.set("query_embeddings", queryEmbeddings);

            // N results
            requestBody.put("n_results", nResults);

            // Include fields
            ArrayNode include = objectMapper.createArrayNode();
            include.add("documents");
            include.add("metadatas");
            include.add("distances");
            requestBody.set("include", include);

            // Where filter
            if (where != null && !where.isEmpty()) {
                ObjectNode whereNode = objectMapper.createObjectNode();
                where.forEach((k, v) -> whereNode.put(k, String.valueOf(v)));
                requestBody.set("where", whereNode);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (!config.getApiKey().isEmpty()) {
                headers.set("Authorization", "Bearer " + config.getApiKey());
            }

            HttpEntity<ObjectNode> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseQueryResult(response.getBody());
            }

            return new ChromaQueryResult();

        } catch (RestClientException e) {
            log.error("Error querying Chroma: {}", e.getMessage());
            throw new ChromaException("Failed to query", e);
        }
    }

    /**
     * 删除文档
     *
     * @param ids 文档ID列表
     * @return 是否成功
     */
    public boolean deleteDocuments(List<String> ids) {
        if (collectionId == null) {
            collectionId = getCollectionId(config.getCollectionName());
        }

        try {
            String url = config.getHost() + "/api/v1/collections/" + collectionId + "/delete";

            ObjectNode requestBody = objectMapper.createObjectNode();
            ArrayNode idsArray = objectMapper.createArrayNode();
            ids.forEach(idsArray::add);
            requestBody.set("ids", idsArray);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (!config.getApiKey().isEmpty()) {
                headers.set("Authorization", "Bearer " + config.getApiKey());
            }

            HttpEntity<ObjectNode> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, JsonNode.class);

            return response.getStatusCode().is2xxSuccessful();

        } catch (RestClientException e) {
            log.error("Error deleting documents from Chroma: {}", e.getMessage());
            throw new ChromaException("Failed to delete documents", e);
        }
    }

    /**
     * 获取集合中的所有条目
     *
     * @return 条目列表
     */
    public List<ChromaDocument> getAllDocuments() {
        if (collectionId == null) {
            collectionId = getCollectionId(config.getCollectionName());
        }

        try {
            String url = config.getHost() + "/api/v1/collections/" + collectionId + "/get";

            ObjectNode requestBody = objectMapper.createObjectNode();
            ArrayNode include = objectMapper.createArrayNode();
            include.add("documents");
            include.add("metadatas");
            requestBody.set("include", include);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (!config.getApiKey().isEmpty()) {
                headers.set("Authorization", "Bearer " + config.getApiKey());
            }

            HttpEntity<ObjectNode> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseDocuments(response.getBody());
            }

            return Collections.emptyList();

        } catch (RestClientException e) {
            log.error("Error getting documents from Chroma: {}", e.getMessage());
            throw new ChromaException("Failed to get documents", e);
        }
    }

    /**
     * 解析查询结果
     */
    private ChromaQueryResult parseQueryResult(JsonNode response) {
        ChromaQueryResult result = new ChromaQueryResult();

        if (response.has("ids") && response.get("ids").isArray()) {
            ArrayNode idsArray = (ArrayNode) response.get("ids");
            if (idsArray.size() > 0 && idsArray.get(0).isArray()) {
                ArrayNode firstQueryIds = (ArrayNode) idsArray.get(0);
                firstQueryIds.forEach(id -> result.getIds().add(id.asText()));
            }
        }

        if (response.has("documents") && response.get("documents").isArray()) {
            ArrayNode docsArray = (ArrayNode) response.get("documents");
            if (docsArray.size() > 0 && docsArray.get(0).isArray()) {
                ArrayNode firstQueryDocs = (ArrayNode) docsArray.get(0);
                firstQueryDocs.forEach(doc -> {
                    if (doc.isNull()) {
                        result.getDocuments().add(null);
                    } else {
                        result.getDocuments().add(doc.asText());
                    }
                });
            }
        }

        if (response.has("distances") && response.get("distances").isArray()) {
            ArrayNode distArray = (ArrayNode) response.get("distances");
            if (distArray.size() > 0 && distArray.get(0).isArray()) {
                ArrayNode firstQueryDistances = (ArrayNode) distArray.get(0);
                firstQueryDistances.forEach(dist -> result.getDistances().add(dist.asDouble()));
            }
        }

        if (response.has("metadatas") && response.get("metadatas").isArray()) {
            ArrayNode metaArray = (ArrayNode) response.get("metadatas");
            if (metaArray.size() > 0 && metaArray.get(0).isArray()) {
                ArrayNode firstQueryMetas = (ArrayNode) metaArray.get(0);
                firstQueryMetas.forEach(meta -> {
                    Map<String, String> metadata = new HashMap<>();
                    if (meta.isObject()) {
                        meta.fields().forEachRemaining(entry ->
                                metadata.put(entry.getKey(), entry.getValue().asText()));
                    }
                    result.getMetadatas().add(metadata);
                });
            }
        }

        return result;
    }

    /**
     * 解析文档列表
     */
    private List<ChromaDocument> parseDocuments(JsonNode response) {
        List<ChromaDocument> documents = new ArrayList<>();

        if (!response.has("ids")) {
            return documents;
        }

        ArrayNode idsArray = (ArrayNode) response.get("ids");
        ArrayNode docsArray = response.has("documents") ? (ArrayNode) response.get("documents") : null;
        ArrayNode metaArray = response.has("metadatas") ? (ArrayNode) response.get("metadatas") : null;

        for (int i = 0; i < idsArray.size(); i++) {
            ChromaDocument doc = new ChromaDocument();
            doc.setId(idsArray.get(i).asText());

            if (docsArray != null && i < docsArray.size() && !docsArray.get(i).isNull()) {
                doc.setDocument(docsArray.get(i).asText());
            }

            if (metaArray != null && i < metaArray.size() && metaArray.get(i).isObject()) {
                Map<String, String> metadata = new HashMap<>();
                metaArray.get(i).fields().forEachRemaining(entry ->
                        metadata.put(entry.getKey(), entry.getValue().asText()));
                doc.setMetadata(metadata);
            }

            documents.add(doc);
        }

        return documents;
    }

    /**
     * Chroma查询结果
     */
    public static class ChromaQueryResult {
        private List<String> ids = new ArrayList<>();
        private List<String> documents = new ArrayList<>();
        private List<Double> distances = new ArrayList<>();
        private List<Map<String, String>> metadatas = new ArrayList<>();

        public List<String> getIds() {
            return ids;
        }

        public void setIds(List<String> ids) {
            this.ids = ids;
        }

        public List<String> getDocuments() {
            return documents;
        }

        public void setDocuments(List<String> documents) {
            this.documents = documents;
        }

        public List<Double> getDistances() {
            return distances;
        }

        public void setDistances(List<Double> distances) {
            this.distances = distances;
        }

        public List<Map<String, String>> getMetadatas() {
            return metadatas;
        }

        public void setMetadatas(List<Map<String, String>> metadatas) {
            this.metadatas = metadatas;
        }
    }

    /**
     * Chroma文档
     */
    public static class ChromaDocument {
        private String id;
        private String document;
        private Map<String, String> metadata;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getDocument() {
            return document;
        }

        public void setDocument(String document) {
            this.document = document;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
        }
    }

    /**
     * Chroma异常
     */
    public static class ChromaException extends RuntimeException {
        public ChromaException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
