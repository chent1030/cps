package com.company.cps.service;

import com.company.cps.config.CpsAiProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NewApiImageEmbeddingClient implements ImageEmbeddingClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;
    private final CpsAiProperties properties;

    @Autowired
    public NewApiImageEmbeddingClient(CpsAiProperties properties) {
        this(new RestTemplate(), properties);
    }

    NewApiImageEmbeddingClient(RestTemplate restTemplate, CpsAiProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public ImageEmbeddingResult embedImage(String imageUrl) {
        CpsAiProperties.Embedding embedding = properties.getEmbedding();
        Map<String, Object> imageInput = new LinkedHashMap<>();
        imageInput.put("image_url", imageUrl);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", embedding.getModel());
        request.put("input", Collections.singletonList(imageInput));
        String rawRequest = json(request);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(embedding.getApiKey() == null ? "" : embedding.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        String rawResponse = restTemplate.postForObject(
                baseUrl(embedding) + endpoint(embedding),
                new HttpEntity<>(rawRequest, headers),
                String.class
        );
        List<Float> vector = parseVector(rawResponse);
        if (vector.size() != embedding.getDimension()) {
            throw new IllegalStateException("Embedding dimension mismatch: expected " + embedding.getDimension() + " but got " + vector.size());
        }
        return new ImageEmbeddingResult(
                vector,
                embedding.getModel(),
                embedding.getModelVersion(),
                embedding.getDimension(),
                rawRequest,
                rawResponse
        );
    }

    private static List<Float> parseVector(String rawResponse) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawResponse);
            JsonNode embeddingNode = root.path("data").path(0).path("embedding");
            if (!embeddingNode.isArray()) {
                throw new IllegalStateException("NewAPI response does not contain data[0].embedding");
            }
            List<Float> vector = new ArrayList<>(embeddingNode.size());
            for (JsonNode node : embeddingNode) {
                vector.add((float) node.asDouble());
            }
            return vector;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse NewAPI embedding response", e);
        }
    }

    private static String json(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize NewAPI embedding request", e);
        }
    }

    private static String baseUrl(CpsAiProperties.Embedding embedding) {
        if (isBlank(embedding.getBaseUrl())) {
            throw new IllegalStateException("cps.ai.embedding.base-url is required");
        }
        return embedding.getBaseUrl().replaceAll("/+$", "");
    }

    private static String endpoint(CpsAiProperties.Embedding embedding) {
        String endpoint = isBlank(embedding.getEndpoint())
                ? "/v1/embeddings"
                : embedding.getEndpoint();
        return endpoint.startsWith("/") ? endpoint : "/" + endpoint;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
