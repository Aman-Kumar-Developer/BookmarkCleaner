package com.aman.bookmark_cleaner.exception;

/**
 * Exception thrown when a requested versioned backup file cannot be located.
 */
public class BackupNotFoundException extends BookmarkException {

    public BackupNotFoundException(String message) {
        super(message);
    }

    public BackupNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
