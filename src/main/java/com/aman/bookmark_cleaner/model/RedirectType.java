package com.aman.bookmark_cleaner.model;

/**
 * Classification of a redirect relative to the original domain.
 */
public enum RedirectType {

    /**
     * No redirect occurred.
     */
    NONE,

    /**
     * Redirect remains within the same effective website
     * (e.g. HTTP to HTTPS, www normalization, or changed path on the same host).
     */
    SAME_WEBSITE,

    /**
     * Redirect destination belongs to a different registrable domain or distinct website.
     */
    CROSS_DOMAIN
}
