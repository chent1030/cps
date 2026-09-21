package com.company.cps.service;

import com.company.cps.config.CpsAiProperties;
import com.company.cps.domain.CpsKnowledgeCase;
import com.company.cps.domain.CpsKnowledgeCaseImage;
import com.company.cps.mapper.CpsKnowledgeCaseImageMapper;
import com.company.cps.mapper.CpsKnowledgeCaseMapper;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;

@Service
public class KnowledgeVectorSyncService {

    private static final int DEFAULT_BATCH_SIZE = 200;

    private final CpsKnowledgeCaseImageMapper imageMapper;
    private final CpsKnowledgeCaseMapper caseMapper;
    private final ImageEmbeddingClient embeddingClient;
    private final MilvusVectorService milvusVectorService;
    private final CpsAiProperties aiProperties;
    private final RustFsStorageService storage;

    public KnowledgeVectorSyncService(
            CpsKnowledgeCaseImageMapper imageMapper,
            CpsKnowledgeCaseMapper caseMapper,
            ImageEmbeddingClient embeddingClient,
            MilvusVectorService milvusVectorService,
            CpsAiProperties aiProperties,
            RustFsStorageService storage
    ) {
        this.imageMapper = imageMapper;
        this.caseMapper = caseMapper;
        this.embeddingClient = embeddingClient;
        this.milvusVectorService = milvusVectorService;
        this.aiProperties = aiProperties;
        this.storage = storage;
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
            ImageEmbeddingResult embedding = embeddingClient.embedImage(imageInput(image));
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

    /**
     * 新上传的知识库素材存储在 RustFS。读取其二进制并转为 Data URL，避免向量服务
     * 因无法访问对象存储内网地址而把 URL 当作无效图片。
     */
    private String imageInput(CpsKnowledgeCaseImage image) {
        if (!storage.isPublicObjectUrl(image.getFileUrl())) {
            return image.getFileUrl();
        }
        try {
            byte[] content = storage.readPublicObjectUrl(image.getFileUrl());
            if (content == null || content.length == 0) {
                throw new IllegalStateException("knowledge material content is empty");
            }
            return "data:" + mediaType(image.getFileName()) + ";base64,"
                    + Base64.getEncoder().encodeToString(content);
        } catch (Exception exception) {
            throw new IllegalStateException("knowledge material content is unavailable in RustFS", exception);
        }
    }

    private static String mediaType(String fileName) {
        String value = fileName == null ? "" : fileName.toLowerCase();
        if (value.endsWith(".png")) return "image/png";
        if (value.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
