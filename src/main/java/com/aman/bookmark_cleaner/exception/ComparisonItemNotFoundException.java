package com.aman.bookmark_cleaner.exception;

/**
 * Exception thrown when a comparison item ID is not found in the specified scan session.
 */
public class ComparisonItemNotFoundException extends BookmarkException {

    public ComparisonItemNotFoundException(String scanId, String itemId) {
        super("Comparison item '" + itemId + "' not found in scan session '" + scanId + "'");
    }
}
