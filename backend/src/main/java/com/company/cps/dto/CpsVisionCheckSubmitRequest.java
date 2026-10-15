package com.company.cps.dto;

/**
 * B6 视觉点检提交请求（POST /api/cps/room-checks）。
 * roomId/checkItemId/roomType/createdBy 必填；photoObjectKey/photoUrl 至少其一（图片证据持久化）。
 */
public class CpsVisionCheckSubmitRequest {
    private Long roomId;
    private Long checkItemId;
    private String roomType;
    private String photoObjectKey;
    private String photoUrl;
    private String createdBy;

    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public Long getCheckItemId() { return checkItemId; }
    public void setCheckItemId(Long checkItemId) { this.checkItemId = checkItemId; }
    public String getRoomType() { return roomType; }
    public void setRoomType(String roomType) { this.roomType = roomType; }
    public String getPhotoObjectKey() { return photoObjectKey; }
    public void setPhotoObjectKey(String photoObjectKey) { this.photoObjectKey = photoObjectKey; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}