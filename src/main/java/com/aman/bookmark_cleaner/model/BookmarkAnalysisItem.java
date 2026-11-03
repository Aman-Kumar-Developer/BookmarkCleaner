package com.aman.bookmark_cleaner.model;

import java.util.Objects;

/**
 * Composite analysis container for an individual bookmark in a scan.
 * Associates the bookmark's stable identity and hierarchy with its validation and duplicate results.
 */
public class BookmarkAnalysisItem {

    private final String bookmarkId;
    private final String bookmarkName;
    private final String originalUrl;
    private final String folderPath;
    private final ValidationResult validationResult;
    private final String duplicateGroupId;
    private final DuplicateType duplicateType;
    private final boolean designatedRetained;

    public BookmarkAnalysisItem(String bookmarkId,
                                String bookmarkName,
                                String originalUrl,
                                String folderPath,
                                ValidationResult validationResult,
                                String duplicateGroupId,
                                DuplicateType duplicateType,
                                boolean designatedRetained) {
        this.bookmarkId = bookmarkId;
        this.bookmarkName = bookmarkName;
        this.originalUrl = originalUrl;
        this.folderPath = folderPath != null ? folderPath : "";
        this.validationResult = validationResult;
        this.duplicateGroupId = duplicateGroupId;
        this.duplicateType = duplicateType;
        this.designatedRetained = designatedRetained;
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

    public ValidationResult getValidationResult() {
        return validationResult;
    }

    public String getDuplicateGroupId() {
        return duplicateGroupId;
    }

    public DuplicateType getDuplicateType() {
        return duplicateType;
    }

    public boolean isDesignatedRetained() {
        return designatedRetained;
    }

    public boolean isDuplicate() {
        return duplicateGroupId != null;
    }

    public boolean isRemovalCandidate() {
        return isDuplicate() && !designatedRetained;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BookmarkAnalysisItem that = (BookmarkAnalysisItem) o;
        return designatedRetained == that.designatedRetained &&
                Objects.equals(bookmarkId, that.bookmarkId) &&
                Objects.equals(bookmarkName, that.bookmarkName) &&
                Objects.equals(originalUrl, that.originalUrl) &&
                Objects.equals(folderPath, that.folderPath) &&
                Objects.equals(validationResult, that.validationResult) &&
                Objects.equals(duplicateGroupId, that.duplicateGroupId) &&
                duplicateType == that.duplicateType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bookmarkId, bookmarkName, originalUrl, folderPath, validationResult, duplicateGroupId, duplicateType, designatedRetained);
    }

    @Override
    public String toString() {
        return "BookmarkAnalysisItem{" +
                "id='" + bookmarkId + '\'' +
                ", name='" + bookmarkName + '\'' +
                ", url='" + originalUrl + '\'' +
                ", outcome=" + (validationResult != null ? validationResult.getOutcome() : "null") +
                ", isDuplicate=" + isDuplicate() +
                '}';
    }
}
