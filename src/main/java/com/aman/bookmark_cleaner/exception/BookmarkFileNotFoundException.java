package com.aman.bookmark_cleaner.exception;

/**
 * Exception thrown when the specified bookmark file cannot be found.
 */
public class BookmarkFileNotFoundException extends BookmarkReaderException {

    public BookmarkFileNotFoundException(String message) {
        super(message);
    }

    public BookmarkFileNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
