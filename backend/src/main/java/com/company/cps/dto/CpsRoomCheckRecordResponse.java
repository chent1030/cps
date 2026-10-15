package com.company.cps.dto;

import com.company.cps.domain.CpsRoomCheckRecord;
import com.company.cps.domain.CpsRoomCheckRecordItem;

import java.util.List;

/** B3 点检单详情（mobile/回显）：明细含照片访问 URL。 */
public class CpsRoomCheckRecordResponse {
    private Long id;
    private Long planTaskId;
    private Long roomId;
    private String roomCode;
    private String roomName;
    private String checkEmpNo;
    private String recordStatus;
    private String judgeStatus;
    private Integer score;
    private String startedAt;
    private String submittedAt;
    private List<Item> items;

    public static class Item {
        private Long id;
        private Long checkItemId;
        private String itemCode;
        private String content;
        private String photoCategory;
        private Integer deductScore;
        private Integer configVersion;
        private String photoObjectKey;
        private String photoUrl;
        private String photoFileName;
        private String judgeResult;
        private String judgeReason;
        private String finalResult;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getCheckItemId() { return checkItemId; }
        public void setCheckItemId(Long checkItemId) { this.checkItemId = checkItemId; }
        public String getItemCode() { return itemCode; }
        public void setItemCode(String itemCode) { this.itemCode = itemCode; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getPhotoCategory() { return photoCategory; }
        public void setPhotoCategory(String photoCategory) { this.photoCategory = photoCategory; }
        public Integer getDeductScore() { return deductScore; }
        public void setDeductScore(Integer deductScore) { this.deductScore = deductScore; }
        public Integer getConfigVersion() { return configVersion; }
        public void setConfigVersion(Integer configVersion) { this.configVersion = configVersion; }
        public String getPhotoObjectKey() { return photoObjectKey; }
        public void setPhotoObjectKey(String photoObjectKey) { this.photoObjectKey = photoObjectKey; }
        public String getPhotoUrl() { return photoUrl; }
        public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
        public String getPhotoFileName() { return photoFileName; }
        public void setPhotoFileName(String photoFileName) { this.photoFileName = photoFileName; }
        public String getJudgeResult() { return judgeResult; }
        public void setJudgeResult(String judgeResult) { this.judgeResult = judgeResult; }
        public String getJudgeReason() { return judgeReason; }
        public void setJudgeReason(String judgeReason) { this.judgeReason = judgeReason; }
        public String getFinalResult() { return finalResult; }
        public void setFinalResult(String finalResult) { this.finalResult = finalResult; }
    }

    public static CpsRoomCheckRecordResponse from(CpsRoomCheckRecord record, List<CpsRoomCheckRecordItem> items,
                                                  java.util.function.UnaryOperator<String> photoUrlResolver) {
        CpsRoomCheckRecordResponse response = new CpsRoomCheckRecordResponse();
        response.id = record.getId();
        response.planTaskId = record.getPlanTaskId();
        response.roomId = record.getRoomId();
        response.roomCode = record.getRoomCode();
        response.roomName = record.getRoomName();
        response.checkEmpNo = record.getCheckEmpNo();
        response.recordStatus = record.getRecordStatus();
        response.judgeStatus = record.getJudgeStatus();
        response.score = record.getScore();
        response.startedAt = record.getStartedAt() == null ? null : record.getStartedAt().toString();
        response.submittedAt = record.getSubmittedAt() == null ? null : record.getSubmittedAt().toString();
        java.util.ArrayList<Item> list = new java.util.ArrayList<>();
        for (CpsRoomCheckRecordItem item : items) {
            Item view = new Item();
            view.id = item.getId();
            view.checkItemId = item.getCheckItemId();
            view.itemCode = item.getItemCode();
            view.content = item.getContent();
            view.photoCategory = item.getPhotoCategory();
            view.deductScore = item.getDeductScore();
            view.configVersion = item.getConfigVersion();
            view.photoObjectKey = item.getPhotoObjectKey();
            view.photoUrl = item.getPhotoObjectKey() == null ? null : photoUrlResolver.apply(item.getPhotoObjectKey());
            view.photoFileName = item.getPhotoFileName();
            view.judgeResult = item.getJudgeResult();
            view.judgeReason = item.getJudgeReason();
            view.finalResult = item.getFinalResult();
            list.add(view);
        }
        response.items = list;
        return response;
    }

    public Long getId() { return id; }
    public Long getPlanTaskId() { return planTaskId; }
    public Long getRoomId() { return roomId; }
    public String getRoomCode() { return roomCode; }
    public String getRoomName() { return roomName; }
    public String getCheckEmpNo() { return checkEmpNo; }
    public String getRecordStatus() { return recordStatus; }
    public String getJudgeStatus() { return judgeStatus; }
    public Integer getScore() { return score; }
    public String getStartedAt() { return startedAt; }
    public String getSubmittedAt() { return submittedAt; }
    public List<Item> getItems() { return items; }
}
