package com.catlbattery.domain.entity.cps.dto;

import java.util.List;

/**
 * 非 CPS 巡检整改提交（Android 整改人）。
 * 三要素与问题整改同口径：原因分析 / 短期措施 / 长期措施 + 1~5 张整改照片。
 */
public class CpsInspectionRectifyRequest {

    private String reason;
    private String shortTermMeasure;
    private String longTermMeasure;
    /** 整改照片附件 id（先调 POST /api/cps/attachments 上传，再带 id 提交） */
    private List<Long> attachmentIds;
    /** 整改人（区域责任人；与 /start 同口径由客户端带登录人） */
    private String empNo;
    private String empName;

    public String getEmpNo() { return empNo; }
    public void setEmpNo(String empNo) { this.empNo = empNo; }
    public String getEmpName() { return empName; }
    public void setEmpName(String empName) { this.empName = empName; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getShortTermMeasure() { return shortTermMeasure; }
    public void setShortTermMeasure(String shortTermMeasure) { this.shortTermMeasure = shortTermMeasure; }
    public String getLongTermMeasure() { return longTermMeasure; }
    public void setLongTermMeasure(String longTermMeasure) { this.longTermMeasure = longTermMeasure; }
    public List<Long> getAttachmentIds() { return attachmentIds; }
    public void setAttachmentIds(List<Long> attachmentIds) { this.attachmentIds = attachmentIds; }
}
