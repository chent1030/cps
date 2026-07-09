package com.company.cps.service;

import com.company.cps.config.CpsAiProperties;
import com.company.cps.config.CpsMilvusProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpMilvusVectorServiceTest {

    @Test
    void ensureCollectionCallsEmbeddingServiceVectorApi() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        HttpMilvusVectorService service = new HttpMilvusVectorService(restTemplate, milvusProperties(), aiProperties());

        server.expect(requestTo("http://vector.test/v1/vector/ensure"))
                .andExpect(header("Authorization", "Bearer vector-key"))
                .andExpect(jsonPath("$.collection").value("cps_vectors"))
                .andExpect(jsonPath("$.dimension").value(3))
                .andExpect(jsonPath("$.metricType").value("COSINE"))
                .andExpect(jsonPath("$.indexType").value("HNSW"))
                .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));

        service.ensureCollection();

        server.verify();
    }

    @Test
    void upsertKnowledgeImageCallsEmbeddingServiceVectorApi() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        HttpMilvusVectorService service = new HttpMilvusVectorService(restTemplate, milvusProperties(), aiProperties());

        server.expect(requestTo("http://vector.test/v1/vector/upsert"))
                .andExpect(header("Authorization", "Bearer vector-key"))
                .andExpect(jsonPath("$.collection").value("cps_vectors"))
                .andExpect(jsonPath("$.id").value(501))
                .andExpect(jsonPath("$.caseId").value(12))
                .andExpect(jsonPath("$.categoryL1Id").value(100))
                .andExpect(jsonPath("$.categoryL2Id").value(101))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.vector[0]").value(0.1))
                .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));

        service.upsertKnowledgeImage(501L, 12L, 100L, 101L, true, Arrays.asList(0.1f, 0.2f, 0.3f));

        server.verify();
    }

    @Test
    void searchSimilarImagesParsesEmbeddingServiceHits() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        HttpMilvusVectorService service = new HttpMilvusVectorService(restTemplate, milvusProperties(), aiProperties());

        server.expect(requestTo("http://vector.test/v1/vector/search"))
                .andExpect(header("Authorization", "Bearer vector-key"))
                .andExpect(jsonPath("$.collection").value("cps_vectors"))
                .andExpect(jsonPath("$.topK").value(2))
                .andExpect(jsonPath("$.vector[1]").value(0.2))
                .andRespond(withSuccess("{\n"
                        + "  \"hits\": [\n"
                        + "    {\"imageId\": 501, \"caseId\": 12, \"categoryL1Id\": 100, \"categoryL2Id\": 101, \"score\": 0.91},\n"
                        + "    {\"imageId\": 502, \"caseId\": 13, \"categoryL1Id\": 200, \"categoryL2Id\": 201, \"score\": 0.82}\n"
                        + "  ]\n"
                        + "}", MediaType.APPLICATION_JSON));

        List<MilvusSearchHit> hits = service.searchSimilarImages(Arrays.asList(0.1f, 0.2f, 0.3f), 2);

        assertEquals(2, hits.size());
        assertEquals(501L, hits.get(0).getImageId());
        assertEquals(12L, hits.get(0).getCaseId());
        assertEquals(100L, hits.get(0).getCategoryL1Id());
        assertEquals(101L, hits.get(0).getCategoryL2Id());
        assertEquals(0.91, hits.get(0).getScore(), 0.0001);
        server.verify();
    }

    private static CpsMilvusProperties milvusProperties() {
        CpsMilvusProperties properties = new CpsMilvusProperties();
        properties.setBaseUrl("http://vector.test");
        properties.setApiKey("vector-key");
        properties.setCollection("cps_vectors");
        properties.setMetricType("COSINE");
        properties.setIndexType("HNSW");
        return properties;
    }

    private static CpsAiProperties aiProperties() {
        CpsAiProperties properties = new CpsAiProperties();
        CpsAiProperties.Embedding embedding = new CpsAiProperties.Embedding();
        embedding.setDimension(3);
        properties.setEmbedding(embedding);
        return properties;
    }
}
