package com.aman.bookmark_cleaner.dto;

public class ScanStatusResponse {

    private final String scanId;
    private final String status;
    private final int progressPercentage;
    private final int totalBookmarks;
    private final int processedBookmarks;
    private final int completedBookmarks;
    private final int pendingBookmarks;
    private final int failedBookmarks;
    private final long workingCount;
    private final long brokenCount;
    private final long redirectedCount;
    private final long duplicateCount;
    private final long unknownCount;
    private final String errorMessage;
    private final boolean importedFromBrowser;
    private final String browserId;
    private final String browserName;
    private final String profileId;
    private final String profileName;

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
        this(scanId, status, progressPercentage, totalBookmarks, processedBookmarks,
             processedBookmarks, Math.max(0, totalBookmarks - processedBookmarks),
             (int) (brokenCount + unknownCount), workingCount, brokenCount, redirectedCount,
             duplicateCount, unknownCount, errorMessage, false, null, null, null, null);
    }

    public ScanStatusResponse(String scanId,
                              String status,
                              int progressPercentage,
                              int totalBookmarks,
                              int processedBookmarks,
                              int completedBookmarks,
                              int pendingBookmarks,
                              int failedBookmarks,
                              long workingCount,
                              long brokenCount,
                              long redirectedCount,
                              long duplicateCount,
                              long unknownCount,
                              String errorMessage) {
        this(scanId, status, progressPercentage, totalBookmarks, processedBookmarks,
             completedBookmarks, pendingBookmarks, failedBookmarks, workingCount, brokenCount,
             redirectedCount, duplicateCount, unknownCount, errorMessage, false, null, null, null, null);
    }

    public ScanStatusResponse(String scanId,
                              String status,
                              int progressPercentage,
                              int totalBookmarks,
                              int processedBookmarks,
                              int completedBookmarks,
                              int pendingBookmarks,
                              int failedBookmarks,
                              long workingCount,
                              long brokenCount,
                              long redirectedCount,
                              long duplicateCount,
                              long unknownCount,
                              String errorMessage,
                              boolean importedFromBrowser,
                              String browserId,
                              String browserName,
                              String profileId,
                              String profileName) {
        this.scanId = scanId;
        this.status = status;
        this.progressPercentage = progressPercentage;
        this.totalBookmarks = totalBookmarks;
        this.processedBookmarks = processedBookmarks;
        this.completedBookmarks = completedBookmarks;
        this.pendingBookmarks = pendingBookmarks;
        this.failedBookmarks = failedBookmarks;
        this.workingCount = workingCount;
        this.brokenCount = brokenCount;
        this.redirectedCount = redirectedCount;
        this.duplicateCount = duplicateCount;
        this.unknownCount = unknownCount;
        this.errorMessage = errorMessage;
        this.importedFromBrowser = importedFromBrowser;
        this.browserId = browserId;
        this.browserName = browserName;
        this.profileId = profileId;
        this.profileName = profileName;
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

    public int getCompletedBookmarks() {
        return completedBookmarks;
    }

    public int getPendingBookmarks() {
        return pendingBookmarks;
    }

    public int getFailedBookmarks() {
        return failedBookmarks;
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

    public boolean isImportedFromBrowser() {
        return importedFromBrowser;
    }

    public String getBrowserId() {
        return browserId;
    }

    public String getBrowserName() {
        return browserName;
    }

    public String getProfileId() {
        return profileId;
    }

    public String getProfileName() {
        return profileName;
    }
}
