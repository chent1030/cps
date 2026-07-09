package com.company.cps.service;

import com.company.cps.config.CpsAiProperties;
import com.company.cps.config.CpsMilvusProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "cps.milvus", name = "enabled", havingValue = "true")
public class HttpMilvusVectorService implements MilvusVectorService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;
    private final CpsMilvusProperties milvusProperties;
    private final CpsAiProperties aiProperties;

    @Autowired
    public HttpMilvusVectorService(CpsMilvusProperties milvusProperties, CpsAiProperties aiProperties) {
        this(new RestTemplate(), milvusProperties, aiProperties);
    }

    HttpMilvusVectorService(RestTemplate restTemplate, CpsMilvusProperties milvusProperties, CpsAiProperties aiProperties) {
        this.restTemplate = restTemplate;
        this.milvusProperties = milvusProperties;
        this.aiProperties = aiProperties;
    }

    @Override
    public void ensureCollection() {
        Map<String, Object> request = baseCollectionRequest();
        request.put("dimension", aiProperties.getEmbedding().getDimension());
        request.put("metricType", milvusProperties.getMetricType());
        request.put("indexType", milvusProperties.getIndexType());
        post("/v1/vector/ensure", request);
    }

    @Override
    public void loadCollection() {
        post("/v1/vector/load", baseCollectionRequest());
    }

    @Override
    public void upsertKnowledgeImage(Long imageId, Long caseId, Long categoryL1Id, Long categoryL2Id, boolean enabled, List<Float> vector) {
        Map<String, Object> request = baseCollectionRequest();
        request.put("id", imageId);
        request.put("caseId", caseId);
        request.put("categoryL1Id", categoryL1Id);
        request.put("categoryL2Id", categoryL2Id);
        request.put("enabled", enabled);
        request.put("vector", vector);
        post("/v1/vector/upsert", request);
    }

    @Override
    public List<MilvusSearchHit> searchSimilarImages(List<Float> vector, int topK) {
        Map<String, Object> request = baseCollectionRequest();
        request.put("vector", vector);
        request.put("topK", topK);
        String rawResponse = post("/v1/vector/search", request);
        return parseHits(rawResponse);
    }

    private Map<String, Object> baseCollectionRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("collection", milvusProperties.getCollection());
        return request;
    }

    private String post(String endpoint, Map<String, Object> request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (!isBlank(milvusProperties.getApiKey())) {
            headers.setBearerAuth(milvusProperties.getApiKey());
        }
        return restTemplate.postForObject(
                baseUrl() + endpoint,
                new HttpEntity<>(json(request), headers),
                String.class
        );
    }

    private List<MilvusSearchHit> parseHits(String rawResponse) {
        try {
            JsonNode hitsNode = OBJECT_MAPPER.readTree(rawResponse).path("hits");
            if (!hitsNode.isArray()) {
                throw new IllegalStateException("Vector service response does not contain hits");
            }
            List<MilvusSearchHit> hits = new ArrayList<>(hitsNode.size());
            for (JsonNode item : hitsNode) {
                hits.add(new MilvusSearchHit(
                        asLong(item.path("imageId")),
                        asLong(item.path("caseId")),
                        asLong(item.path("categoryL1Id")),
                        asLong(item.path("categoryL2Id")),
                        item.path("score").asDouble()
                ));
            }
            return hits;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse vector service search response", e);
        }
    }

    private String baseUrl() {
        if (isBlank(milvusProperties.getBaseUrl())) {
            throw new IllegalStateException("cps.milvus.base-url is required");
        }
        return milvusProperties.getBaseUrl().replaceAll("/+$", "");
    }

    private static Long asLong(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asLong();
    }

    private static String json(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize vector service request", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
