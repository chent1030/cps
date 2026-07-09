package com.company.cps.service;

import com.company.cps.domain.CpsKnowledgeCase;
import com.company.cps.domain.CpsKnowledgeCaseImage;
import com.company.cps.dto.CpsKnowledgeCaseImageRequest;
import com.company.cps.dto.CpsKnowledgeCaseRequest;
import com.company.cps.dto.CpsKnowledgeVectorSyncResponse;
import com.company.cps.mapper.CpsKnowledgeCaseImageMapper;
import com.company.cps.mapper.CpsKnowledgeCaseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CpsKnowledgeAdminService {

    private static final int DEFAULT_SYNC_LIMIT = 200;

    private final CpsKnowledgeCaseMapper caseMapper;
    private final CpsKnowledgeCaseImageMapper imageMapper;
    private final KnowledgeVectorSyncService vectorSyncService;

    public CpsKnowledgeAdminService(
            CpsKnowledgeCaseMapper caseMapper,
            CpsKnowledgeCaseImageMapper imageMapper,
            KnowledgeVectorSyncService vectorSyncService
    ) {
        this.caseMapper = caseMapper;
        this.imageMapper = imageMapper;
        this.vectorSyncService = vectorSyncService;
    }

    public List<CpsKnowledgeCase> listCases(Boolean enabled) {
        return caseMapper.findAll(enabled);
    }

    @Transactional
    public CpsKnowledgeCase saveCase(CpsKnowledgeCaseRequest request, String currentEmpNo) {
        validateCase(request);
        CpsKnowledgeCase item = new CpsKnowledgeCase();
        item.setId(request.getId());
        item.setCaseCode(request.getCaseCode().trim());
        item.setCaseTitle(trimToNull(request.getCaseTitle()));
        item.setCategoryL1Id(request.getCategoryL1Id());
        item.setCategoryL2Id(request.getCategoryL2Id());
        item.setCategoryL1Name(request.getCategoryL1Name().trim());
        item.setCategoryL2Name(request.getCategoryL2Name().trim());
        item.setScopeRemark(trimToNull(request.getScopeRemark()));
        item.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        item.setCreatedBy(currentEmpNo);
        caseMapper.upsert(item);
        return item;
    }

    @Transactional
    public void setCaseEnabled(Long id, Boolean enabled) {
        requireNonNull(id, "id is required");
        requireNonNull(enabled, "enabled is required");
        int updated = caseMapper.setEnabled(id, enabled);
        if (updated != 1) {
            throw new IllegalArgumentException("knowledge case not found: " + id);
        }
    }

    public List<CpsKnowledgeCaseImage> listImages(Long caseId) {
        requireNonNull(caseId, "caseId is required");
        return imageMapper.findByCaseId(caseId);
    }

    @Transactional
    public CpsKnowledgeCaseImage saveImage(CpsKnowledgeCaseImageRequest request) {
        validateImage(request);
        caseMapper.findById(request.getCaseId())
                .orElseThrow(() -> new IllegalArgumentException("knowledge case not found: " + request.getCaseId()));
        CpsKnowledgeCaseImage item = new CpsKnowledgeCaseImage();
        item.setId(request.getId());
        item.setCaseId(request.getCaseId());
        item.setFileUrl(request.getFileUrl().trim());
        item.setFileName(trimToNull(request.getFileName()));
        item.setFileHash(trimToNull(request.getFileHash()));
        item.setSortNo(request.getSortNo() == null ? 1 : request.getSortNo());
        item.setReason(request.getReason().trim());
        item.setMeasure(request.getMeasure().trim());
        imageMapper.upsert(item);
        return imageMapper.findById(item.getId()).orElse(item);
    }

    public CpsKnowledgeVectorSyncResponse syncOneImageVector(Long imageId) {
        requireNonNull(imageId, "imageId is required");
        vectorSyncService.syncOneImage(imageId);
        return new CpsKnowledgeVectorSyncResponse(1);
    }

    public CpsKnowledgeVectorSyncResponse syncChangedImageVectors(Integer limit) {
        int actualLimit = limit == null || limit <= 0 ? DEFAULT_SYNC_LIMIT : limit;
        return new CpsKnowledgeVectorSyncResponse(vectorSyncService.syncChangedImages(actualLimit));
    }

    private static void validateCase(CpsKnowledgeCaseRequest request) {
        requireNonNull(request, "request is required");
        requireText(request.getCaseCode(), "caseCode is required");
        requireNonNull(request.getCategoryL1Id(), "categoryL1Id is required");
        requireNonNull(request.getCategoryL2Id(), "categoryL2Id is required");
        requireText(request.getCategoryL1Name(), "categoryL1Name is required");
        requireText(request.getCategoryL2Name(), "categoryL2Name is required");
    }

    private static void validateImage(CpsKnowledgeCaseImageRequest request) {
        requireNonNull(request, "request is required");
        requireNonNull(request.getCaseId(), "caseId is required");
        requireText(request.getFileUrl(), "fileUrl is required");
        requireText(request.getReason(), "reason is required");
        requireText(request.getMeasure(), "measure is required");
    }

    private static void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireText(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
