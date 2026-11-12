package com.aman.bookmark_cleaner.dto;

public class UploadResponse {

    private String scanId;
    private String filename;
    private int totalBookmarks;
    private String status;
    private boolean importedFromBrowser;
    private String browserId;
    private String browserName;
    private String profileId;
    private String profileName;

    public UploadResponse() {
    }

    public UploadResponse(String scanId, String filename, int totalBookmarks, String status) {
        this(scanId, filename, totalBookmarks, status, false, null, null, null, null);
    }

    public UploadResponse(String scanId,
                          String filename,
                          int totalBookmarks,
                          String status,
                          boolean importedFromBrowser,
                          String browserId,
                          String browserName,
                          String profileId,
                          String profileName) {
        this.scanId = scanId;
        this.filename = filename;
        this.totalBookmarks = totalBookmarks;
        this.status = status;
        this.importedFromBrowser = importedFromBrowser;
        this.browserId = browserId;
        this.browserName = browserName;
        this.profileId = profileId;
        this.profileName = profileName;
    }

    public String getScanId() {
        return scanId;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public int getTotalBookmarks() {
        return totalBookmarks;
    }

    public void setTotalBookmarks(int totalBookmarks) {
        this.totalBookmarks = totalBookmarks;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isImportedFromBrowser() {
        return importedFromBrowser;
    }

    public void setImportedFromBrowser(boolean importedFromBrowser) {
        this.importedFromBrowser = importedFromBrowser;
    }

    public String getBrowserId() {
        return browserId;
    }

    public void setBrowserId(String browserId) {
        this.browserId = browserId;
    }

    public String getBrowserName() {
        return browserName;
    }

    public void setBrowserName(String browserName) {
        this.browserName = browserName;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }
}