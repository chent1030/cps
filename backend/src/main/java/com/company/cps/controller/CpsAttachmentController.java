package com.company.cps.controller;

import com.company.cps.dto.CpsAttachmentUploadResponse;
import com.company.cps.service.CpsAttachmentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
            @RequestHeader(value = "X-Emp-No", required = false) String empNo
    ) {
        return attachmentService.upload(file, resolveCurrentEmpNo(empNo));
    }

    private String resolveCurrentEmpNo(String empNo) {
        if (empNo != null && !empNo.trim().isEmpty()) {
            return empNo.trim();
        }
        return "DEV_EMP";
    }
}
