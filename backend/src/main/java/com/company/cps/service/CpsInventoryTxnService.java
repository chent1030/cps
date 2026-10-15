package com.company.cps.service;

import com.company.cps.domain.CpsInventoryAlertEvent;
import com.company.cps.domain.CpsInventoryItem;
import com.company.cps.domain.CpsInventoryTxn;
import com.company.cps.dto.CpsInventoryAlertHandleRequest;
import com.company.cps.dto.CpsInventoryTxnRequest;
import com.company.cps.dto.CpsInventoryTxnResponse;
import com.company.cps.dto.CpsPageResponse;
import com.company.cps.mapper.CpsInventoryAlertEventMapper;
import com.company.cps.mapper.CpsInventoryItemMapper;
import com.company.cps.mapper.CpsInventoryTxnMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * E2 出入库流水 + E3 库存预警（PRD §25.2–§25.4；AC-12/13/33）。
 *
 * <p>并发口径（AC-33）：register 单事务内 SELECT ... FOR UPDATE 悲观行锁——同件多次
 * 出入库串行化；after&lt;0 时服务层直接 400（禁超可用库存出库/负库存，§25.4 已确认），
 * DB CHECK chk_cps_inventory_stock(stock_qty&gt;=0) 为绕过服务层直写的最后兜底（违反即整事务回滚）。
 *
 * <p>预警口径（AC-13/§25.4）：每次出入库同事务评估 stock_qty &lt;= alert_threshold（含等于）；
 * 持续不足合并为同一 OPEN 事件；回升(&gt;阈值)自动解除；恢复后再不足形成新事件；
 * 人工处理 IGNORE / RESOLVED_MANUAL(带原因)。
 */
@Service
public class CpsInventoryTxnService {

    public static final String TXN_IN = "IN";
    public static final String TXN_OUT = "OUT";
    public static final String TXN_ADJUST = "ADJUST";

    public static final String ALERT_ACTION_OPENED = "OPENED";
    public static final String ALERT_ACTION_MERGED = "MERGED";
    public static final String ALERT_ACTION_AUTO_RESOLVED = "AUTO_RESOLVED";
    public static final String ALERT_ACTION_NONE = "NONE";

    private static final int MAX_PAGE_SIZE = 200;

    private final CpsInventoryItemMapper itemMapper;
    private final CpsInventoryTxnMapper txnMapper;
    private final CpsInventoryAlertEventMapper alertMapper;

    public CpsInventoryTxnService(
            CpsInventoryItemMapper itemMapper,
            CpsInventoryTxnMapper txnMapper,
            CpsInventoryAlertEventMapper alertMapper) {
        this.itemMapper = itemMapper;
        this.txnMapper = txnMapper;
        this.alertMapper = alertMapper;
    }

    /** E2：登记出入库（AC-33）。单事务：行锁→校验→更新库存→流水快照→E3 评估。 */
    @Transactional
    public CpsInventoryTxnResponse register(CpsInventoryTxnRequest request) {
        if (request.getItemId() == null) {
            throw new IllegalArgumentException("itemId is required");
        }
        requireText(request.getTxnType(), "txnType");
        requireText(request.getOperatorEmpNo(), "operatorEmpNo");
        String type = request.getTxnType().trim().toUpperCase();
        if (!TXN_IN.equals(type) && !TXN_OUT.equals(type) && !TXN_ADJUST.equals(type)) {
            throw new IllegalArgumentException("txnType must be IN/OUT/ADJUST: " + request.getTxnType());
        }
        if (request.getQty() == null) {
            throw new IllegalArgumentException("qty is required");
        }
        int qty = request.getQty();
        // 数量正负约束：IN>0 / OUT<0 / ADJUST<>0（与 DB CHECK chk_cps_inv_txn_qty_sign 一致）
        if (TXN_IN.equals(type) && qty <= 0) {
            throw new IllegalArgumentException("IN qty must be > 0: " + qty);
        }
        if (TXN_OUT.equals(type) && qty >= 0) {
            throw new IllegalArgumentException("OUT qty must be < 0: " + qty);
        }
        if (TXN_ADJUST.equals(type) && qty == 0) {
            throw new IllegalArgumentException("ADJUST qty must be != 0");
        }

        CpsInventoryItem item = itemMapper.selectForUpdate(request.getItemId())
                .orElseThrow(() -> new IllegalArgumentException("Inventory item not found: " + request.getItemId()));

        int before = item.getStockQty() == null ? 0 : item.getStockQty();
        int after = before + qty;
        if (after < 0) {
            // §25.4 已确认：禁止超可用库存出库、禁止负库存 → 400
            throw new IllegalArgumentException(
                    "Insufficient stock: before=" + before + ", qty=" + qty + " (negative stock forbidden)");
        }

        itemMapper.updateStockQty(item.getId(), after, request.getOperatorEmpNo());

        CpsInventoryTxn txn = new CpsInventoryTxn();
        txn.setItemId(item.getId());
        txn.setTxnType(type);
        txn.setQty(qty);
        txn.setBeforeQty(before);
        txn.setAfterQty(after);
        txn.setUnit(item.getUnit());
        txn.setOperatorEmpNo(request.getOperatorEmpNo());
        txn.setOperatorName(request.getOperatorName());
        txn.setRemark(request.getRemark());
        txnMapper.insert(txn);

        // E3 同事务评估（含等于阈值；合并事件）
        item.setStockQty(after);
        String alertAction = evaluateAlert(item);

        CpsInventoryItem latest = itemMapper.findById(item.getId()).orElse(item);
        return new CpsInventoryTxnResponse(txn, latest, alertAction);
    }

    /**
     * E3：预警评估（AC-13）。须在持有 item 行锁的事务内调用。
     * low = stock &lt;= threshold（含等于：阈值 5 时库存 4/5 触发、6 不触发）。
     */
    String evaluateAlert(CpsInventoryItem item) {
        boolean low = item.getStockQty() != null && item.getAlertThreshold() != null
                && item.getStockQty() <= item.getAlertThreshold();
        Optional<CpsInventoryAlertEvent> open = alertMapper.findOpenByItemId(item.getId());
        LocalDateTime now = LocalDateTime.now();

        if (low) {
            if (open.isPresent()) {
                // 合并事件：持续不足期间不重复开新，仅刷新最近评估
                CpsInventoryAlertEvent event = open.get();
                event.setLastEvalAt(now);
                event.setLastEvalQty(item.getStockQty());
                alertMapper.update(event);
                return ALERT_ACTION_MERGED;
            }
            CpsInventoryAlertEvent event = new CpsInventoryAlertEvent();
            event.setItemId(item.getId());
            event.setStatus(CpsInventoryAlertEvent.STATUS_OPEN);
            event.setFirstTriggeredAt(now);
            event.setLastEvalAt(now);
            event.setLastEvalQty(item.getStockQty());
            event.setThresholdSnapshot(item.getAlertThreshold());
            alertMapper.insert(event);
            return ALERT_ACTION_OPENED;
        }

        if (open.isPresent()) {
            // 库存回升（>阈值）自动解除（§25.4 已确认）
            CpsInventoryAlertEvent event = open.get();
            event.setStatus(CpsInventoryAlertEvent.STATUS_RESOLVED_AUTO);
            event.setLastEvalAt(now);
            event.setLastEvalQty(item.getStockQty());
            event.setClosedAt(now);
            alertMapper.update(event);
            return ALERT_ACTION_AUTO_RESOLVED;
        }
        return ALERT_ACTION_NONE;
    }

    /** E3：预警人工处理（OPEN→IGNORE / RESOLVED_MANUAL；关闭必填原因）。 */
    @Transactional
    public CpsInventoryAlertEvent handle(Long eventId, CpsInventoryAlertHandleRequest request) {
        requireText(request.getAction(), "action");
        String action = request.getAction().trim().toUpperCase();
        boolean ignore = "IGNORE".equals(action);
        boolean close = "CLOSE".equals(action);
        if (!ignore && !close) {
            throw new IllegalArgumentException("action must be IGNORE or CLOSE: " + request.getAction());
        }
        if (close && (request.getReason() == null || request.getReason().trim().isEmpty())) {
            throw new IllegalArgumentException("reason is required when closing an alert");
        }
        CpsInventoryAlertEvent event = alertMapper.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Alert event not found: " + eventId));
        if (!CpsInventoryAlertEvent.STATUS_OPEN.equals(event.getStatus())) {
            throw new IllegalStateException("Alert event not OPEN: id=" + eventId + ", status=" + event.getStatus());
        }
        event.setStatus(ignore
                ? CpsInventoryAlertEvent.STATUS_IGNORED
                : CpsInventoryAlertEvent.STATUS_RESOLVED_MANUAL);
        event.setClosedAt(LocalDateTime.now());
        event.setClosedBy(request.getOperatorEmpNo());
        event.setCloseReason(request.getReason());
        alertMapper.update(event);
        return alertMapper.findById(eventId).orElse(event);
    }

    /** E2：流水分页查询（AC-12 可查询流水）。 */
    public CpsPageResponse<CpsInventoryTxn> pageTxns(Long itemId, String txnType, Integer page, Integer size) {
        int pageSize = normalizeSize(size);
        long total = txnMapper.count(itemId, txnType);
        List<CpsInventoryTxn> rows = total == 0
                ? List.of()
                : txnMapper.page(itemId, txnType, pageSize, normalizeOffset(page, pageSize));
        return new CpsPageResponse<>(total, rows);
    }

    /** E3：预警分页列表。 */
    public CpsPageResponse<CpsInventoryAlertEvent> pageAlerts(String status, Long itemId, Integer page, Integer size) {
        if (status != null && !status.trim().isEmpty()) {
            String s = status.trim().toUpperCase();
            if (!CpsInventoryAlertEvent.STATUS_OPEN.equals(s)
                    && !CpsInventoryAlertEvent.STATUS_RESOLVED_AUTO.equals(s)
                    && !CpsInventoryAlertEvent.STATUS_RESOLVED_MANUAL.equals(s)
                    && !CpsInventoryAlertEvent.STATUS_IGNORED.equals(s)) {
                throw new IllegalArgumentException("Invalid alert status: " + status);
            }
            status = s;
        } else {
            status = null;
        }
        int pageSize = normalizeSize(size);
        long total = alertMapper.count(status, itemId);
        List<CpsInventoryAlertEvent> rows = total == 0
                ? List.of()
                : alertMapper.page(status, itemId, pageSize, normalizeOffset(page, pageSize));
        return new CpsPageResponse<>(total, rows);
    }

    private static int normalizeSize(Integer size) {
        if (size == null || size <= 0) return 20;
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static int normalizeOffset(Integer page, int pageSize) {
        int p = page == null || page <= 0 ? 1 : page;
        return (p - 1) * pageSize;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Inventory txn " + field + " is required");
        }
    }
}
