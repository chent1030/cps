package com.company.cps.service;

import com.company.cps.domain.CpsInspectionItem;
import com.company.cps.domain.CpsInspectionItemPermission;
import com.company.cps.dto.CpsInspectionItemRequest;
import com.company.cps.mapper.CpsInspectionItemMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * W4 巡检事项 + §30.1 查看权限：
 * 事项未配置任何权限行 = 对所有人开放；配置了权限行 = 仅授权工号可见。
 * cps_issue.inspection_item_id（V20260923 预留列）指向本表 id。
 */
@Service
public class CpsInspectionItemService {

    private final CpsInspectionItemMapper itemMapper;

    public CpsInspectionItemService(CpsInspectionItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    public List<CpsInspectionItem> list(String factory, Boolean enabled) {
        return itemMapper.findAll(factory, enabled);
    }

    /** §30.1：过滤当前人可见的事项（无配置行=开放）。 */
    public List<CpsInspectionItem> listVisible(String factory, Boolean enabled, String empNo) {
        List<CpsInspectionItem> items = new java.util.ArrayList<>(list(factory, enabled));
        List<Long> visibleIds = itemMapper.findVisibleItemIds(empNo);
        items.removeIf(item -> !visibleIds.contains(item.getId()));
        return items;
    }

    public CpsInspectionItem getDetail(Long id) {
        return itemMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Inspection item not found: " + id));
    }

    /** §30.1 可见性判定：无任何权限行=开放；有权限行=仅授权工号。事项不存在=不可见。 */
    public boolean canView(Long itemId, String empNo) {
        Optional<CpsInspectionItem> item = itemMapper.findById(itemId);
        if (!item.isPresent()) {
            return false;
        }
        if (itemMapper.countPermissions(itemId) == 0) {
            return true;
        }
        return itemMapper.findPermissions(itemId).stream()
                .anyMatch(p -> p.getEmpNo() != null && p.getEmpNo().equals(empNo));
    }

    @Transactional
    public CpsInspectionItem save(Long id, CpsInspectionItemRequest request, String operatorEmpNo) {
        requireText(request.getItemCode(), "itemCode");
        requireText(request.getItemName(), "itemName");

        CpsInspectionItem existing = id == null
                ? null
                : itemMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Inspection item not found: " + id));
        Optional<CpsInspectionItem> sameCode = itemMapper.findByCode(request.getItemCode());
        long selfId = existing == null ? -1L : existing.getId();
        if (sameCode.isPresent() && !sameCode.get().getId().equals(selfId)) {
            throw new IllegalStateException("Inspection item code already exists: " + request.getItemCode());
        }

        CpsInspectionItem item = existing == null ? new CpsInspectionItem() : existing;
        item.setItemCode(request.getItemCode());
        item.setItemName(request.getItemName());
        item.setFactory(request.getFactory());
        item.setRemark(request.getRemark());
        item.setEnabled(request.getEnabled() == null || request.getEnabled());

        if (id == null) {
            item.setCreatedBy(operatorEmpNo);
            item.setUpdatedBy(operatorEmpNo);
            itemMapper.insert(item);
        } else {
            item.setUpdatedBy(operatorEmpNo);
            itemMapper.update(item);
        }
        return getDetail(item.getId());
    }

    @Transactional
    public void setEnabled(Long id, Boolean enabled, String operatorEmpNo) {
        int rows = itemMapper.setEnabled(id, enabled, operatorEmpNo);
        if (rows != 1) {
            throw new IllegalStateException("Inspection item not found or not updated: " + id);
        }
    }

    /** 整体替换权限名单：空名单=删除全部配置行，事项回到开放（§30.1）。 */
    @Transactional
    public List<CpsInspectionItemPermission> replacePermissions(
            Long itemId, List<CpsInspectionItemPermission> permissions, String operatorEmpNo) {
        itemMapper.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Inspection item not found: " + itemId));
        itemMapper.deletePermissionsByItemId(itemId);
        if (permissions != null) {
            for (CpsInspectionItemPermission permission : permissions) {
                if (permission.getEmpNo() == null || permission.getEmpNo().trim().isEmpty()) {
                    throw new IllegalArgumentException("Permission empNo is required");
                }
                CpsInspectionItemPermission row = new CpsInspectionItemPermission();
                row.setItemId(itemId);
                row.setEmpNo(permission.getEmpNo().trim());
                row.setEmpName(permission.getEmpName());
                row.setCreatedBy(operatorEmpNo);
                itemMapper.insertPermission(row);
            }
        }
        return itemMapper.findPermissions(itemId);
    }

    public List<CpsInspectionItemPermission> listPermissions(Long itemId) {
        itemMapper.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Inspection item not found: " + itemId));
        return itemMapper.findPermissions(itemId);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Inspection item " + field + " is required");
        }
    }
}
