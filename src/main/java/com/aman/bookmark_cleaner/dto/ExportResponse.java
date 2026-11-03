package com.aman.bookmark_cleaner.dto;

public class ExportResponse {

    private final String scanId;
    private final String exportFilename;
    private final int totalBookmarksExported;
    private final int changesAppliedCount;
    private final String downloadUrl;

    public ExportResponse(String scanId, String exportFilename, int totalBookmarksExported, int changesAppliedCount, String downloadUrl) {
        this.scanId = scanId;
        this.exportFilename = exportFilename;
        this.totalBookmarksExported = totalBookmarksExported;
        this.changesAppliedCount = changesAppliedCount;
        this.downloadUrl = downloadUrl;
    }

    public String getScanId() {
        return scanId;
    }

    public String getExportFilename() {
        return exportFilename;
    }

    public int getTotalBookmarksExported() {
        return totalBookmarksExported;
    }

    public int getChangesAppliedCount() {
        return changesAppliedCount;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }
}
