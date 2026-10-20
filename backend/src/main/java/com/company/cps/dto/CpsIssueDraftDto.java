package com.company.cps.dto;

import com.company.cps.domain.CpsIssueDraft;

import java.time.LocalDateTime;

/** 草稿响应 DTO（Phase B2）：列表与详情共用。 */
public class CpsIssueDraftDto {

    private Long id;
    private String creatorEmpNo;
    private String creatorEmpName;
    private String title;
    private String inspectionTypeCode;
    private String factory;
    private String area;
    private String draftData;
    private String status;
    private Long submittedIssueId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CpsIssueDraftDto from(CpsIssueDraft d) {
        if (d == null) return null;
        CpsIssueDraftDto dto = new CpsIssueDraftDto();
        dto.id = d.getId();
        dto.creatorEmpNo = d.getCreatorEmpNo();
        dto.creatorEmpName = d.getCreatorEmpName();
        dto.title = d.getTitle();
        dto.inspectionTypeCode = d.getInspectionTypeCode();
        dto.factory = d.getFactory();
        dto.area = d.getArea();
        dto.draftData = d.getDraftData();
        dto.status = d.getStatus();
        dto.submittedIssueId = d.getSubmittedIssueId();
        dto.createdAt = d.getCreatedAt();
        dto.updatedAt = d.getUpdatedAt();
        return dto;
    }

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
