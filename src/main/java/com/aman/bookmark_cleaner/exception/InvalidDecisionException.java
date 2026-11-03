package com.aman.bookmark_cleaner.exception;

/**
 * Exception thrown when a user decision update is invalid (e.g. attempting to enable a disabled item).
 */
public class InvalidDecisionException extends BookmarkException {

    public InvalidDecisionException(String message) {
        super(message);
    }
}
