package com.aman.bookmark_cleaner.model;

import java.util.Objects;

/**
 * Identifies a single bookmark node within a duplicate group, retaining its original identity and location.
 */
public class DuplicateBookmarkItem {

    private final String bookmarkId;
    private final String bookmarkName;
    private final String originalUrl;
    private final String folderPath;

    public DuplicateBookmarkItem(String bookmarkId, String bookmarkName, String originalUrl, String folderPath) {
        this.bookmarkId = bookmarkId;
        this.bookmarkName = bookmarkName;
        this.originalUrl = originalUrl;
        this.folderPath = folderPath != null ? folderPath : "";
    }

    public String getBookmarkId() {
        return bookmarkId;
    }

    public String getBookmarkName() {
        return bookmarkName;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public String getFolderPath() {
        return folderPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DuplicateBookmarkItem that = (DuplicateBookmarkItem) o;
        return Objects.equals(bookmarkId, that.bookmarkId) &&
                Objects.equals(bookmarkName, that.bookmarkName) &&
                Objects.equals(originalUrl, that.originalUrl) &&
                Objects.equals(folderPath, that.folderPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bookmarkId, bookmarkName, originalUrl, folderPath);
    }

    @Override
    public String toString() {
        return "DuplicateBookmarkItem{" +
                "id='" + bookmarkId + '\'' +
                ", name='" + bookmarkName + '\'' +
                ", url='" + originalUrl + '\'' +
                ", folder='" + folderPath + '\'' +
                '}';
    }
}
