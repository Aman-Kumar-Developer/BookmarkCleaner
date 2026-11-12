package com.aman.bookmark_cleaner.exception;

/**
 * Exception thrown when a direct browser replacement or restore operation is attempted
 * while the target browser application is currently running.
 */
public class BrowserRunningException extends BookmarkException {

    public BrowserRunningException(String message) {
        super(message);
    }

    public BrowserRunningException(String message, Throwable cause) {
        super(message, cause);
    }
}
