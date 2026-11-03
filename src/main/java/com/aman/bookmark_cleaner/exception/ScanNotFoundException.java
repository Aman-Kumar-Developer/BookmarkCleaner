package com.aman.bookmark_cleaner.exception;

/**
 * Exception thrown when a requested scan session does not exist or has expired.
 */
public class ScanNotFoundException extends BookmarkException {

    public ScanNotFoundException(String scanId) {
        super("Scan session not found or expired: " + scanId);
    }
}
