package com.aman.bookmark_cleaner.dto;

public class UploadResponse {

    private final String scanId;
    private final String filename;
    private final int totalBookmarks;
    private final String status;

    public UploadResponse(String scanId, String filename, int totalBookmarks, String status) {
        this.scanId = scanId;
        this.filename = filename;
        this.totalBookmarks = totalBookmarks;
        this.status = status;
    }

    public String getScanId() {
        return scanId;
    }

    public String getFilename() {
        return filename;
    }

    public int getTotalBookmarks() {
        return totalBookmarks;
    }

    public String getStatus() {
        return status;
    }
}
