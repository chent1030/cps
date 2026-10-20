package com.company.cps.controller;

import com.company.cps.domain.CpsDict;
import com.company.cps.dto.CpsDictRequest;
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

/** 通用字典维护（/api/cps/admin/dicts 条目 + /api/cps/admin/dict-types 类型）：选项类配置唯一事实源。 */
@RestController
@RequestMapping("/api/cps/admin/dicts")
public class CpsDictController {

    private final CpsDictService dictService;

    public CpsDictController(CpsDictService dictService) {
        this.dictService = dictService;
    }

    @GetMapping
    public List<CpsDict> list(
            @RequestParam(required = false) String dictType,
            @RequestParam(required = false) Boolean enabled
    ) {
        return dictService.list(dictType, enabled);
    }

    @GetMapping("/{id}")
    public CpsDict detail(@PathVariable Long id) {
        return dictService.getDetail(id);
    }

    /** 新建或更新（id 为空=新建；dictType+itemValue 唯一；builtin 项受保护）。 */
    @PostMapping
    public CpsDict save(
            @RequestParam(required = false) Long id,
            @RequestBody CpsDictRequest request,
            @RequestParam(defaultValue = "admin") String operatorEmpNo
    ) {
        return dictService.save(id, request, operatorEmpNo);
    }

    @PatchMapping("/{id}/enabled")
    public void setEnabled(@PathVariable Long id, @RequestBody CpsEnabledRequest request) {
        dictService.setEnabled(id, request.getEnabled(), request.getEmpNo());
    }

    /** 删除非内置项（builtin=1 拒绝，建议用停用替代）。 */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        dictService.delete(id);
    }
}
