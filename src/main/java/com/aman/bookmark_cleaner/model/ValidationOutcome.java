package com.aman.bookmark_cleaner.model;

/**
 * Structured outcome of URL validation.
 */
public enum ValidationOutcome {

    /**
     * URL responded successfully (HTTP 2xx) without a meaningful destination redirect.
     */
    WORKING,

    /**
     * URL redirected to another destination URL via one or more HTTP redirects.
     */
    REDIRECTED,

    /**
     * Strong, confirmed evidence that the resource is permanently unavailable (e.g. HTTP 404, HTTP 410).
     */
    BROKEN,

    /**
     * Inconclusive outcome, access restrictions (401, 403), rate limits (429), server errors (5xx),
     * connection failures, timeouts, redirect loops, or security blocks.
     */
    UNKNOWN
}
