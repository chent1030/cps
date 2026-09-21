package com.company.cps.controller;

import com.company.cps.domain.CpsKnowledgeCase;
import com.company.cps.domain.CpsKnowledgeCaseImage;
import com.company.cps.dto.CpsKnowledgeCaseImageRequest;
import com.company.cps.dto.CpsKnowledgeCaseRequest;
import com.company.cps.dto.CpsKnowledgeVectorSyncResponse;
import com.company.cps.dto.CpsKnowledgeMaterialRequest;
import com.company.cps.dto.CpsAdminPageResponse;
import com.company.cps.service.CpsKnowledgeAdminService;
import com.company.cps.support.CpsExcelWriter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;

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

    @GetMapping("/cases/page")
    public CpsAdminPageResponse<CpsKnowledgeCase> pageCases(
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return knowledgeAdminService.pageCases(enabled, category, page, pageSize);
    }

    @GetMapping("/cases/export")
    public void exportCases(
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String category,
            HttpServletResponse response
    ) throws IOException {
        List<CpsKnowledgeCase> records = knowledgeAdminService.exportCases(enabled, category);
        List<String[]> rows = new ArrayList<>();
        for (CpsKnowledgeCase item : records) {
            rows.add(new String[] {
                    item.getCategoryL1Name(), item.getCategoryL2Name(),
                    Boolean.TRUE.equals(item.getEnabled()) ? "启用" : "停用"
            });
        }
        CpsExcelWriter.write(response, "cps-knowledge-cases.xlsx", "案例知识库",
                new String[] {"一级分类", "二级分类", "状态"}, rows);
    }

    /** 新增或更新知识库案例分类归集信息。 */
    @PostMapping("/cases")
    public CpsKnowledgeCase saveCase(
            @RequestBody CpsKnowledgeCaseRequest request
    ) {
        return knowledgeAdminService.saveCase(request, resolveCurrentEmpNo(request.getEmpNo()));
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

    @PostMapping("/materials")
    public CpsKnowledgeCaseImage saveMaterial(@RequestBody CpsKnowledgeMaterialRequest request) { return knowledgeAdminService.saveMaterial(request, resolveCurrentEmpNo(request.getEmpNo())); }

    @PostMapping(value = "/materials/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CpsKnowledgeCaseImage uploadMaterial(
            @RequestParam("file") MultipartFile file,
            @ModelAttribute CpsKnowledgeMaterialRequest request
    ) {
        return knowledgeAdminService.uploadMaterial(file, request, resolveCurrentEmpNo(request.getEmpNo()));
    }

    @PostMapping("/cases/{caseId}/sync-vectors")
    public CpsKnowledgeVectorSyncResponse syncCaseVectors(@PathVariable Long caseId) { return knowledgeAdminService.syncCaseVectors(caseId); }

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
