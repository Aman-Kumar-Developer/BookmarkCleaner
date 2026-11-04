package com.aman.bookmark_cleaner.exception;

/**
 * Thrown when a requested browser is not recognized, unsupported, or not installed.
 */
public class BrowserNotFoundException extends BookmarkException {

    public BrowserNotFoundException(String message) {
        super(message);
    }

    public BrowserNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
