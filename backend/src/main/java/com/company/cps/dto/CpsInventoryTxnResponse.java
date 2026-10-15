package com.company.cps.dto;

import com.company.cps.domain.CpsInventoryItem;
import com.company.cps.domain.CpsInventoryTxn;

/** E2 出入库登记结果：流水 + 最新台账 + 本次触发的预警动作（E3 同事务评估）。 */
public class CpsInventoryTxnResponse {
    private CpsInventoryTxn txn;
    private CpsInventoryItem item;
    /** OPENED=新开预警 / MERGED=合并既有事件 / AUTO_RESOLVED=回升自动解除 / NONE=无动作 */
    private String alertAction;

    public CpsInventoryTxnResponse(CpsInventoryTxn txn, CpsInventoryItem item, String alertAction) {
        this.txn = txn;
        this.item = item;
        this.alertAction = alertAction;
    }

    public CpsInventoryTxn getTxn() { return txn; }
    public CpsInventoryItem getItem() { return item; }
    public String getAlertAction() { return alertAction; }
}
