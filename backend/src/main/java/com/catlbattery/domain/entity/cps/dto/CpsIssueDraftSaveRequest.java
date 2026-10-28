package com.catlbattery.domain.entity.cps.dto;

/**
 * 暂存/更新草稿请求（Phase B2）。
 * id 为空 → 新建草稿；非空 → 覆盖更新（仅允许本人更新自己的 ACTIVE 草稿）。
 */
public class CpsIssueDraftSaveRequest {

    private Long id;
    private String empNo;
    private String empName;
    /** 草稿摘要（列表展示）。空则后端按 类型+位置 拼接。 */
    private String title;
    private String inspectionTypeCode;
    private String factory;
    private String area;
    /** 完整表单快照 JSON 字符串（由 Android 端序列化 formDraft store） */
    private String draftData;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmpNo() { return empNo; }
    public void setEmpNo(String empNo) { this.empNo = empNo; }

    public String getEmpName() { return empName; }
    public void setEmpName(String empName) { this.empName = empName; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getInspectionTypeCode() { return inspectionTypeCode; }
    public void setInspectionTypeCode(String inspectionTypeCode) { this.inspectionTypeCode = inspectionTypeCode; }

    public String getFactory() { return factory; }
    public void setFactory(String factory) { this.factory = factory; }

    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }

    public String getDraftData() { return draftData; }
    public void setDraftData(String draftData) { this.draftData = draftData; }
}
