package com.aman.bookmark_cleaner.model;

import java.nio.file.Path;

/**
 * Domain model representing a persistent browser bookmark replacement or restoration transaction.
 */
public class InstallationOperation {

    private final String operationId;
    private final String scanId;
    private final String operationType; // "INSTALL" or "RESTORE"
    private final String backupId;      // if RESTORE: "1" or "2"
    private final BrowserType browserType;
    private final String browserName;
    private final String profileId;
    private final String profileDisplayName;
    private final String profileDirectoryName;
    private final String originalBookmarkPathString;
    private final String stagedTempPathString;
    private final String expectedOriginalHash;
    private final long createdAt;

    private volatile InstallationState state;
    private volatile int targetBookmarkCount;
    private volatile int installedBookmarkCount;
    private volatile String backupCreated;
    private volatile boolean backupRotated;
    private volatile String message;
    private volatile String errorMessage;
    private volatile long updatedAt;

    public InstallationOperation(String operationId,
                                 String scanId,
                                 String operationType,
                                 String backupId,
                                 BrowserType browserType,
                                 String browserName,
                                 String profileId,
                                 String profileDisplayName,
                                 String profileDirectoryName,
                                 Path originalBookmarkPath,
                                 Path stagedTempPath,
                                 String expectedOriginalHash,
                                 int targetBookmarkCount) {
        this.operationId = operationId;
        this.scanId = scanId;
        this.operationType = operationType;
        this.backupId = backupId;
        this.browserType = browserType;
        this.browserName = browserName;
        this.profileId = profileId;
        this.profileDisplayName = profileDisplayName;
        this.profileDirectoryName = profileDirectoryName;
        this.originalBookmarkPathString = originalBookmarkPath != null ? originalBookmarkPath.toAbsolutePath().normalize().toString() : null;
        this.stagedTempPathString = stagedTempPath != null ? stagedTempPath.toAbsolutePath().normalize().toString() : null;
        this.expectedOriginalHash = expectedOriginalHash;
        this.targetBookmarkCount = targetBookmarkCount;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.state = InstallationState.PREPARING;
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

    public BrowserType getBrowserType() {
        return browserType;
    }

    public String getBrowserName() {
        return browserName;
    }

    public String getProfileId() {
        return profileId;
    }

    public String getProfileDisplayName() {
        return profileDisplayName;
    }

    public String getProfileDirectoryName() {
        return profileDirectoryName;
    }

    public Path getOriginalBookmarkPath() {
        return originalBookmarkPathString != null ? Path.of(originalBookmarkPathString) : null;
    }

    public String getOriginalBookmarkPathString() {
        return originalBookmarkPathString;
    }

    public Path getStagedTempPath() {
        return stagedTempPathString != null ? Path.of(stagedTempPathString) : null;
    }

    public String getStagedTempPathString() {
        return stagedTempPathString;
    }

    public String getExpectedOriginalHash() {
        return expectedOriginalHash;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public InstallationState getState() {
        return state;
    }

    public void setState(InstallationState state) {
        this.state = state;
        this.updatedAt = System.currentTimeMillis();
    }

    public int getTargetBookmarkCount() {
        return targetBookmarkCount;
    }

    public void setTargetBookmarkCount(int targetBookmarkCount) {
        this.targetBookmarkCount = targetBookmarkCount;
    }

    public int getInstalledBookmarkCount() {
        return installedBookmarkCount;
    }

    public void setInstalledBookmarkCount(int installedBookmarkCount) {
        this.installedBookmarkCount = installedBookmarkCount;
    }

    public String getBackupCreated() {
        return backupCreated;
    }

    public void setBackupCreated(String backupCreated) {
        this.backupCreated = backupCreated;
    }

    public boolean isBackupRotated() {
        return backupRotated;
    }

    public void setBackupRotated(boolean backupRotated) {
        this.backupRotated = backupRotated;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        this.updatedAt = System.currentTimeMillis();
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
