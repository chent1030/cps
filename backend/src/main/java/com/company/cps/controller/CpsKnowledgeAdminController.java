package com.company.cps.controller;

import com.company.cps.domain.CpsKnowledgeCase;
import com.company.cps.domain.CpsKnowledgeCaseImage;
import com.company.cps.dto.CpsKnowledgeCaseImageRequest;
import com.company.cps.dto.CpsKnowledgeCaseRequest;
import com.company.cps.dto.CpsKnowledgeVectorSyncResponse;
import com.company.cps.service.CpsKnowledgeAdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cps/admin/knowledge")
public class CpsKnowledgeAdminController {

    private final CpsKnowledgeAdminService knowledgeAdminService;

    public CpsKnowledgeAdminController(CpsKnowledgeAdminService knowledgeAdminService) {
        this.knowledgeAdminService = knowledgeAdminService;
    }

    /**
     * 查询知识库案例维护列表，可按启停状态过滤；原因和措施不在案例主表返回。
     */
    @GetMapping("/cases")
    public List<CpsKnowledgeCase> cases(@RequestParam(required = false) Boolean enabled) {
        return knowledgeAdminService.listCases(enabled);
    }

    /**
     * 新增或更新知识库案例主信息；分类、标题和适用区域保存在案例主表。
     */
    @PostMapping("/cases")
    public CpsKnowledgeCase saveCase(
            @RequestBody CpsKnowledgeCaseRequest request,
            @RequestHeader(value = "X-Emp-No", required = false) String empNo
    ) {
        return knowledgeAdminService.saveCase(request, resolveCurrentEmpNo(empNo));
    }

    /**
     * 启用或停用知识库案例；停用后移动端 AI 匹配不会主动加载该案例的素材。
     */
    @PatchMapping("/cases/{id}/enabled")
    public void setCaseEnabled(@PathVariable Long id, @RequestParam Boolean enabled) {
        knowledgeAdminService.setCaseEnabled(id, enabled);
    }

    /**
     * 查询指定知识库案例下的素材图片列表，原因和措施跟随每张素材图片返回。
     */
    @GetMapping("/cases/{caseId}/images")
    public List<CpsKnowledgeCaseImage> images(@PathVariable Long caseId) {
        return knowledgeAdminService.listImages(caseId);
    }

    /**
     * 新增或更新知识库素材图片；保存后素材会被标记为待同步向量。
     */
    @PostMapping("/images")
    public CpsKnowledgeCaseImage saveImage(@RequestBody CpsKnowledgeCaseImageRequest request) {
        return knowledgeAdminService.saveImage(request);
    }

    /**
     * 立即同步单张素材图片到向量库，用于素材保存后手动刷新或失败重试。
     */
    @PostMapping("/images/{imageId}/sync-vector")
    public CpsKnowledgeVectorSyncResponse syncOneImageVector(@PathVariable Long imageId) {
        return knowledgeAdminService.syncOneImageVector(imageId);
    }

    /**
     * 批量同步待同步或维度不一致的素材图片到向量库，默认最多处理 200 张。
     */
    @PostMapping("/images/sync-vectors")
    public CpsKnowledgeVectorSyncResponse syncChangedImageVectors(@RequestParam(required = false) Integer limit) {
        return knowledgeAdminService.syncChangedImageVectors(limit);
    }

    private String resolveCurrentEmpNo(String empNo) {
        if (empNo != null && !empNo.trim().isEmpty()) {
            return empNo.trim();
        }
        return "DEV_EMP";
    }
}
