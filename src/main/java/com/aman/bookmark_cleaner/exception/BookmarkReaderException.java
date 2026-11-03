package com.aman.bookmark_cleaner.exception;

/**
 * Exception thrown when bookmark reading fails due to I/O or reader constraints (e.g. file unreadable or too large).
 */
public class BookmarkReaderException extends BookmarkException {

    public BookmarkReaderException(String message) {
        super(message);
    }

    public BookmarkReaderException(String message, Throwable cause) {
        super(message, cause);
    }
}
