package com.aman.bookmark_cleaner.model;

/**
 * Priority categorization for proposed bookmark modifications.
 */
public enum Priority {

    /**
     * High priority: Confirmed broken links and cross-domain redirects requiring user review.
     */
    HIGH,

    /**
     * Medium priority: Same-website redirects and duplicate-removal proposals.
     */
    MEDIUM,

    /**
     * Low priority: Working bookmarks with no change needed.
     */
    LOW
}
