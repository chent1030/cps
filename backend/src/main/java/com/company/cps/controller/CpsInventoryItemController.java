package com.company.cps.controller;

import com.company.cps.domain.CpsInventoryItem;
import com.company.cps.dto.CpsEnabledRequest;
import com.company.cps.dto.CpsInventoryItemRequest;
import com.company.cps.service.CpsInventoryItemService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** E1 设备/物品台账（PRD §25.1，手工录入 CRUD；预警=E3 消费 lowStock 过滤）。 */
@RestController
@RequestMapping("/api/cps/admin/inventory-items")
public class CpsInventoryItemController {

    private final CpsInventoryItemService itemService;

    public CpsInventoryItemController(CpsInventoryItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    public List<CpsInventoryItem> list(
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String storageRoom,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean lowStock,
            @RequestParam(required = false) Boolean enabled
    ) {
        return itemService.list(factory, storageRoom, keyword, lowStock, enabled);
    }

    @GetMapping("/{id}")
    public CpsInventoryItem detail(@PathVariable Long id) {
        return itemService.getDetail(id);
    }

    @PostMapping
    public CpsInventoryItem save(
            @RequestParam(required = false) Long id,
            @RequestBody CpsInventoryItemRequest request,
            @RequestParam(defaultValue = "admin") String operatorEmpNo
    ) {
        return itemService.save(id, request, operatorEmpNo);
    }

    @PatchMapping("/{id}/enabled")
    public void setEnabled(@PathVariable Long id, @RequestBody CpsEnabledRequest request) {
        itemService.setEnabled(id, request.getEnabled(), request.getEmpNo());
    }
}
