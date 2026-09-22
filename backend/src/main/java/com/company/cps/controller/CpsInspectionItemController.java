package com.company.cps.controller;

import com.company.cps.domain.CpsInspectionItem;
import com.company.cps.domain.CpsInspectionItemPermission;
import com.company.cps.dto.CpsEnabledRequest;
import com.company.cps.dto.CpsInspectionItemPermissionRequest;
import com.company.cps.dto.CpsInspectionItemRequest;
import com.company.cps.service.CpsInspectionItemService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** W4 巡检事项 + §30.1 查看权限（无配置行=开放）。 */
@RestController
@RequestMapping("/api/cps/inspection-items")
public class CpsInspectionItemController {

    private final CpsInspectionItemService itemService;

    public CpsInspectionItemController(CpsInspectionItemService itemService) {
        this.itemService = itemService;
    }

    /** 全量列表（管理端）。 */
    @GetMapping
    public List<CpsInspectionItem> list(
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) Boolean enabled
    ) {
        return itemService.list(factory, enabled);
    }

    /** §30.1：按当前人过滤可见事项（empNo 必传）。 */
    @GetMapping(params = "empNo")
    public List<CpsInspectionItem> listVisible(
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam String empNo
    ) {
        return itemService.listVisible(factory, enabled, empNo);
    }

    @GetMapping("/{id}")
    public CpsInspectionItem detail(@PathVariable Long id) {
        return itemService.getDetail(id);
    }

    /** §30.1 可见性判定（无配置行=true 开放）。 */
    @GetMapping("/{id}/can-view")
    public boolean canView(@PathVariable Long id, @RequestParam String empNo) {
        return itemService.canView(id, empNo);
    }

    @PostMapping
    public CpsInspectionItem save(
            @RequestParam(required = false) Long id,
            @RequestBody CpsInspectionItemRequest request,
            @RequestParam(defaultValue = "admin") String operatorEmpNo
    ) {
        return itemService.save(id, request, operatorEmpNo);
    }

    @PatchMapping("/{id}/enabled")
    public void setEnabled(@PathVariable Long id, @RequestBody CpsEnabledRequest request) {
        itemService.setEnabled(id, request.getEnabled(), request.getEmpNo());
    }

    @GetMapping("/{id}/permissions")
    public List<CpsInspectionItemPermission> listPermissions(@PathVariable Long id) {
        return itemService.listPermissions(id);
    }

    /** 整体替换权限名单；空名单=删光配置行，事项回到开放。 */
    @PutMapping("/{id}/permissions")
    public List<CpsInspectionItemPermission> replacePermissions(
            @PathVariable Long id,
            @RequestBody CpsInspectionItemPermissionRequest request,
            @RequestParam(defaultValue = "admin") String operatorEmpNo
    ) {
        return itemService.replacePermissions(id, request.getPermissions(), operatorEmpNo);
    }
}
