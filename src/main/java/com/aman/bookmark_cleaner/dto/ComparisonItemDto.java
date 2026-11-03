package com.aman.bookmark_cleaner.dto;

import com.aman.bookmark_cleaner.model.ComparisonItem;

public class ComparisonItemDto {

    private final String itemId;
    private final String bookmarkId;
    private final String bookmarkName;
    private final String folderPath;
    private final String originalUrl;
    private final String proposedUrl;
    private final String outcome;
    private final String redirectType;
    private final String duplicateType;
    private final String priority;
    private final String proposedAction;
    private final String changeDescription;
    private final boolean applicable;
    private final boolean enabled;

    public ComparisonItemDto(ComparisonItem item) {
        this.itemId = item.getItemId();
        this.bookmarkId = item.getBookmarkId();
        this.bookmarkName = item.getBookmarkName();
        this.folderPath = item.getFolderPath();
        this.originalUrl = item.getOriginalUrl();
        this.proposedUrl = item.getProposedUrl();
        this.outcome = item.getOutcome() != null ? item.getOutcome().name() : null;
        this.redirectType = item.getRedirectType() != null ? item.getRedirectType().name() : null;
        this.duplicateType = item.getDuplicateType() != null ? item.getDuplicateType().name() : null;
        this.priority = item.getPriority() != null ? item.getPriority().name() : null;
        this.proposedAction = item.getProposedAction() != null ? item.getProposedAction().name() : null;
        this.changeDescription = item.getChangeDescription();
        this.applicable = item.isApplicable();
        this.enabled = item.isEnabled();
    }

    public String getItemId() {
        return itemId;
    }

    public String getBookmarkId() {
        return bookmarkId;
    }

    public String getBookmarkName() {
        return bookmarkName;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public String getProposedUrl() {
        return proposedUrl;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getRedirectType() {
        return redirectType;
    }

    public String getDuplicateType() {
        return duplicateType;
    }

    public String getPriority() {
        return priority;
    }

    public String getProposedAction() {
        return proposedAction;
    }

    public String getChangeDescription() {
        return changeDescription;
    }

    public boolean isApplicable() {
        return applicable;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
