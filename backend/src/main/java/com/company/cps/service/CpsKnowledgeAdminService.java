package com.company.cps.service;

import com.company.cps.domain.CpsKnowledgeCase;
import com.company.cps.domain.CpsKnowledgeCaseImage;
import com.company.cps.dto.CpsKnowledgeCaseImageRequest;
import com.company.cps.dto.CpsKnowledgeCaseRequest;
import com.company.cps.dto.CpsKnowledgeVectorSyncResponse;
import com.company.cps.dto.CpsKnowledgeMaterialRequest;
import com.company.cps.dto.CpsAdminPageResponse;
import com.company.cps.mapper.CpsKnowledgeCaseImageMapper;
import com.company.cps.mapper.CpsKnowledgeCaseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CpsKnowledgeAdminService {

    private static final int DEFAULT_SYNC_LIMIT = 200;
    private static final int MAX_EXPORT_ROWS = 10000;

    private final CpsKnowledgeCaseMapper caseMapper;
    private final CpsKnowledgeCaseImageMapper imageMapper;
    private final KnowledgeVectorSyncService vectorSyncService;
    private final RustFsStorageService storage;

    public CpsKnowledgeAdminService(
            CpsKnowledgeCaseMapper caseMapper,
            CpsKnowledgeCaseImageMapper imageMapper,
            KnowledgeVectorSyncService vectorSyncService,
            RustFsStorageService storage
    ) {
        this.caseMapper = caseMapper;
        this.imageMapper = imageMapper;
        this.vectorSyncService = vectorSyncService;
        this.storage = storage;
    }

    public List<CpsKnowledgeCase> listCases(Boolean enabled) {
        return caseMapper.findAll(enabled);
    }

    public CpsAdminPageResponse<CpsKnowledgeCase> pageCases(
            Boolean enabled, String category, int page, int pageSize
    ) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        String normalizedKeyword = trimToNull(category);
        int offset = (safePage - 1) * safePageSize;
        List<CpsKnowledgeCase> records = caseMapper.findAdminPage(
                enabled, normalizedKeyword, safePageSize, offset
        );
        long total = caseMapper.countAdmin(enabled, normalizedKeyword);
        return new CpsAdminPageResponse<>(records, total, safePage, safePageSize);
    }

    public List<CpsKnowledgeCase> exportCases(Boolean enabled, String category) {
        return caseMapper.findAdminExport(enabled, trimToNull(category), MAX_EXPORT_ROWS);
    }

    @Transactional
    public CpsKnowledgeCase saveCase(CpsKnowledgeCaseRequest request, String currentEmpNo) {
        validateCase(request);
        CpsKnowledgeCase item = new CpsKnowledgeCase();
        item.setId(request.getId());
        item.setCategoryL1Id(request.getCategoryL1Id());
        item.setCategoryL2Id(request.getCategoryL2Id());
        item.setCategoryL1Name(request.getCategoryL1Name().trim());
        item.setCategoryL2Name(request.getCategoryL2Name().trim());
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

    @Transactional
    public CpsKnowledgeCaseImage saveMaterial(CpsKnowledgeMaterialRequest request, String currentEmpNo) {
        requireNonNull(request, "request is required");
        requireNonNull(request.getCategoryL1Id(), "categoryL1Id is required");
        requireNonNull(request.getCategoryL2Id(), "categoryL2Id is required");
        requireText(request.getCategoryL1Name(), "categoryL1Name is required"); requireText(request.getCategoryL2Name(), "categoryL2Name is required");
        CpsKnowledgeCase knowledgeCase = request.getCaseId() == null ? caseMapper.findByCategoryL2Id(request.getCategoryL2Id()).orElse(null) : caseMapper.findById(request.getCaseId()).orElse(null);
        if (request.getCaseId() != null && knowledgeCase == null) {
            throw new IllegalArgumentException("knowledge case not found: " + request.getCaseId());
        }
        if (knowledgeCase != null && !request.getCategoryL2Id().equals(knowledgeCase.getCategoryL2Id())) {
            throw new IllegalArgumentException("material category does not match knowledge case");
        }
        if (knowledgeCase == null) {
            CpsKnowledgeCaseRequest caseRequest = new CpsKnowledgeCaseRequest(); caseRequest.setCategoryL1Id(request.getCategoryL1Id()); caseRequest.setCategoryL2Id(request.getCategoryL2Id()); caseRequest.setCategoryL1Name(request.getCategoryL1Name()); caseRequest.setCategoryL2Name(request.getCategoryL2Name());
            knowledgeCase = saveCase(caseRequest, currentEmpNo);
        }
        CpsKnowledgeCaseImageRequest image = new CpsKnowledgeCaseImageRequest(); image.setCaseId(knowledgeCase.getId()); image.setFileUrl(request.getFileUrl()); image.setFileName(request.getFileName()); image.setReason(request.getReason()); image.setMeasure(request.getMeasure());
        return saveImage(image);
    }

    /** 上传并保存一条知识库素材，不写入问题附件表。 */
    @Transactional
    public CpsKnowledgeCaseImage uploadMaterial(
            MultipartFile file, CpsKnowledgeMaterialRequest request, String currentEmpNo
    ) {
        validateMaterialFile(file);
        String fileName = trimToNull(file.getOriginalFilename());
        if (fileName == null) {
            fileName = "knowledge-material";
        }
        String objectKey = "cps/knowledge/" + UUID.randomUUID() + "-"
                + fileName.replace('\\', '_').replace('/', '_');
        try {
            storage.put(objectKey, file.getBytes(), file.getContentType());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to store knowledge material", exception);
        }
        request.setFileName(fileName);
        request.setFileUrl(storage.publicObjectUrl(objectKey));
        return saveMaterial(request, currentEmpNo);
    }

    public CpsKnowledgeVectorSyncResponse syncCaseVectors(Long caseId) { List<CpsKnowledgeCaseImage> images = listImages(caseId); for (CpsKnowledgeCaseImage image : images) vectorSyncService.syncOneImage(image.getId()); return new CpsKnowledgeVectorSyncResponse(images.size()); }

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

    private static void validateMaterialFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("material file is required");
        }
        if (file.getSize() > 20L * 1024 * 1024) {
            throw new IllegalArgumentException("material file must not exceed 20 MB");
        }
        String contentType = trimToNull(file.getContentType());
        if (contentType == null || !Set.of("image/jpeg", "image/png", "image/webp").contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("only JPEG, PNG and WebP material images are supported");
        }
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
