package com.aman.bookmark_cleaner.dto;

import java.util.Collections;
import java.util.List;

/**
 * Data transfer object conveying backup status, active file details, and profile metadata for a scan session.
 */
public class BackupManagementResponse {

    private final String scanId;
    private final boolean importedFromBrowser;
    private final String browserId;
    private final String browserName;
    private final String profileId;
    private final String profileName;
    private final boolean bookmarksFileExists;
    private final boolean browserRunning;
    private final int activeBookmarkCount;
    private final long activeFileSize;
    private final long activeLastModified;
    private final BackupInfoDto backup1;
    private final BackupInfoDto backup2;
    private final List<BackupInfoDto> backups;
    private final InstallationOperationDto activeOperation;

    public BackupManagementResponse(String scanId,
                                    boolean importedFromBrowser,
                                    String browserId,
                                    String browserName,
                                    String profileId,
                                    String profileName,
                                    boolean bookmarksFileExists,
                                    boolean browserRunning,
                                    List<BackupInfoDto> backups) {
        this(scanId, importedFromBrowser, browserId, browserName, profileId, profileName,
                bookmarksFileExists, browserRunning, 0, 0, 0, null, null, backups, null);
    }

    public BackupManagementResponse(String scanId,
                                    boolean importedFromBrowser,
                                    String browserId,
                                    String browserName,
                                    String profileId,
                                    String profileName,
                                    boolean bookmarksFileExists,
                                    boolean browserRunning,
                                    int activeBookmarkCount,
                                    long activeFileSize,
                                    long activeLastModified,
                                    BackupInfoDto backup1,
                                    BackupInfoDto backup2,
                                    List<BackupInfoDto> backups,
                                    InstallationOperationDto activeOperation) {
        this.scanId = scanId;
        this.importedFromBrowser = importedFromBrowser;
        this.browserId = browserId;
        this.browserName = browserName;
        this.profileId = profileId;
        this.profileName = profileName;
        this.bookmarksFileExists = bookmarksFileExists;
        this.browserRunning = browserRunning;
        this.activeBookmarkCount = activeBookmarkCount;
        this.activeFileSize = activeFileSize;
        this.activeLastModified = activeLastModified;
        this.backup1 = backup1;
        this.backup2 = backup2;
        this.backups = backups != null ? Collections.unmodifiableList(backups) : Collections.emptyList();
        this.activeOperation = activeOperation;
    }

    public String getScanId() {
        return scanId;
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

    public boolean isBookmarksFileExists() {
        return bookmarksFileExists;
    }

    public boolean isBrowserRunning() {
        return browserRunning;
    }

    public int getActiveBookmarkCount() {
        return activeBookmarkCount;
    }

    public long getActiveFileSize() {
        return activeFileSize;
    }

    public long getActiveLastModified() {
        return activeLastModified;
    }

    public BackupInfoDto getBackup1() {
        return backup1;
    }

    public BackupInfoDto getBackup2() {
        return backup2;
    }

    public List<BackupInfoDto> getBackups() {
        return backups;
    }

    public InstallationOperationDto getActiveOperation() {
        return activeOperation;
    }
}
