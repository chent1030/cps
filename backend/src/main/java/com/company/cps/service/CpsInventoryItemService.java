package com.company.cps.service;

import com.company.cps.domain.CpsInventoryItem;
import com.company.cps.dto.CpsInventoryItemRequest;
import com.company.cps.mapper.CpsInventoryItemMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * E1 设备/物品台账（PRD §25.1 字段全集）：手工录入 CRUD。
 * 库存/预警数量语义：stock_qty <= alert_threshold（含等于）即预警（§25.2，E3 消费）；
 * 出入库流水（E2）落 cps_inventory_movement，本服务不直接改库存。
 */
@Service
public class CpsInventoryItemService {

    private final CpsInventoryItemMapper itemMapper;

    public CpsInventoryItemService(CpsInventoryItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    public List<CpsInventoryItem> list(
            String factory, String storageRoom, String keyword, Boolean lowStock, Boolean enabled) {
        return itemMapper.findAll(factory, storageRoom, keyword, lowStock, enabled);
    }

    public CpsInventoryItem getDetail(Long id) {
        return itemMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Inventory item not found: " + id));
    }

    /** 是否处于预警线（库存<=预警数量，含等于，§25.2/AC-13）。 */
    public boolean isLowStock(CpsInventoryItem item) {
        return item.getStockQty() != null && item.getAlertThreshold() != null
                && item.getStockQty() <= item.getAlertThreshold();
    }

    @Transactional
    public CpsInventoryItem save(Long id, CpsInventoryItemRequest request, String operatorEmpNo) {
        requireText(request.getItemCode(), "itemCode");
        requireText(request.getItemName(), "itemName");
        requireText(request.getUnit(), "unit");
        requireText(request.getFactory(), "factory");
        requireText(request.getStorageRoom(), "storageRoom");
        requireText(request.getRoomKeeperEmpNo(), "roomKeeperEmpNo");
        requireText(request.getRoomKeeperEmpName(), "roomKeeperEmpName");
        int stockQty = request.getStockQty() == null ? 0 : request.getStockQty();
        int alertThreshold = request.getAlertThreshold() == null ? 0 : request.getAlertThreshold();
        if (stockQty < 0) {
            throw new IllegalArgumentException("stockQty must be >= 0: " + stockQty);
        }
        if (alertThreshold < 0) {
            throw new IllegalArgumentException("alertThreshold must be >= 0: " + alertThreshold);
        }

        CpsInventoryItem existing = id == null
                ? null
                : itemMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Inventory item not found: " + id));
        Optional<CpsInventoryItem> sameCode = itemMapper.findByCode(request.getItemCode());
        long selfId = existing == null ? -1L : existing.getId();
        if (sameCode.isPresent() && !sameCode.get().getId().equals(selfId)) {
            throw new IllegalStateException("Inventory item code already exists: " + request.getItemCode());
        }

        CpsInventoryItem item = existing == null ? new CpsInventoryItem() : existing;
        item.setItemCode(request.getItemCode());
        item.setItemName(request.getItemName());
        item.setUnit(request.getUnit());
        item.setStockQty(stockQty);
        item.setAlertThreshold(alertThreshold);
        item.setBaseCode(request.getBaseCode());
        item.setFactory(request.getFactory());
        item.setStorageRoom(request.getStorageRoom());
        item.setRoomKeeperEmpNo(request.getRoomKeeperEmpNo());
        item.setRoomKeeperEmpName(request.getRoomKeeperEmpName());
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
            throw new IllegalStateException("Inventory item not found or not updated: " + id);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Inventory item " + field + " is required");
        }
    }
}
