package com.aman.bookmark_cleaner.model;

/**
 * Type of bookmark duplicate match.
 */
public enum DuplicateType {

    /**
     * Exact duplicate URL match after safe canonical normalization.
     */
    EXACT,

    /**
     * Potential duplicate match based on conservative variant normalization
     * (e.g. HTTP vs HTTPS, or WWW vs non-WWW variants).
     */
    POTENTIAL
}
