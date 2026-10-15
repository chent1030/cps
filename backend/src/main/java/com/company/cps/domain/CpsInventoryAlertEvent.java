package com.company.cps.domain;

import java.time.LocalDateTime;

/**
 * E3 库存预警事件（cps_inventory_alert_event，PRD §25.4；AC-13）。
 * 同物品持续不足合并为同一 OPEN 事件（DB 生成列 open_item_id + UNIQUE 兜底）；
 * 库存回升(&gt;阈值)自动解除 RESOLVED_AUTO；人工 RESOLVED_MANUAL/IGNORED。
 */
public class CpsInventoryAlertEvent {
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_RESOLVED_AUTO = "RESOLVED_AUTO";
    public static final String STATUS_RESOLVED_MANUAL = "RESOLVED_MANUAL";
    public static final String STATUS_IGNORED = "IGNORED";

    private Long id;
    private Long itemId;
    private String status;
    private LocalDateTime firstTriggeredAt;
    private LocalDateTime lastEvalAt;
    private Integer lastEvalQty;
    private Integer thresholdSnapshot;
    private LocalDateTime closedAt;
    private String closedBy;
    private String closeReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 列表联查冗余（非表列） */
    private String itemCode;
    private String itemName;
    private String unit;
    private Integer currentStockQty;
    private Integer currentAlertThreshold;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getFirstTriggeredAt() { return firstTriggeredAt; }
    public void setFirstTriggeredAt(LocalDateTime firstTriggeredAt) { this.firstTriggeredAt = firstTriggeredAt; }
    public LocalDateTime getLastEvalAt() { return lastEvalAt; }
    public void setLastEvalAt(LocalDateTime lastEvalAt) { this.lastEvalAt = lastEvalAt; }
    public Integer getLastEvalQty() { return lastEvalQty; }
    public void setLastEvalQty(Integer lastEvalQty) { this.lastEvalQty = lastEvalQty; }
    public Integer getThresholdSnapshot() { return thresholdSnapshot; }
    public void setThresholdSnapshot(Integer thresholdSnapshot) { this.thresholdSnapshot = thresholdSnapshot; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public String getClosedBy() { return closedBy; }
    public void setClosedBy(String closedBy) { this.closedBy = closedBy; }
    public String getCloseReason() { return closeReason; }
    public void setCloseReason(String closeReason) { this.closeReason = closeReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Integer getCurrentStockQty() { return currentStockQty; }
    public void setCurrentStockQty(Integer currentStockQty) { this.currentStockQty = currentStockQty; }
    public Integer getCurrentAlertThreshold() { return currentAlertThreshold; }
    public void setCurrentAlertThreshold(Integer currentAlertThreshold) { this.currentAlertThreshold = currentAlertThreshold; }
}
