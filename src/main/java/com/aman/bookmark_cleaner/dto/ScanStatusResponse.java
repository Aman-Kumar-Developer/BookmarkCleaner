package com.aman.bookmark_cleaner.dto;

public class ScanStatusResponse {

    private final String scanId;
    private final String status;
    private final int progressPercentage;
    private final int totalBookmarks;
    private final int processedBookmarks;
    private final long workingCount;
    private final long brokenCount;
    private final long redirectedCount;
    private final long duplicateCount;
    private final long unknownCount;
    private final String errorMessage;

    public ScanStatusResponse(String scanId,
                              String status,
                              int progressPercentage,
                              int totalBookmarks,
                              int processedBookmarks,
                              long workingCount,
                              long brokenCount,
                              long redirectedCount,
                              long duplicateCount,
                              long unknownCount,
                              String errorMessage) {
        this.scanId = scanId;
        this.status = status;
        this.progressPercentage = progressPercentage;
        this.totalBookmarks = totalBookmarks;
        this.processedBookmarks = processedBookmarks;
        this.workingCount = workingCount;
        this.brokenCount = brokenCount;
        this.redirectedCount = redirectedCount;
        this.duplicateCount = duplicateCount;
        this.unknownCount = unknownCount;
        this.errorMessage = errorMessage;
    }

    public String getScanId() {
        return scanId;
    }

    public String getStatus() {
        return status;
    }

    public int getProgressPercentage() {
        return progressPercentage;
    }

    public int getTotalBookmarks() {
        return totalBookmarks;
    }

    public int getProcessedBookmarks() {
        return processedBookmarks;
    }

    public long getWorkingCount() {
        return workingCount;
    }

    public long getBrokenCount() {
        return brokenCount;
    }

    public long getRedirectedCount() {
        return redirectedCount;
    }

    public long getDuplicateCount() {
        return duplicateCount;
    }

    public long getUnknownCount() {
        return unknownCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
