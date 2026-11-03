package com.aman.bookmark_cleaner.exception;

/**
 * Exception thrown when bookmark tree reconstruction or export fails.
 */
public class ExportException extends BookmarkException {

    public ExportException(String message) {
        super(message);
    }

    public ExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
