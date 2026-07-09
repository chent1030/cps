package com.company.cps.controller;

import com.company.cps.domain.CpsAreaPersonConfig;
import com.company.cps.domain.CpsProblemCategory;
import com.company.cps.dto.CpsAreaPersonConfigRequest;
import com.company.cps.dto.CpsProblemCategoryRequest;
import com.company.cps.service.CpsMasterDataService;
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
@RequestMapping("/api/cps/admin/master")
public class CpsMasterAdminController {

    private final CpsMasterDataService masterDataService;

    public CpsMasterAdminController(CpsMasterDataService masterDataService) {
        this.masterDataService = masterDataService;
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

    /**
     * 新增或更新问题分类；一级分类 parentId 为空或0，二级分类必须传一级分类ID。
     */
    @PostMapping("/categories")
    public CpsProblemCategory saveCategory(
            @RequestBody CpsProblemCategoryRequest request,
            @RequestHeader(value = "X-Emp-No", required = false) String empNo
    ) {
        return masterDataService.saveCategory(request, resolveCurrentEmpNo(empNo));
    }

    /**
     * 启用或停用问题分类；停用后移动端分类选项不再返回该分类。
     */
    @PatchMapping("/categories/{id}/enabled")
    public void setCategoryEnabled(
            @PathVariable Long id,
            @RequestParam Boolean enabled,
            @RequestHeader(value = "X-Emp-No", required = false) String empNo
    ) {
        masterDataService.setCategoryEnabled(id, enabled, resolveCurrentEmpNo(empNo));
    }

    /**
     * 查询区域人员配置维护列表，可按工厂、区域和启停状态过滤。
     */
    @GetMapping("/area-person-configs")
    public List<CpsAreaPersonConfig> areaPersonConfigs(
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) Boolean enabled
    ) {
        return masterDataService.listAreaPersonConfigs(factory, area, enabled);
    }

    /**
     * 新增或更新区域人员配置；同一工厂、区域、拉线、工序范围只保留一条配置。
     */
    @PostMapping("/area-person-configs")
    public CpsAreaPersonConfig saveAreaPersonConfig(
            @RequestBody CpsAreaPersonConfigRequest request,
            @RequestHeader(value = "X-Emp-No", required = false) String empNo
    ) {
        return masterDataService.saveAreaPersonConfig(request, resolveCurrentEmpNo(empNo));
    }

    /**
     * 启用或停用区域人员配置；停用后不参与反馈人和审核人自动匹配。
     */
    @PatchMapping("/area-person-configs/{id}/enabled")
    public void setAreaPersonConfigEnabled(
            @PathVariable Long id,
            @RequestParam Boolean enabled,
            @RequestHeader(value = "X-Emp-No", required = false) String empNo
    ) {
        masterDataService.setAreaPersonConfigEnabled(id, enabled, resolveCurrentEmpNo(empNo));
    }

    private String resolveCurrentEmpNo(String empNo) {
        if (empNo != null && !empNo.trim().isEmpty()) {
            return empNo.trim();
        }
        return "DEV_EMP";
    }
}
