package com.company.cps.service;

public class MilvusSearchHit {

    private final Long imageId;
    private final Long caseId;
    private final Long categoryL1Id;
    private final Long categoryL2Id;
    private final double score;

    public MilvusSearchHit(Long imageId, Long caseId, Long categoryL1Id, Long categoryL2Id, double score) {
        this.imageId = imageId;
        this.caseId = caseId;
        this.categoryL1Id = categoryL1Id;
        this.categoryL2Id = categoryL2Id;
        this.score = score;
    }

    public Long getImageId() {
        return imageId;
    }

    public Long getCaseId() {
        return caseId;
    }

    public Long getCategoryL1Id() {
        return categoryL1Id;
    }

    public Long getCategoryL2Id() {
        return categoryL2Id;
    }

    public double getScore() {
        return score;
    }
}
