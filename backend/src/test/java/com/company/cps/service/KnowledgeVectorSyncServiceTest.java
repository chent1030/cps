package com.company.cps.service;

import com.company.cps.config.CpsAiProperties;
import com.company.cps.domain.CpsKnowledgeCase;
import com.company.cps.domain.CpsKnowledgeCaseImage;
import com.company.cps.mapper.CpsKnowledgeCaseImageMapper;
import com.company.cps.mapper.CpsKnowledgeCaseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeVectorSyncServiceTest {

    @Mock
    private CpsKnowledgeCaseImageMapper imageMapper;
    @Mock
    private CpsKnowledgeCaseMapper caseMapper;
    @Mock
    private ImageEmbeddingClient embeddingClient;
    @Mock
    private MilvusVectorService milvusVectorService;

    private KnowledgeVectorSyncService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeVectorSyncService(
                imageMapper,
                caseMapper,
                embeddingClient,
                milvusVectorService,
                properties()
        );
    }

    @Test
    void bootstrapEnsuresCollectionAndSyncsCandidates() {
        CpsKnowledgeCaseImage image = image(501L, 12L, "https://files.test/std.jpg");
        List<Float> vector = Arrays.asList(0.1f, 0.2f, 0.3f);
        when(imageMapper.findSyncCandidates(3, 200))
                .thenReturn(Collections.singletonList(image));
        when(caseMapper.findById(12L)).thenReturn(java.util.Optional.of(caseData(12L)));
        when(embeddingClient.embedImage("https://files.test/std.jpg"))
                .thenReturn(new ImageEmbeddingResult(vector, "google/siglip2-so400m-patch14-384", "siglip2-v1", 3, "{}", "{}"));

        service.bootstrap();

        verify(milvusVectorService).ensureCollection();
        verify(imageMapper).markVectorProcessing(501L);
        verify(milvusVectorService).upsertKnowledgeImage(501L, 12L, 100L, 101L, true, vector);
        verify(imageMapper).markVectorSuccess(501L, "501", 3);
        verify(milvusVectorService).loadCollection();
    }

    @Test
    void syncMarksImageFailedWhenEmbeddingFails() {
        CpsKnowledgeCaseImage image = image(502L, 13L, "https://files.test/bad.jpg");
        when(imageMapper.findById(502L)).thenReturn(java.util.Optional.of(image));
        when(caseMapper.findById(13L)).thenReturn(java.util.Optional.of(caseData(13L)));
        when(embeddingClient.embedImage("https://files.test/bad.jpg")).thenThrow(new IllegalStateException("model unavailable"));

        service.syncOneImage(502L);

        verify(imageMapper).markVectorProcessing(502L);
        verify(imageMapper).markVectorFailed(502L, "model unavailable");
    }

    private static CpsKnowledgeCaseImage image(Long id, Long caseId, String fileUrl) {
        CpsKnowledgeCaseImage image = new CpsKnowledgeCaseImage();
        image.setId(id);
        image.setCaseId(caseId);
        image.setFileUrl(fileUrl);
        return image;
    }

    private static CpsKnowledgeCase caseData(Long caseId) {
        CpsKnowledgeCase item = new CpsKnowledgeCase();
        item.setId(caseId);
        item.setCategoryL1Id(100L);
        item.setCategoryL2Id(101L);
        item.setEnabled(Boolean.TRUE);
        return item;
    }

    private static CpsAiProperties properties() {
        CpsAiProperties properties = new CpsAiProperties();
        CpsAiProperties.Embedding embedding = new CpsAiProperties.Embedding();
        embedding.setModel("google/siglip2-so400m-patch14-384");
        embedding.setModelVersion("siglip2-v1");
        embedding.setDimension(3);
        properties.setEmbedding(embedding);
        return properties;
    }
}
