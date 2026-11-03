package com.aman.bookmark_cleaner.dto;

public class StartScanRequest {

    private String scanId;

    public StartScanRequest() {
    }

    public StartScanRequest(String scanId) {
        this.scanId = scanId;
    }

    public String getScanId() {
        return scanId;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
    }
}
