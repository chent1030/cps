package com.company.cps.controller;

import com.company.cps.dto.CpsAttachmentUploadResponse;
import com.company.cps.domain.CpsIssueAttachment;
import com.company.cps.service.CpsAttachmentService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/cps/attachments")
public class CpsAttachmentController {

    private final CpsAttachmentService attachmentService;

    public CpsAttachmentController(CpsAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /**
     * 上传问题或凭证图片，先生成未绑定问题的附件记录，返回附件 ID 供创建问题或节点操作绑定。
     */
    @PostMapping
    public CpsAttachmentUploadResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("empNo") String empNo
    ) {
        CpsAttachmentUploadResponse response = attachmentService.upload(file, resolveCurrentEmpNo(empNo));
        response.setUrl("/api/cps/attachments/" + response.getId() + "/content");
        return response;
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable Long id) {
        return attachmentService.content(id);
    }

    private ResponseEntity<byte[]> toResponse(CpsIssueAttachment attachment) {
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(attachment.getFileType());
        } catch (IllegalArgumentException exception) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok().contentType(mediaType).body(attachment.getContent());
    }

    private String resolveCurrentEmpNo(String empNo) {
        if (empNo != null && !empNo.trim().isEmpty()) {
            return empNo.trim();
        }
        return "DEV_EMP";
    }
}
