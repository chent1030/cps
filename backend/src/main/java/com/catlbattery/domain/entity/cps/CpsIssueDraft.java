package com.catlbattery.domain.entity.cps;

import java.time.LocalDateTime;

/**
 * 巡检问题服务端草稿（Phase B2）。
 * 一个用户可持有多份 ACTIVE 草稿；draftData 存完整表单快照（JSON），
 * Android 端恢复时整包回填 store。
 */
public class CpsIssueDraft {

    private Long id;
    /** 草稿归属人工号（移动端当前用户） */
    private String creatorEmpNo;
    /** 草稿归属人姓名（冗余展示用） */
    private String creatorEmpName;
    /** 草稿摘要（列表展示：位置+类型+描述截断） */
    private String title;
    /** 巡检类型编码（快照冗余，便于列表筛选） */
    private String inspectionTypeCode;
    /** 工厂（快照冗余） */
    private String factory;
    /** 区域（快照冗余） */
    private String area;
    /** 完整表单快照 JSON 字符串 */
    private String draftData;
    /** ACTIVE=编辑中 / SUBMITTED=已提交归档 / DISCARDED=已废弃 */
    private String status;
    /** 提交成功后对应的问题 ID（SUBMITTED 时回填） */
    private Long submittedIssueId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCreatorEmpNo() { return creatorEmpNo; }
    public void setCreatorEmpNo(String creatorEmpNo) { this.creatorEmpNo = creatorEmpNo; }

    public String getCreatorEmpName() { return creatorEmpName; }
    public void setCreatorEmpName(String creatorEmpName) { this.creatorEmpName = creatorEmpName; }

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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getSubmittedIssueId() { return submittedIssueId; }
    public void setSubmittedIssueId(Long submittedIssueId) { this.submittedIssueId = submittedIssueId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
