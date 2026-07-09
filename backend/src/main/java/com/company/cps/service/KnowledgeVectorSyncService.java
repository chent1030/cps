package com.company.cps.service;

import com.company.cps.config.CpsAiProperties;
import com.company.cps.domain.CpsKnowledgeCase;
import com.company.cps.domain.CpsKnowledgeCaseImage;
import com.company.cps.mapper.CpsKnowledgeCaseImageMapper;
import com.company.cps.mapper.CpsKnowledgeCaseMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeVectorSyncService {

    private static final int DEFAULT_BATCH_SIZE = 200;

    private final CpsKnowledgeCaseImageMapper imageMapper;
    private final CpsKnowledgeCaseMapper caseMapper;
    private final ImageEmbeddingClient embeddingClient;
    private final MilvusVectorService milvusVectorService;
    private final CpsAiProperties aiProperties;

    public KnowledgeVectorSyncService(
            CpsKnowledgeCaseImageMapper imageMapper,
            CpsKnowledgeCaseMapper caseMapper,
            ImageEmbeddingClient embeddingClient,
            MilvusVectorService milvusVectorService,
            CpsAiProperties aiProperties
    ) {
        this.imageMapper = imageMapper;
        this.caseMapper = caseMapper;
        this.embeddingClient = embeddingClient;
        this.milvusVectorService = milvusVectorService;
        this.aiProperties = aiProperties;
    }

    public void bootstrap() {
        milvusVectorService.ensureCollection();
        syncChangedImages(DEFAULT_BATCH_SIZE);
        milvusVectorService.loadCollection();
    }

    public int syncChangedImages(int limit) {
        CpsAiProperties.Embedding embedding = aiProperties.getEmbedding();
        List<CpsKnowledgeCaseImage> candidates = imageMapper.findSyncCandidates(
                embedding.getDimension(),
                limit
        );
        for (CpsKnowledgeCaseImage image : candidates) {
            syncImage(image);
        }
        return candidates.size();
    }

    public void syncOneImage(Long imageId) {
        CpsKnowledgeCaseImage image = imageMapper.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge image not found: " + imageId));
        syncImage(image);
    }

    private void syncImage(CpsKnowledgeCaseImage image) {
        imageMapper.markVectorProcessing(image.getId());
        try {
            CpsKnowledgeCase knowledgeCase = caseMapper.findById(image.getCaseId())
                    .orElseThrow(() -> new IllegalArgumentException("Knowledge case not found: " + image.getCaseId()));
            ImageEmbeddingResult embedding = embeddingClient.embedImage(image.getFileUrl());
            milvusVectorService.upsertKnowledgeImage(
                    image.getId(),
                    image.getCaseId(),
                    knowledgeCase.getCategoryL1Id(),
                    knowledgeCase.getCategoryL2Id(),
                    knowledgeCase.getEnabled() == null || Boolean.TRUE.equals(knowledgeCase.getEnabled()),
                    embedding.getVector()
            );
            imageMapper.markVectorSuccess(
                    image.getId(),
                    String.valueOf(image.getId()),
                    embedding.getDimension()
            );
        } catch (RuntimeException e) {
            imageMapper.markVectorFailed(image.getId(), limitError(e.getMessage()));
        }
    }

    private static String limitError(String message) {
        if (isBlank(message)) {
            return "unknown vector sync error";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
