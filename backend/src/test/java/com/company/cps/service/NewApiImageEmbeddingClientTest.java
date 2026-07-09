package com.company.cps.service;

import com.company.cps.config.CpsAiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NewApiImageEmbeddingClientTest {

    @Test
    void embedImageCallsNewApiAndParsesVector() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        CpsAiProperties properties = properties(3);
        NewApiImageEmbeddingClient client = new NewApiImageEmbeddingClient(restTemplate, properties);

        server.expect(requestTo("http://newapi.test/v1/embeddings"))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("google/siglip2-so400m-patch14-384"))
                .andExpect(jsonPath("$.input[0].image_url").value("https://files.test/a.jpg"))
                .andRespond(withSuccess("{\n"
                        + "  \"data\": [\n"
                        + "    {\n"
                        + "      \"embedding\": [0.1, 0.2, 0.3]\n"
                        + "    }\n"
                        + "  ]\n"
                        + "}", MediaType.APPLICATION_JSON));

        ImageEmbeddingResult result = client.embedImage("https://files.test/a.jpg");

        assertEquals(Arrays.asList(0.1f, 0.2f, 0.3f), result.getVector());
        assertEquals("google/siglip2-so400m-patch14-384", result.getModel());
        assertEquals("siglip2-v1", result.getVersion());
        assertEquals(3, result.getDimension());
        server.verify();
    }

    @Test
    void embedImageRejectsUnexpectedDimension() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        NewApiImageEmbeddingClient client = new NewApiImageEmbeddingClient(restTemplate, properties(4));

        server.expect(requestTo("http://newapi.test/v1/embeddings"))
                .andRespond(withSuccess("{\n"
                        + "  \"data\": [\n"
                        + "    {\n"
                        + "      \"embedding\": [0.1, 0.2, 0.3]\n"
                        + "    }\n"
                        + "  ]\n"
                        + "}", MediaType.APPLICATION_JSON));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> client.embedImage("https://files.test/a.jpg")
        );

        assertEquals("Embedding dimension mismatch: expected 4 but got 3", error.getMessage());
    }

    private static CpsAiProperties properties(int dimension) {
        CpsAiProperties properties = new CpsAiProperties();
        CpsAiProperties.Embedding embedding = new CpsAiProperties.Embedding();
        embedding.setBaseUrl("http://newapi.test");
        embedding.setApiKey("test-key");
        embedding.setModel("google/siglip2-so400m-patch14-384");
        embedding.setModelVersion("siglip2-v1");
        embedding.setDimension(dimension);
        embedding.setEndpoint("/v1/embeddings");
        properties.setEmbedding(embedding);
        return properties;
    }
}
