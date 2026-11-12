package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.model.BookmarkAnalysisItem;

/**
 * Progress listener callback invoked incrementally during bookmark validation and analysis.
 */
@FunctionalInterface
public interface BookmarkProgressListener {

    /**
     * Invoked when an individual bookmark completes validation and analysis.
     *
     * @param item the analyzed bookmark item
     * @param completedCount total number of bookmarks that have finished processing so far
     * @param totalCount total number of bookmarks scheduled for analysis
     */
    void onBookmarkCompleted(BookmarkAnalysisItem item, int completedCount, int totalCount);

    /**
     * Optional notification when duplicate detection stage finishes.
     *
     * @param duplicateCount number of duplicates detected
     */
    default void onDuplicatesDetected(int duplicateCount) {
    }
}
