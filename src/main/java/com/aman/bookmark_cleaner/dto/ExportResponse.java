package com.aman.bookmark_cleaner.dto;

public class ExportResponse {

    private final String scanId;
    private final String exportFilename;
    private final int totalBookmarksExported;
    private final int changesAppliedCount;
    private final String downloadUrl;

    private final boolean importedFromBrowser;
    private final String browserId;
    private final String browserName;
    private final String profileId;
    private final String profileName;

    public ExportResponse(String scanId, String exportFilename, int totalBookmarksExported, int changesAppliedCount, String downloadUrl) {
        this(scanId, exportFilename, totalBookmarksExported, changesAppliedCount, downloadUrl, false, null, null, null, null);
    }

    public ExportResponse(String scanId,
                          String exportFilename,
                          int totalBookmarksExported,
                          int changesAppliedCount,
                          String downloadUrl,
                          boolean importedFromBrowser,
                          String browserId,
                          String browserName,
                          String profileId,
                          String profileName) {
        this.scanId = scanId;
        this.exportFilename = exportFilename;
        this.totalBookmarksExported = totalBookmarksExported;
        this.changesAppliedCount = changesAppliedCount;
        this.downloadUrl = downloadUrl;
        this.importedFromBrowser = importedFromBrowser;
        this.browserId = browserId;
        this.browserName = browserName;
        this.profileId = profileId;
        this.profileName = profileName;
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
