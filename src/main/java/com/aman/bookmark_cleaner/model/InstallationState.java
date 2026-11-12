package com.aman.bookmark_cleaner.model;

/**
 * State lifecycle for an asynchronous browser bookmark installation or restoration operation.
 */
public enum InstallationState {
    PREPARING,
    WAITING_FOR_BROWSER_CLOSURE,
    INSTALLING,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED,
    RECOVERY_REQUIRED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
