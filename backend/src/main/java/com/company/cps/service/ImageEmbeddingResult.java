package com.company.cps.service;

import java.util.List;

public class ImageEmbeddingResult {

    private final List<Float> vector;
    private final String model;
    private final String version;
    private final int dimension;
    private final String rawRequest;
    private final String rawResponse;

    public ImageEmbeddingResult(List<Float> vector, String model, String version, int dimension, String rawRequest, String rawResponse) {
        this.vector = vector;
        this.model = model;
        this.version = version;
        this.dimension = dimension;
        this.rawRequest = rawRequest;
        this.rawResponse = rawResponse;
    }

    public List<Float> getVector() {
        return vector;
    }

    public String getModel() {
        return model;
    }

    public String getVersion() {
        return version;
    }

    public int getDimension() {
        return dimension;
    }

    public String getRawRequest() {
        return rawRequest;
    }

    public String getRawResponse() {
        return rawResponse;
    }
}
