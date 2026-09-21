package com.company.cps.controller;

import com.company.cps.domain.CpsAreaPersonConfig;
import com.company.cps.domain.CpsProblemCategory;
import com.company.cps.dto.CpsAreaPersonConfigRequest;
import com.company.cps.dto.CpsAdminLoginRequest;
import com.company.cps.dto.CpsAdminLoginResponse;
import com.company.cps.dto.CpsProblemCategoryRequest;
import com.company.cps.dto.CpsEnabledRequest;
import com.company.cps.dto.CpsAdminPageResponse;
import com.company.cps.service.CpsMasterDataService;
import com.company.cps.support.CpsExcelWriter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/cps/admin/master")
public class CpsMasterAdminController {

    private final CpsMasterDataService masterDataService;

    public CpsMasterAdminController(CpsMasterDataService masterDataService) {
        this.masterDataService = masterDataService;
    }

    @PostMapping("/auth/login")
    public CpsAdminLoginResponse login(@RequestBody CpsAdminLoginRequest request) {
        return masterDataService.login(request);
    }

    /**
     * 查询问题分类维护列表；parentId 为空时查询一级分类，可按启停状态过滤。
     */
    @GetMapping("/categories")
    public List<CpsProblemCategory> categories(
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) Boolean enabled
    ) {
        return masterDataService.listCategories(parentId, enabled);
    }

    @GetMapping("/categories/page")
    public CpsAdminPageResponse<CpsProblemCategory> pageCategories(
            @RequestParam(required = false) Long parentId,
            @RequestParam(defaultValue = "false") Boolean allLevels,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return masterDataService.pageCategories(parentId, allLevels, enabled, keyword, page, pageSize);
    }

    @GetMapping("/categories/export")
    public void exportCategories(
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            HttpServletResponse response
    ) throws IOException {
        List<CpsProblemCategory> records = masterDataService.exportCategories(parentId, enabled, keyword);
        List<String[]> rows = new ArrayList<>();
        for (CpsProblemCategory item : records) {
            rows.add(new String[] {
                    item.getCategoryName(),
                    item.getCategoryLevel() == null ? "" : item.getCategoryLevel().toString(),
                    item.getSortNo() == null ? "" : item.getSortNo().toString(),
                    Boolean.TRUE.equals(item.getEnabled()) ? "启用" : "停用"
            });
        }
        CpsExcelWriter.write(response, "cps-categories.xlsx", "问题分类",
                new String[] {"分类名称", "层级", "排序值", "状态"}, rows);
    }

    /**
     * 新增或更新问题分类；一级分类 parentId 为空或0，二级分类必须传一级分类ID。
     */
    @PostMapping("/categories")
    public CpsProblemCategory saveCategory(
            @RequestBody CpsProblemCategoryRequest request
    ) {
        return masterDataService.saveCategory(request, resolveCurrentEmpNo(request.getEmpNo()));
    }

    /**
     * 启用或停用问题分类；停用后移动端分类选项不再返回该分类。
     */
    @PatchMapping("/categories/{id}/enabled")
    public void setCategoryEnabled(
            @PathVariable Long id,
            @RequestBody CpsEnabledRequest request
    ) {
        masterDataService.setCategoryEnabled(id, request.getEnabled(), resolveCurrentEmpNo(request.getEmpNo()));
    }

    /**
     * 查询区域人员配置维护列表，可按工厂、区域和启停状态过滤。
     */
    @GetMapping("/area-person-configs")
    public List<CpsAreaPersonConfig> areaPersonConfigs(
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String line,
            @RequestParam(required = false) String process,
            @RequestParam(required = false) Boolean enabled
    ) {
        return masterDataService.listAreaPersonConfigs(factory, area, enabled);
    }

    @GetMapping("/area-person-configs/page")
    public CpsAdminPageResponse<CpsAreaPersonConfig> pageAreaPersonConfigs(
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String line,
            @RequestParam(required = false) String process,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return masterDataService.pageAreaPersonConfigs(factory, area, line, process, enabled, keyword, page, pageSize);
    }

    @GetMapping("/area-person-configs/export")
    public void exportAreaPersonConfigs(
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String line,
            @RequestParam(required = false) String process,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            HttpServletResponse response
    ) throws IOException {
        List<CpsAreaPersonConfig> records = masterDataService.exportAreaPersonConfigs(factory, area, line, process, enabled, keyword);
        List<String[]> rows = new ArrayList<>();
        for (CpsAreaPersonConfig item : records) {
            rows.add(new String[] {
                    item.getFactory(), item.getArea(), item.getLine(), item.getProcess(),
                    item.getEmpNo(), item.getEmpName(), Boolean.TRUE.equals(item.getEnabled()) ? "启用" : "停用"
            });
        }
        CpsExcelWriter.write(response, "cps-area-person-configs.xlsx", "区域人员",
                new String[] {"工厂", "区域", "拉线", "工序", "员工工号", "员工姓名", "状态"}, rows);
    }

    @GetMapping("/area-person-configs/import-template")
    public void downloadAreaPersonImportTemplate(HttpServletResponse response) throws IOException {
        CpsExcelWriter.writeTemplate(response, "区域人员导入模板.xlsx", "区域人员导入",
                new String[] {"工厂", "区域", "拉线", "工序", "员工工号", "员工姓名"});
    }

    /**
     * 新增或更新区域人员配置；同一工厂、区域、拉线、工序范围只保留一条配置。
     */
    @PostMapping("/area-person-configs")
    public CpsAreaPersonConfig saveAreaPersonConfig(
            @RequestBody CpsAreaPersonConfigRequest request
    ) {
        return masterDataService.saveAreaPersonConfig(request);
    }

    /**
     * 启用或停用区域人员配置；停用后不参与反馈人和审核人自动匹配。
     */
    @PatchMapping("/area-person-configs/{id}/enabled")
    public void setAreaPersonConfigEnabled(
            @PathVariable Long id,
            @RequestBody CpsEnabledRequest request
    ) {
        masterDataService.setAreaPersonConfigEnabled(id, request.getEnabled(), resolveCurrentEmpNo(request.getEmpNo()));
    }

    private String resolveCurrentEmpNo(String empNo) {
        if (empNo != null && !empNo.trim().isEmpty()) {
            return empNo.trim();
        }
        return "DEV_EMP";
    }
}
