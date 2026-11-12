package com.aman.bookmark_cleaner.dto;

import java.util.Collections;
import java.util.List;

/**
 * Data transfer object conveying the live state of an installation or restoration transaction.
 */
public class InstallationOperationDto {

    private final String operationId;
    private final String scanId;
    private final String operationType;
    private final String backupId;
    private final String state;
    private final String browserName;
    private final String profileName;
    private final int targetBookmarkCount;
    private final int installedBookmarkCount;
    private final String backupCreated;
    private final boolean backupRotated;
    private final String message;
    private final String errorMessage;
    private final boolean browserRunning;
    private final boolean canCancel;
    private final List<BackupInfoDto> availableBackups;
    private final long updatedAt;

    public InstallationOperationDto(String operationId,
                                  String scanId,
                                  String operationType,
                                  String backupId,
                                  String state,
                                  String browserName,
                                  String profileName,
                                  int targetBookmarkCount,
                                  int installedBookmarkCount,
                                  String backupCreated,
                                  boolean backupRotated,
                                  String message,
                                  String errorMessage,
                                  boolean browserRunning,
                                  boolean canCancel,
                                  List<BackupInfoDto> availableBackups,
                                  long updatedAt) {
        this.operationId = operationId;
        this.scanId = scanId;
        this.operationType = operationType;
        this.backupId = backupId;
        this.state = state;
        this.browserName = browserName;
        this.profileName = profileName;
        this.targetBookmarkCount = targetBookmarkCount;
        this.installedBookmarkCount = installedBookmarkCount;
        this.backupCreated = backupCreated;
        this.backupRotated = backupRotated;
        this.message = message;
        this.errorMessage = errorMessage;
        this.browserRunning = browserRunning;
        this.canCancel = canCancel;
        this.availableBackups = availableBackups != null ? Collections.unmodifiableList(availableBackups) : Collections.emptyList();
        this.updatedAt = updatedAt;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getScanId() {
        return scanId;
    }

    public String getOperationType() {
        return operationType;
    }

    public String getBackupId() {
        return backupId;
    }

    public String getState() {
        return state;
    }

    public String getBrowserName() {
        return browserName;
    }

    public String getProfileName() {
        return profileName;
    }

    public int getTargetBookmarkCount() {
        return targetBookmarkCount;
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

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isBrowserRunning() {
        return browserRunning;
    }

    public boolean isCanCancel() {
        return canCancel;
    }

    public List<BackupInfoDto> getAvailableBackups() {
        return availableBackups;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }
}
