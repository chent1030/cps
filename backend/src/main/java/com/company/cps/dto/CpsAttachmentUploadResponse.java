package com.company.cps.dto;

public class CpsAttachmentUploadResponse {
    private Long id;
    private String url;
    private String name;

    public CpsAttachmentUploadResponse() {
    }

    public CpsAttachmentUploadResponse(Long id, String url, String name) {
        this.id = id;
        this.url = url;
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
