package com.aman.bookmark_cleaner.model;

import java.util.Objects;

/**
 * Represents a single user-facing comparison item combining original bookmark data,
 * analysis outcome, proposed modification, and user decision state.
 */
public class ComparisonItem {

    private final String itemId;
    private final String bookmarkId;
    private final String bookmarkName;
    private final String originalUrl;
    private final String folderPath;
    private final String proposedUrl;
    private final ValidationOutcome outcome;
    private final RedirectType redirectType;
    private final DuplicateType duplicateType;
    private final Priority priority;
    private final ProposedAction proposedAction;
    private final String changeDescription;
    private final boolean applicable;
    private volatile boolean enabled;

    public ComparisonItem(String itemId,
                          String bookmarkId,
                          String bookmarkName,
                          String originalUrl,
                          String folderPath,
                          String proposedUrl,
                          ValidationOutcome outcome,
                          RedirectType redirectType,
                          DuplicateType duplicateType,
                          Priority priority,
                          ProposedAction proposedAction,
                          String changeDescription,
                          boolean applicable,
                          boolean enabled) {
        this.itemId = itemId;
        this.bookmarkId = bookmarkId;
        this.bookmarkName = bookmarkName;
        this.originalUrl = originalUrl;
        this.folderPath = folderPath != null ? folderPath : "";
        this.proposedUrl = proposedUrl;
        this.outcome = outcome;
        this.redirectType = redirectType != null ? redirectType : RedirectType.NONE;
        this.duplicateType = duplicateType;
        this.priority = priority;
        this.proposedAction = proposedAction;
        this.changeDescription = changeDescription;
        this.applicable = applicable;
        this.enabled = enabled;
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

    public String getOriginalUrl() {
        return originalUrl;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public String getProposedUrl() {
        return proposedUrl;
    }

    public ValidationOutcome getOutcome() {
        return outcome;
    }

    public RedirectType getRedirectType() {
        return redirectType;
    }

    public DuplicateType getDuplicateType() {
        return duplicateType;
    }

    public Priority getPriority() {
        return priority;
    }

    public ProposedAction getProposedAction() {
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

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRemoval() {
        return (proposedAction == ProposedAction.REMOVE_BOOKMARK || proposedAction == ProposedAction.REMOVE_DUPLICATE) && enabled;
    }

    public boolean isUrlUpdate() {
        return proposedAction == ProposedAction.UPDATE_URL && enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComparisonItem that = (ComparisonItem) o;
        return Objects.equals(itemId, that.itemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId);
    }

    @Override
    public String toString() {
        return "ComparisonItem{" +
                "itemId='" + itemId + '\'' +
                ", bookmarkId='" + bookmarkId + '\'' +
                ", name='" + bookmarkName + '\'' +
                ", priority=" + priority +
                ", action=" + proposedAction +
                ", applicable=" + applicable +
                ", enabled=" + enabled +
                '}';
    }
}
