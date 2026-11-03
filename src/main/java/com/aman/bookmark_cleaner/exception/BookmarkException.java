package com.aman.bookmark_cleaner.exception;

/**
 * Base exception for all Bookmark Cleaner application errors.
 */
public class BookmarkException extends RuntimeException {

    public BookmarkException(String message) {
        super(message);
    }

    public BookmarkException(String message, Throwable cause) {
        super(message, cause);
    }
}
