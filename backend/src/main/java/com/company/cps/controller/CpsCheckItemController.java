package com.company.cps.controller;

import com.company.cps.domain.CpsCheckItem;
import com.company.cps.dto.CpsCheckItemRequest;
import com.company.cps.dto.CpsEnabledRequest;
import com.company.cps.service.CpsCheckItemService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** B2 辅房点检项配置维护（PRD §23.2，配置版本随更新自动递增）。 */
@RestController
@RequestMapping("/api/cps/admin/check-items")
public class CpsCheckItemController {

    private final CpsCheckItemService checkItemService;

    public CpsCheckItemController(CpsCheckItemService checkItemService) {
        this.checkItemService = checkItemService;
    }

    @GetMapping
    public List<CpsCheckItem> list(
            @RequestParam(required = false) String photoCategory,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean enabled
    ) {
        return checkItemService.list(photoCategory, status, enabled);
    }

    @GetMapping("/{id}")
    public CpsCheckItem detail(@PathVariable Long id) {
        return checkItemService.getDetail(id);
    }

    @PostMapping
    public CpsCheckItem save(
            @RequestParam(required = false) Long id,
            @RequestBody CpsCheckItemRequest request,
            @RequestParam(defaultValue = "admin") String operatorEmpNo
    ) {
        return checkItemService.save(id, request, operatorEmpNo);
    }

    @PatchMapping("/{id}/enabled")
    public void setEnabled(@PathVariable Long id, @RequestBody CpsEnabledRequest request) {
        checkItemService.setEnabled(id, request.getEnabled(), request.getEmpNo());
    }
}
