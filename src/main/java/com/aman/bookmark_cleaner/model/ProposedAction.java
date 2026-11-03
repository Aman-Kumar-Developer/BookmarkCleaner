package com.aman.bookmark_cleaner.model;

/**
 * Action proposed by the analysis engine to be applied during export if accepted.
 */
public enum ProposedAction {

    /**
     * Remove the bookmark because the link is confirmed broken (404/410).
     */
    REMOVE_BOOKMARK,

    /**
     * Update the bookmark URL with the redirected destination.
     */
    UPDATE_URL,

    /**
     * Remove this specific bookmark entry because it is a designated duplicate.
     */
    REMOVE_DUPLICATE,

    /**
     * No modification proposed (working or inconclusive bookmark).
     */
    NO_CHANGE
}
