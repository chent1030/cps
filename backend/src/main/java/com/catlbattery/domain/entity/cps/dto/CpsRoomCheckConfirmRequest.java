package com.catlbattery.domain.entity.cps.dto;

import java.util.List;

/**
 * PRD 24.2.2/24.2.5：点检人提交前确认/修正判定结果。
 * 仅 PASS/FAIL 可确认或修正（TYPE_MISMATCH/UNJUDGEABLE 在 submit 已阻断，不允许手工改合格绕过证据要求）。
 */
public class CpsRoomCheckConfirmRequest {

    private String empNo;
    private List<ItemDecision> items;

    /** 单项最终结果决定；未出现的项按 AI 判定原样确认。 */
    public static class ItemDecision {
        private Long itemId;
        private String finalResult;
        private String reason;

        public Long getItemId() { return itemId; }
        public void setItemId(Long itemId) { this.itemId = itemId; }
        public String getFinalResult() { return finalResult; }
        public void setFinalResult(String finalResult) { this.finalResult = finalResult; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public String getEmpNo() { return empNo; }
    public void setEmpNo(String empNo) { this.empNo = empNo; }
    public List<ItemDecision> getItems() { return items; }
    public void setItems(List<ItemDecision> items) { this.items = items; }
}
