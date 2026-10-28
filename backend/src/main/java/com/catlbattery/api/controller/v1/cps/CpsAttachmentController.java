package com.catlbattery.api.controller.v1.cps;

import com.catlbattery.domain.entity.cps.dto.CpsAttachmentUploadResponse;
import com.catlbattery.domain.entity.cps.CpsIssueAttachment;
import com.catlbattery.app.service.cps.CpsAttachmentService;
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
        // url 直接返回 MinIO 公开地址（与 file_url 同值），不再拼接 /api/cps/attachments/{id}/content。
        return attachmentService.upload(file, resolveCurrentEmpNo(empNo));
    }

    /** 按附件 id 直出字节（file_url 已是可直接访问的 MinIO 公开 URL，前端正常不调这个）。 */
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
