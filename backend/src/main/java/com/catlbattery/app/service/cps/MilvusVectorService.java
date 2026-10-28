package com.catlbattery.app.service.cps;

import java.util.List;

public interface MilvusVectorService {
    void ensureCollection();

    void loadCollection();

    void upsertKnowledgeImage(
            Long imageId,
            Long caseId,
            Long categoryL1Id,
            Long categoryL2Id,
            boolean enabled,
            List<Float> vector
    );

    List<MilvusSearchHit> searchSimilarImages(List<Float> vector, int topK);
}
