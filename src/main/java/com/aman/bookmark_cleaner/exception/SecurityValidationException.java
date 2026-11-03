package com.aman.bookmark_cleaner.exception;

/**
 * Exception thrown when an address or target fails security validation (e.g. SSRF protection).
 */
public class SecurityValidationException extends BookmarkException {

    public SecurityValidationException(String message) {
        super(message);
    }

    public SecurityValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
