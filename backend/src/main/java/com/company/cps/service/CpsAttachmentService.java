package com.company.cps.service;

import com.company.cps.domain.CpsIssueAttachment;
import com.company.cps.dto.CpsAttachmentUploadResponse;
import com.company.cps.mapper.CpsIssueAttachmentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Service
public class CpsAttachmentService {

    private final CpsIssueAttachmentMapper attachmentMapper;

    public CpsAttachmentService(CpsIssueAttachmentMapper attachmentMapper) {
        this.attachmentMapper = attachmentMapper;
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
        attachment.setFileUrl(buildFileUrl(fileName));
        attachment.setSortNo(null);
        attachment.setCreatedBy(currentEmpNo);
        attachment.setCreatedName(currentEmpNo);
        attachment.setCreatedAt(LocalDateTime.now());
        attachmentMapper.insert(attachment);
        return new CpsAttachmentUploadResponse(attachment.getId(), attachment.getFileUrl(), attachment.getFileName());
    }

    private static String buildFileUrl(String fileName) {
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
