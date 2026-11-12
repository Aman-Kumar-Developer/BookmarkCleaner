package com.aman.bookmark_cleaner.dto;

import java.util.Collections;
import java.util.List;

/**
 * Data transfer object returning the result or scheduled state of restoring a versioned backup.
 */
public class RestoreResponse {

    private final boolean success;
    private final String operationId;
    private final String state;
    private final String scanId;
    private final String backupId;
    private final String browserName;
    private final String profileName;
    private final int restoredBookmarkCount;
    private final String message;
    private final List<BackupInfoDto> availableBackups;

    public RestoreResponse(boolean success,
                           String scanId,
                           String backupId,
                           String browserName,
                           String profileName,
                           int restoredBookmarkCount,
                           String message,
                           List<BackupInfoDto> availableBackups) {
        this(success, null, success ? "COMPLETED" : "FAILED", scanId, backupId, browserName,
                profileName, restoredBookmarkCount, message, availableBackups);
    }

    public RestoreResponse(boolean success,
                           String operationId,
                           String state,
                           String scanId,
                           String backupId,
                           String browserName,
                           String profileName,
                           int restoredBookmarkCount,
                           String message,
                           List<BackupInfoDto> availableBackups) {
        this.success = success;
        this.operationId = operationId;
        this.state = state;
        this.scanId = scanId;
        this.backupId = backupId;
        this.browserName = browserName;
        this.profileName = profileName;
        this.restoredBookmarkCount = restoredBookmarkCount;
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

    public String getBackupId() {
        return backupId;
    }

    public String getBrowserName() {
        return browserName;
    }

    public String getProfileName() {
        return profileName;
    }

    public int getRestoredBookmarkCount() {
        return restoredBookmarkCount;
    }

    public String getMessage() {
        return message;
    }

    public List<BackupInfoDto> getAvailableBackups() {
        return availableBackups;
    }
}
