package com.company.cps.service;

import com.company.cps.domain.CpsKnowledgeCase;
import com.company.cps.domain.CpsKnowledgeCaseImage;
import com.company.cps.dto.CpsKnowledgeCaseImageRequest;
import com.company.cps.mapper.CpsKnowledgeCaseImageMapper;
import com.company.cps.mapper.CpsKnowledgeCaseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CpsKnowledgeAdminServiceTest {

    @Mock
    private CpsKnowledgeCaseMapper caseMapper;
    @Mock
    private CpsKnowledgeCaseImageMapper imageMapper;
    @Mock
    private KnowledgeVectorSyncService vectorSyncService;

    private CpsKnowledgeAdminService service;

    @BeforeEach
    void setUp() {
        service = new CpsKnowledgeAdminService(caseMapper, imageMapper, vectorSyncService);
    }

    @Test
    void saveImageStoresReasonAndMeasureOnMaterialImage() {
        CpsKnowledgeCase knowledgeCase = new CpsKnowledgeCase();
        knowledgeCase.setId(12L);
        when(caseMapper.findById(12L)).thenReturn(Optional.of(knowledgeCase));
        when(imageMapper.findById(501L)).thenReturn(Optional.of(savedImage()));

        CpsKnowledgeCaseImageRequest request = new CpsKnowledgeCaseImageRequest();
        request.setId(501L);
        request.setCaseId(12L);
        request.setFileUrl("https://files.test/std.jpg");
        request.setFileName("std.jpg");
        request.setFileHash("hash-1");
        request.setSortNo(2);
        request.setReason("Label was not added");
        request.setMeasure("Add standard label");

        CpsKnowledgeCaseImage response = service.saveImage(request);

        ArgumentCaptor<CpsKnowledgeCaseImage> captor = ArgumentCaptor.forClass(CpsKnowledgeCaseImage.class);
        verify(imageMapper).upsert(captor.capture());
        assertEquals(12L, captor.getValue().getCaseId());
        assertEquals("Label was not added", captor.getValue().getReason());
        assertEquals("Add standard label", captor.getValue().getMeasure());
        assertEquals("Label was not added", response.getReason());
        assertEquals("Add standard label", response.getMeasure());
    }

    private static CpsKnowledgeCaseImage savedImage() {
        CpsKnowledgeCaseImage image = new CpsKnowledgeCaseImage();
        image.setId(501L);
        image.setCaseId(12L);
        image.setReason("Label was not added");
        image.setMeasure("Add standard label");
        return image;
    }
}
