package com.company.cps.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "cps.milvus", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopMilvusVectorService implements MilvusVectorService {
    @Override
    public void ensureCollection() {
    }

    @Override
    public void loadCollection() {
    }

    @Override
    public void upsertKnowledgeImage(Long imageId, Long caseId, Long categoryL1Id, Long categoryL2Id, boolean enabled, List<Float> vector) {
    }

    @Override
    public List<MilvusSearchHit> searchSimilarImages(List<Float> vector, int topK) {
        return Collections.emptyList();
    }
}
