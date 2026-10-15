package com.company.cps.controller;

import com.company.cps.domain.CpsInventoryAlertEvent;
import com.company.cps.domain.CpsInventoryTxn;
import com.company.cps.dto.CpsInventoryAlertHandleRequest;
import com.company.cps.dto.CpsInventoryTxnRequest;
import com.company.cps.dto.CpsInventoryTxnResponse;
import com.company.cps.dto.CpsPageResponse;
import com.company.cps.service.CpsInventoryTxnService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * E2 出入库登记/流水分页 + E3 预警列表/处理（admin；AC-12/13/33）。
 * 台账 CRUD 在 CpsInventoryItemController（/api/cps/admin/inventory-items）。
 */
@RestController
@RequestMapping("/api/cps/admin")
public class CpsAdminInventoryTxnController {

    private final CpsInventoryTxnService txnService;

    public CpsAdminInventoryTxnController(CpsInventoryTxnService txnService) {
        this.txnService = txnService;
    }

    /** E2：登记出入库（单事务：行锁→更新库存→流水→预警评估；负库存→400）。 */
    @PostMapping("/inventory-txns")
    public CpsInventoryTxnResponse register(@RequestBody CpsInventoryTxnRequest request) {
        return txnService.register(request);
    }

    /** E2：流水分页查询（AC-12）。 */
    @GetMapping("/inventory-txns")
    public CpsPageResponse<CpsInventoryTxn> txns(
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) String txnType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return txnService.pageTxns(itemId, txnType, page, size);
    }

    /** E3：预警分页列表（状态过滤 OPEN/RESOLVED_AUTO/RESOLVED_MANUAL/IGNORED）。 */
    @GetMapping("/inventory-alerts")
    public CpsPageResponse<CpsInventoryAlertEvent> alerts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return txnService.pageAlerts(status, itemId, page, size);
    }

    /** E3：预警人工处理：IGNORE 忽略 / CLOSE 关闭（必填原因）。 */
    @PostMapping("/inventory-alerts/{id}/handle")
    public CpsInventoryAlertEvent handle(@PathVariable Long id, @RequestBody CpsInventoryAlertHandleRequest request) {
        return txnService.handle(id, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(error.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> conflict(IllegalStateException error) {
        return ResponseEntity.status(409).body(error.getMessage());
    }
}
