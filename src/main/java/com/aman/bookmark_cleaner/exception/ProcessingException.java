package com.aman.bookmark_cleaner.exception;

/**
 * Exception thrown when the bookmark processing or analysis engine encounters an error.
 */
public class ProcessingException extends BookmarkException {

    public ProcessingException(String message) {
        super(message);
    }

    public ProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
