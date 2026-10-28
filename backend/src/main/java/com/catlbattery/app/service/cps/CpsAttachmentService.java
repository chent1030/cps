package com.catlbattery.app.service.cps;

import com.catlbattery.domain.entity.cps.CpsIssueAttachment;
import com.catlbattery.domain.entity.cps.dto.CpsAttachmentUploadResponse;
import com.catlbattery.infra.mapper.cps.CpsIssueAttachmentMapper;
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
    private final MinioStorageService storage;

    public CpsAttachmentService(CpsIssueAttachmentMapper attachmentMapper, MinioStorageService storage) {
        this.attachmentMapper = attachmentMapper;
        this.storage = storage;
    }

    /**
     * 上传附件到 MinIO：file_url 直接存可访问的绝对 URL（{public-url}/{bucket}/{objectKey}），
     * 返回给前端的 url 与该 file_url 一致，不再拼接 /api/cps/attachments/{id}/content。
     */
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
        try {
            byte[] content = file.getBytes();
            storage.put(objectKey, content, fileType);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to store CPS attachment in MinIO", error);
        }
        attachment.setFileUrl(storage.publicObjectUrl(objectKey));
        attachment.setSortNo(null);
        attachment.setCreatedBy(currentEmpNo);
        attachment.setCreatedName(currentEmpNo);
        attachment.setCreatedAt(LocalDateTime.now());
        attachmentMapper.insert(attachment);
        return new CpsAttachmentUploadResponse(attachment.getId(), attachment.getFileUrl(), attachment.getFileName());
    }

    @Transactional(readOnly = true)
    public Optional<CpsIssueAttachment> findById(Long id) {
        return attachmentMapper.findById(id);
    }

    /** 兼容读取：file_url 为绝对 URL（新数据）或裸对象键（历史数据）都能取到内容。 */
    public ResponseEntity<byte[]> content(Long id) {
        try {
            CpsIssueAttachment attachment = findById(id).orElse(null);
            if (attachment == null) return ResponseEntity.notFound().build();
            byte[] content = storage.readByUrl(attachment.getFileUrl());
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(attachment.getFileType())).body(content);
        } catch (Exception error) {
            return ResponseEntity.notFound().build();
        }
    }

    private static String firstNonBlank(String first, String fallback) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        return fallback;
    }
}
