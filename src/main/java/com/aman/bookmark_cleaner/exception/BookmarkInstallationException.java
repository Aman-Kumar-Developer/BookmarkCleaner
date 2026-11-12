package com.aman.bookmark_cleaner.exception;

/**
 * Exception thrown when replacing or restoring bookmarks fails during staging,
 * backup rotation, atomic move, or verification.
 */
public class BookmarkInstallationException extends BookmarkException {

    public BookmarkInstallationException(String message) {
        super(message);
    }

    public BookmarkInstallationException(String message, Throwable cause) {
        super(message, cause);
    }
}
