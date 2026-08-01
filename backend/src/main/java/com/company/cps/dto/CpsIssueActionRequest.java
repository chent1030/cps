package com.company.cps.dto;

import com.company.cps.domain.CpsIssueAction;

import java.util.List;

public class CpsIssueActionRequest {
    private CpsIssueAction action;
    private String reasonAnalysis;
    private String correctiveMeasure;
    private String responsibleEmpNo;
    private String proofEmpNo;
    private String reviewerEmpNo;
    private String targetEmpNo;
    private String rectifyRemark;
    private String reviewOpinion;
    private List<Long> proofAttachmentIds;
    private String comment;
    private Long categoryL1Id;
    private Long categoryL2Id;

    public CpsIssueAction getAction() { return action; }
    public void setAction(CpsIssueAction action) { this.action = action; }
    public String getReasonAnalysis() { return reasonAnalysis; }
    public void setReasonAnalysis(String reasonAnalysis) { this.reasonAnalysis = reasonAnalysis; }
    public String getCorrectiveMeasure() { return correctiveMeasure; }
    public void setCorrectiveMeasure(String correctiveMeasure) { this.correctiveMeasure = correctiveMeasure; }
    public String getResponsibleEmpNo() { return responsibleEmpNo; }
    public void setResponsibleEmpNo(String responsibleEmpNo) { this.responsibleEmpNo = responsibleEmpNo; }
    public String getProofEmpNo() { return proofEmpNo; }
    public void setProofEmpNo(String proofEmpNo) { this.proofEmpNo = proofEmpNo; }
    public String getReviewerEmpNo() { return reviewerEmpNo; }
    public void setReviewerEmpNo(String reviewerEmpNo) { this.reviewerEmpNo = reviewerEmpNo; }
    public String getTargetEmpNo() { return targetEmpNo; }
    public void setTargetEmpNo(String targetEmpNo) { this.targetEmpNo = targetEmpNo; }
    public String getRectifyRemark() { return rectifyRemark; }
    public void setRectifyRemark(String rectifyRemark) { this.rectifyRemark = rectifyRemark; }
    public String getReviewOpinion() { return reviewOpinion; }
    public void setReviewOpinion(String reviewOpinion) { this.reviewOpinion = reviewOpinion; }
    public List<Long> getProofAttachmentIds() { return proofAttachmentIds; }
    public void setProofAttachmentIds(List<Long> proofAttachmentIds) { this.proofAttachmentIds = proofAttachmentIds; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Long getCategoryL1Id() { return categoryL1Id; }
    public void setCategoryL1Id(Long categoryL1Id) { this.categoryL1Id = categoryL1Id; }
    public Long getCategoryL2Id() { return categoryL2Id; }
    public void setCategoryL2Id(Long categoryL2Id) { this.categoryL2Id = categoryL2Id; }
}
