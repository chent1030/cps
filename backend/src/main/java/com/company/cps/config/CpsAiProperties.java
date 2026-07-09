package com.company.cps.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cps.ai")
public class CpsAiProperties {
    private Embedding embedding = new Embedding();

    public Embedding getEmbedding() {
        return embedding;
    }

    public void setEmbedding(Embedding embedding) {
        this.embedding = embedding;
    }

    public static class Embedding {
        private String provider = "newapi";
        private String baseUrl;
        private String endpoint = "/v1/embeddings";
        private String apiKey;
        private String model = "google/siglip2-so400m-patch14-384";
        private String modelVersion = "siglip2-v1";
        private Integer dimension = 1152;
        private Integer timeoutMs = 30000;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getModelVersion() {
            return modelVersion;
        }

        public void setModelVersion(String modelVersion) {
            this.modelVersion = modelVersion;
        }

        public Integer getDimension() {
            return dimension;
        }

        public void setDimension(Integer dimension) {
            this.dimension = dimension;
        }

        public Integer getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(Integer timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }
}
