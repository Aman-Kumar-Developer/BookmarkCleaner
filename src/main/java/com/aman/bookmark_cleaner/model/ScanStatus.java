package com.aman.bookmark_cleaner.model;

/**
 * Status lifecycle of a bookmark scan.
 */
public enum ScanStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
