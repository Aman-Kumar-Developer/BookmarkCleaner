package com.aman.bookmark_cleaner.validator;

import com.aman.bookmark_cleaner.model.ValidationResult;

/**
 * Abstraction for validating bookmark URLs.
 */
public interface BookmarkValidator {

    /**
     * Validates a bookmark URL and returns a structured {@link ValidationResult}.
     *
     * @param url the URL to validate
     * @return the validation result
     */
    ValidationResult validate(String url);
}
