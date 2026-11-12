package com.aman.bookmark_cleaner.exception;

/**
 * Exception thrown when the browser's bookmark file has been modified on disk
 * since the time it was originally imported into the scan session.
 */
public class BookmarkFileModifiedException extends BookmarkException {

    public BookmarkFileModifiedException(String message) {
        super(message);
    }

    public BookmarkFileModifiedException(String message, Throwable cause) {
        super(message, cause);
    }
}
