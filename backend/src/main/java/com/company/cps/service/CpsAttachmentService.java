package com.company.cps.service;

import com.company.cps.domain.CpsIssueAttachment;
import com.company.cps.dto.CpsAttachmentUploadResponse;
import com.company.cps.mapper.CpsIssueAttachmentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Service
public class CpsAttachmentService {

    private final CpsIssueAttachmentMapper attachmentMapper;
    private final RustFsStorageService storage;

    public CpsAttachmentService(CpsIssueAttachmentMapper attachmentMapper, RustFsStorageService storage) {
        this.attachmentMapper = attachmentMapper;
        this.storage = storage;
    }

    @Transactional
    public CpsAttachmentUploadResponse upload(MultipartFile file, String currentEmpNo) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        String fileName = firstNonBlank(file.getOriginalFilename(), "upload-image");
        String fileType = firstNonBlank(file.getContentType(), "application/octet-stream");
        CpsIssueAttachment attachment = new CpsIssueAttachment();
        attachment.setIssueId(null);
        attachment.setStage(null);
        attachment.setFileName(fileName);
        attachment.setFileType(fileType);
        String objectKey = "cps/" + java.util.UUID.randomUUID() + "-" + fileName.replace('\\', '_').replace('/', '_');
        attachment.setFileUrl(objectKey);
        attachment.setSortNo(null);
        attachment.setCreatedBy(currentEmpNo);
        attachment.setCreatedName(currentEmpNo);
        attachment.setCreatedAt(LocalDateTime.now());
        try {
            byte[] content = file.getBytes();
            storage.put(objectKey, content, fileType);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to store CPS attachment in RustFS", error);
        }
        attachmentMapper.insert(attachment);
        return new CpsAttachmentUploadResponse(attachment.getId(), attachment.getFileUrl(), attachment.getFileName());
    }

    @Transactional(readOnly = true)
    public Optional<CpsIssueAttachment> findById(Long id) {
        return attachmentMapper.findById(id);
    }

    public ResponseEntity<byte[]> content(Long id) {
        try {
            CpsIssueAttachment attachment = findById(id).orElse(null);
            if (attachment == null) return ResponseEntity.notFound().build();
            byte[] content = storage.read(attachment.getFileUrl());
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(attachment.getFileType())).body(content);
        } catch (Exception error) {
            return ResponseEntity.notFound().build();
        }
    }

    private static String buildLegacyFileUrl(String fileName) {
        String safeName = fileName.replace('\\', '_').replace('/', '_');
        return "/uploads/cps/" + System.currentTimeMillis() + "-" + safeName;
    }

    private static String firstNonBlank(String first, String fallback) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        return fallback;
    }
}
