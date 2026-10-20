package com.company.cps.controller;

import com.company.cps.domain.CpsDictType;
import com.company.cps.dto.CpsDictTypeRequest;
import com.company.cps.dto.CpsEnabledRequest;
import com.company.cps.service.CpsDictService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 字典类型维护（/api/cps/admin/dict-types，V20261016 双表结构的类型侧）。 */
@RestController
@RequestMapping("/api/cps/admin/dict-types")
public class CpsDictTypeController {

    private final CpsDictService dictService;

    public CpsDictTypeController(CpsDictService dictService) {
        this.dictService = dictService;
    }

    @GetMapping
    public List<CpsDictType> list(@RequestParam(required = false) Boolean enabled) {
        return dictService.listTypes(enabled);
    }

    @GetMapping("/{id}")
    public CpsDictType detail(@PathVariable Long id) {
        return dictService.getTypeDetail(id);
    }

    /** 新建或更新类型（新建时 typeCode 必填且唯一；内置类型不可改码/停用/删除）。 */
    @PostMapping
    public CpsDictType save(
            @RequestParam(required = false) Long id,
            @RequestBody CpsDictTypeRequest request,
            @RequestParam(defaultValue = "admin") String operatorEmpNo
    ) {
        return dictService.saveType(id, request, operatorEmpNo);
    }

    @PatchMapping("/{id}/enabled")
    public void setEnabled(@PathVariable Long id, @RequestBody CpsEnabledRequest request) {
        dictService.setTypeEnabled(id, request.getEnabled(), request.getEmpNo());
    }

    /** 删除非内置类型（仍挂有条目时拒绝）。 */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        dictService.deleteType(id);
    }
}
