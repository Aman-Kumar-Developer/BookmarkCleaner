package com.aman.bookmark_cleaner.dto;

import java.util.Collections;
import java.util.List;

/**
 * Data transfer object returning the result or scheduled state of direct bookmark replacement.
 */
public class InstallResponse {

    private final boolean success;
    private final String operationId;
    private final String state;
    private final String scanId;
    private final String browserName;
    private final String profileName;
    private final int installedBookmarkCount;
    private final String backupCreated;
    private final boolean backupRotated;
    private final String message;
    private final List<BackupInfoDto> availableBackups;

    public InstallResponse(boolean success,
                           String scanId,
                           String browserName,
                           String profileName,
                           int installedBookmarkCount,
                           String backupCreated,
                           boolean backupRotated,
                           String message,
                           List<BackupInfoDto> availableBackups) {
        this(success, null, success ? "COMPLETED" : "FAILED", scanId, browserName, profileName,
                installedBookmarkCount, backupCreated, backupRotated, message, availableBackups);
    }

    public InstallResponse(boolean success,
                           String operationId,
                           String state,
                           String scanId,
                           String browserName,
                           String profileName,
                           int installedBookmarkCount,
                           String backupCreated,
                           boolean backupRotated,
                           String message,
                           List<BackupInfoDto> availableBackups) {
        this.success = success;
        this.operationId = operationId;
        this.state = state;
        this.scanId = scanId;
        this.browserName = browserName;
        this.profileName = profileName;
        this.installedBookmarkCount = installedBookmarkCount;
        this.backupCreated = backupCreated;
        this.backupRotated = backupRotated;
        this.message = message;
        this.availableBackups = availableBackups != null ? Collections.unmodifiableList(availableBackups) : Collections.emptyList();
    }

    public boolean isSuccess() {
        return success;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getState() {
        return state;
    }

    public String getScanId() {
        return scanId;
    }

    public String getBrowserName() {
        return browserName;
    }

    public String getProfileName() {
        return profileName;
    }

    public int getInstalledBookmarkCount() {
        return installedBookmarkCount;
    }

    public String getBackupCreated() {
        return backupCreated;
    }

    public boolean isBackupRotated() {
        return backupRotated;
    }

    public String getMessage() {
        return message;
    }

    public List<BackupInfoDto> getAvailableBackups() {
        return availableBackups;
    }
}
