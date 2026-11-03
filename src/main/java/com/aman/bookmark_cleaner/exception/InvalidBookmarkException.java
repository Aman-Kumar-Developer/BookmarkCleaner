package com.aman.bookmark_cleaner.exception;

/**
 * Exception thrown when a bookmark file contains invalid JSON, empty content,
 * or unsupported/malformed bookmark structures.
 */
public class InvalidBookmarkException extends BookmarkReaderException {

    public InvalidBookmarkException(String message) {
        super(message);
    }

    public InvalidBookmarkException(String message, Throwable cause) {
        super(message, cause);
    }
}
