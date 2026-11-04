package com.aman.bookmark_cleaner.exception;

/**
 * Thrown when a requested browser profile is not found or does not contain a readable bookmark file.
 */
public class BrowserProfileNotFoundException extends BookmarkException {

    public BrowserProfileNotFoundException(String message) {
        super(message);
    }

    public BrowserProfileNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
