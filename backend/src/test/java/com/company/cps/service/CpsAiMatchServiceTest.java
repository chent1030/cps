package com.company.cps.service;

import com.company.cps.domain.CpsIssueAiMatch;
import com.company.cps.domain.CpsIssueAttachment;
import com.company.cps.domain.CpsKnowledgeCase;
import com.company.cps.domain.CpsKnowledgeCaseImage;
import com.company.cps.dto.CpsKnowledgeMatchRequest;
import com.company.cps.dto.CpsKnowledgeMatchResponse;
import com.company.cps.mapper.CpsIssueAiMatchMapper;
import com.company.cps.mapper.CpsIssueAttachmentMapper;
import com.company.cps.mapper.CpsKnowledgeCaseMapper;
import com.company.cps.mapper.CpsKnowledgeCaseImageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CpsAiMatchServiceTest {

    @Mock
    private CpsIssueAttachmentMapper attachmentMapper;
    @Mock
    private CpsKnowledgeCaseImageMapper imageMapper;
    @Mock
    private CpsKnowledgeCaseMapper caseMapper;
    @Mock
    private CpsIssueAiMatchMapper matchMapper;
    @Mock
    private ImageEmbeddingClient embeddingClient;
    @Mock
    private MilvusVectorService milvusVectorService;

    private CpsAiMatchService service;

    @BeforeEach
    void setUp() {
        service = new CpsAiMatchService(
                attachmentMapper,
                imageMapper,
                caseMapper,
                matchMapper,
                embeddingClient,
                milvusVectorService
        );
    }

    @Test
    void matchKnowledgeReturnsBestImageAndPersistsImageBoundSuggestion() {
        CpsIssueAttachment attachment = new CpsIssueAttachment();
        attachment.setId(501L);
        attachment.setFileUrl("https://files.test/issue.jpg");
        when(attachmentMapper.findById(501L)).thenReturn(Optional.of(attachment));
        List<Float> vector = Arrays.asList(0.1f, 0.2f, 0.3f);
        when(embeddingClient.embedImage("https://files.test/issue.jpg"))
                .thenReturn(new ImageEmbeddingResult(vector, "siglip2", "v1", 3, "{}", "{}"));
        when(milvusVectorService.searchSimilarImages(vector, 10))
                .thenReturn(Arrays.asList(
                        new MilvusSearchHit(901L, 12L, 100L, 101L, 0.82),
                        new MilvusSearchHit(902L, 12L, 100L, 101L, 0.91),
                        new MilvusSearchHit(903L, 13L, 200L, 201L, 0.88)
                ));
        when(imageMapper.findEnabledByIds(Arrays.asList(902L, 903L)))
                .thenReturn(Arrays.asList(imageData(902L), imageData(903L)));
        when(caseMapper.findEnabledByIds(Arrays.asList(12L, 13L)))
                .thenReturn(Arrays.asList(caseData(12L), caseData(13L)));

        CpsKnowledgeMatchRequest request = new CpsKnowledgeMatchRequest();
        request.setAttachmentId(501L);
        CpsKnowledgeMatchResponse response = service.matchKnowledge(request);

        assertEquals(12L, response.getMatchedCaseId());
        assertEquals(902L, response.getMatchedImageId());
        assertEquals(new BigDecimal("0.9100"), response.getConfidence());
        assertEquals(100L, response.getCategoryL1Id());
        assertEquals(101L, response.getCategoryL2Id());
        assertEquals("Site 5S", response.getCategoryL1Name());
        assertEquals("Missing label", response.getCategoryL2Name());
        assertEquals("Label was not added", response.getReasonSuggestion());
        assertEquals("Add standard label", response.getMeasureSuggestion());
        assertEquals(2, response.getMatchedCases().size());
        assertEquals(902L, response.getMatchedCases().get(0).getImageId());

        ArgumentCaptor<CpsIssueAiMatch> matchCaptor = ArgumentCaptor.forClass(CpsIssueAiMatch.class);
        verify(matchMapper).insert(matchCaptor.capture());
        CpsIssueAiMatch saved = matchCaptor.getValue();
        assertEquals(501L, saved.getSourceAttachmentId());
        assertEquals(12L, saved.getMatchedCaseId());
        assertTrue(saved.getTopkJson().contains("\"imageId\":902"));
        verify(matchMapper).insert(any());
    }

    private static CpsKnowledgeCaseImage imageData(Long imageId) {
        boolean best = imageId == 902L;
        CpsKnowledgeCaseImage item = new CpsKnowledgeCaseImage();
        item.setId(imageId);
        item.setCaseId(best ? 12L : 13L);
        item.setReason(best ? "Label was not added" : "Surface was scratched");
        item.setMeasure(best ? "Add standard label" : "Isolate and repair");
        return item;
    }

    private static CpsKnowledgeCase caseData(Long caseId) {
        boolean best = caseId == 12L;
        CpsKnowledgeCase item = new CpsKnowledgeCase();
        item.setId(caseId);
        item.setCategoryL1Id(best ? 100L : 200L);
        item.setCategoryL2Id(best ? 101L : 201L);
        item.setCategoryL1Name(best ? "Site 5S" : "Quality issue");
        item.setCategoryL2Name(best ? "Missing label" : "Appearance defect");
        return item;
    }
}
