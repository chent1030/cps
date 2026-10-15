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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E5：E2 出入库 + E3 预警验收（开发计划 L347；AC-12/13/33）。
 * 核心口径：阈值 5 时库存 4/5 触发、6 不触发（含等于）；超可用出库拒绝无负库存；
 * 持续不足合并同一 OPEN 事件；回升自动解除；恢复后再不足形成新事件。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CpsInventoryTxnServiceTest {

    private static final long ITEM_ID = 101L;

    @Mock private CpsInventoryItemMapper itemMapper;
    @Mock private CpsInventoryTxnMapper txnMapper;
    @Mock private CpsInventoryAlertEventMapper alertMapper;

    private CpsInventoryTxnService service;

    @BeforeEach
    void setUp() {
        service = new CpsInventoryTxnService(itemMapper, txnMapper, alertMapper);
    }

    private static CpsInventoryItem item(int stock, int threshold) {
        CpsInventoryItem item = new CpsInventoryItem();
        item.setId(ITEM_ID);
        item.setItemCode("ITM-001");
        item.setItemName("灭火器");
        item.setUnit("个");
        item.setStockQty(stock);
        item.setAlertThreshold(threshold);
        return item;
    }

    private static CpsInventoryTxnRequest req(String type, int qty) {
        CpsInventoryTxnRequest r = new CpsInventoryTxnRequest();
        r.setItemId(ITEM_ID);
        r.setTxnType(type);
        r.setQty(qty);
        r.setOperatorEmpNo("E10001");
        r.setOperatorName("库管员甲");
        r.setRemark("验收测试");
        return r;
    }

    private void stubItem(int stock, int threshold) {
        CpsInventoryItem item = item(stock, threshold);
        when(itemMapper.selectForUpdate(ITEM_ID)).thenReturn(Optional.of(item));
        when(itemMapper.findById(ITEM_ID)).thenReturn(Optional.of(item));
        when(alertMapper.findOpenByItemId(ITEM_ID)).thenReturn(Optional.empty());
    }

    // ---------- E2：出入库流水（AC-33） ----------

    @Test
    void registerInUpdatesStockAndWritesSnapshotTxn() {
        stubItem(10, 5); // 阈值 5

        CpsInventoryTxnResponse resp = service.register(req("IN", 3));

        assertNotNull(resp.getTxn());
        assertEquals(10, resp.getTxn().getBeforeQty());
        assertEquals(13, resp.getTxn().getAfterQty());
        assertEquals("IN", resp.getTxn().getTxnType());
        assertEquals(3, resp.getTxn().getQty());
        assertEquals("个", resp.getTxn().getUnit());
        assertEquals("E10001", resp.getTxn().getOperatorEmpNo());
        verify(itemMapper).updateStockQty(ITEM_ID, 13, "E10001");
        // 库存 13 > 5：不触发预警
        verify(alertMapper, never()).insert(any(CpsInventoryAlertEvent.class));
    }

    @Test
    void registerValidatesQtySigns() {
        stubItem(10, 5);
        // IN 必须 >0；OUT 必须 <0；ADJUST 必须 !=0
        assertThrows(IllegalArgumentException.class, () -> service.register(req("IN", -1)));
        assertThrows(IllegalArgumentException.class, () -> service.register(req("OUT", 1)));
        assertThrows(IllegalArgumentException.class, () -> service.register(req("ADJUST", 0)));
        assertThrows(IllegalArgumentException.class, () -> service.register(req("TRANSFER", 5)));
        assertThrows(IllegalArgumentException.class, () -> {
            CpsInventoryTxnRequest r = req("IN", 1);
            r.setOperatorEmpNo(" ");
            service.register(r);
        });
        verify(itemMapper, never()).updateStockQty(anyLong(), anyInt(), anyString());
        verify(txnMapper, never()).insert(any(CpsInventoryTxn.class));
    }

    @Test
    void registerMissingItemThrows() {
        when(itemMapper.selectForUpdate(999L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> service.register(req("IN", 1)));
    }

    /** §25.4：禁止超可用库存出库 / 禁止负库存——整事务无副作用。 */
    @Test
    void registerRejectsInsufficientStock() {
        stubItem(3, 5);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.register(req("OUT", -5)));

        assertTrue(ex.getMessage().contains("Insufficient stock"));
        assertTrue(ex.getMessage().contains("negative stock forbidden"));
        verify(itemMapper, never()).updateStockQty(anyLong(), anyInt(), anyString());
        verify(txnMapper, never()).insert(any(CpsInventoryTxn.class));
        verify(alertMapper, never()).insert(any(CpsInventoryAlertEvent.class));
    }

    // ---------- E3：预警评估（AC-13 含等于） ----------

    @Test
    void alertTriggersAtOrBelowThreshold() {
        // 阈值 5：库存 4 → 触发（OPENED）
        stubItem(6, 5);
        CpsInventoryTxnResponse resp = service.register(req("OUT", -2)); // 6-2=4
        assertEquals(CpsInventoryTxnService.ALERT_ACTION_OPENED, resp.getAlertAction());
        ArgumentCaptor<CpsInventoryAlertEvent> captor = ArgumentCaptor.forClass(CpsInventoryAlertEvent.class);
        verify(alertMapper).insert(captor.capture());
        assertEquals(4, captor.getValue().getLastEvalQty());
        assertEquals(5, captor.getValue().getThresholdSnapshot());
        assertEquals(CpsInventoryAlertEvent.STATUS_OPEN, captor.getValue().getStatus());
    }

    @Test
    void alertTriggersAtExactlyThreshold() {
        // 阈值 5：库存 5（等于）→ 触发（AC-13）
        stubItem(6, 5);
        CpsInventoryTxnResponse resp = service.register(req("OUT", -1)); // 6-1=5
        assertEquals(CpsInventoryTxnService.ALERT_ACTION_OPENED, resp.getAlertAction());
    }

    @Test
    void alertNotTriggeredAboveThreshold() {
        // 阈值 5：库存 6 → 不触发（AC-13）
        stubItem(8, 5);
        CpsInventoryTxnResponse resp = service.register(req("OUT", -2)); // 8-2=6
        assertEquals(CpsInventoryTxnService.ALERT_ACTION_NONE, resp.getAlertAction());
        verify(alertMapper, never()).insert(any(CpsInventoryAlertEvent.class));
    }

    /** §25.4：同一物品持续不足合并为同一 OPEN 事件（MERGED，不新开）。 */
    @Test
    void continuedShortageMergesIntoSameOpenEvent() {
        stubItem(5, 5);
        CpsInventoryAlertEvent open = new CpsInventoryAlertEvent();
        open.setId(9001L);
        open.setItemId(ITEM_ID);
        open.setStatus(CpsInventoryAlertEvent.STATUS_OPEN);
        open.setLastEvalQty(5);
        when(alertMapper.findOpenByItemId(ITEM_ID)).thenReturn(Optional.of(open));

        CpsInventoryTxnResponse resp = service.register(req("OUT", -1)); // 5-1=4 仍不足

        assertEquals(CpsInventoryTxnService.ALERT_ACTION_MERGED, resp.getAlertAction());
        verify(alertMapper, never()).insert(any(CpsInventoryAlertEvent.class));
        ArgumentCaptor<CpsInventoryAlertEvent> captor = ArgumentCaptor.forClass(CpsInventoryAlertEvent.class);
        verify(alertMapper).update(captor.capture());
        assertEquals(4, captor.getValue().getLastEvalQty());
        assertEquals(CpsInventoryAlertEvent.STATUS_OPEN, captor.getValue().getStatus());
    }

    /** §25.4：库存大于阈值自动解除；恢复后再次不足形成新事件。 */
    @Test
    void restockAutoResolvesThenNewShortageOpensNewEvent() {
        stubItem(4, 5);
        CpsInventoryAlertEvent open = new CpsInventoryAlertEvent();
        open.setId(9001L);
        open.setItemId(ITEM_ID);
        open.setStatus(CpsInventoryAlertEvent.STATUS_OPEN);
        when(alertMapper.findOpenByItemId(ITEM_ID)).thenReturn(Optional.of(open));

        // 1) 补货 4→8：> 阈值 → 自动解除
        CpsInventoryTxnResponse resolved = service.register(req("IN", 4));
        assertEquals(CpsInventoryTxnService.ALERT_ACTION_AUTO_RESOLVED, resolved.getAlertAction());
        assertEquals(CpsInventoryAlertEvent.STATUS_RESOLVED_AUTO, open.getStatus());
        assertNotNull(open.getClosedAt());
        verify(alertMapper, never()).insert(any(CpsInventoryAlertEvent.class));

        // 2) 再次出库 8→3：又不足 → 新事件（不再合并旧的）
        when(alertMapper.findOpenByItemId(ITEM_ID)).thenReturn(Optional.empty());
        CpsInventoryTxnResponse reopened = service.register(req("OUT", -5));
        assertEquals(CpsInventoryTxnService.ALERT_ACTION_OPENED, reopened.getAlertAction());
        verify(alertMapper, times(1)).insert(any(CpsInventoryAlertEvent.class));
    }

    // ---------- E3：人工处理 ----------

    @Test
    void handleIgnoresAndClosesOpenAlerts() {
        CpsInventoryAlertEvent open = new CpsInventoryAlertEvent();
        open.setId(7001L);
        open.setItemId(ITEM_ID);
        open.setStatus(CpsInventoryAlertEvent.STATUS_OPEN);
        when(alertMapper.findById(7001L)).thenReturn(Optional.of(open));

        // CLOSE 必填 reason
        CpsInventoryAlertHandleRequest closeNoReason = new CpsInventoryAlertHandleRequest();
        closeNoReason.setAction("CLOSE");
        closeNoReason.setOperatorEmpNo("E1");
        assertThrows(IllegalArgumentException.class, () -> service.handle(7001L, closeNoReason));

        CpsInventoryAlertHandleRequest close = new CpsInventoryAlertHandleRequest();
        close.setAction("CLOSE");
        close.setReason("已采购补货");
        close.setOperatorEmpNo("E1");
        service.handle(7001L, close);
        assertEquals(CpsInventoryAlertEvent.STATUS_RESOLVED_MANUAL, open.getStatus());
        assertEquals("E1", open.getClosedBy());
        assertEquals("已采购补货", open.getCloseReason());

        // 处理后非 OPEN 再操作 → 409 语义（IllegalStateException）
        CpsInventoryAlertHandleRequest again = new CpsInventoryAlertHandleRequest();
        again.setAction("IGNORE");
        again.setOperatorEmpNo("E1");
        assertThrows(IllegalStateException.class, () -> service.handle(7001L, again));
    }

    @Test
    void handleValidatesActionAndExistence() {
        assertThrows(IllegalArgumentException.class, () -> {
            CpsInventoryAlertHandleRequest r = new CpsInventoryAlertHandleRequest();
            r.setAction("DELETE");
            service.handle(1L, r);
        });
        when(alertMapper.findById(404L)).thenReturn(Optional.empty());
        CpsInventoryAlertHandleRequest ok = new CpsInventoryAlertHandleRequest();
        ok.setAction("IGNORE");
        ok.setOperatorEmpNo("E1");
        assertThrows(IllegalArgumentException.class, () -> service.handle(404L, ok));
    }

    // ---------- 分页 ----------

    @Test
    void pageTxnsAndAlertsNormalizePaging() {
        when(txnMapper.count(null, null)).thenReturn(3L);
        when(txnMapper.page(isNull(), isNull(), eq(20), eq(20))).thenReturn(java.util.List.of());
        CpsPageResponse<CpsInventoryTxn> txns = service.pageTxns(null, null, 2, 20);
        assertEquals(3L, txns.getTotal());

        when(alertMapper.count("OPEN", ITEM_ID)).thenReturn(1L);
        when(alertMapper.page(eq("OPEN"), eq(ITEM_ID), anyInt(), anyInt()))
                .thenReturn(java.util.List.of());
        CpsPageResponse<CpsInventoryAlertEvent> alerts =
                service.pageAlerts("OPEN", ITEM_ID, 0, 9999); // size 上限 200
        assertEquals(1L, alerts.getTotal());
        verify(alertMapper).page("OPEN", ITEM_ID, 200, 0);

        assertThrows(IllegalArgumentException.class, () -> service.pageAlerts("BOGUS", null, 1, 20));
    }
}
