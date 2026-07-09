package com.company.cps.dto;

public class CpsKnowledgeVectorSyncResponse {
    private Integer syncedCount;

    public CpsKnowledgeVectorSyncResponse() {
    }

    public CpsKnowledgeVectorSyncResponse(Integer syncedCount) {
        this.syncedCount = syncedCount;
    }

    public Integer getSyncedCount() {
        return syncedCount;
    }

    public void setSyncedCount(Integer syncedCount) {
        this.syncedCount = syncedCount;
    }
}
