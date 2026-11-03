package com.aman.bookmark_cleaner.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a group of duplicate bookmarks sharing an exact or potential URL match.
 * Designates one bookmark to be retained (e.g. first in tree traversal order)
 * and marks all remaining duplicates as proposed removal candidates.
 */
public class DuplicateGroup {

    private final String groupId;
    private final DuplicateType duplicateType;
    private final String normalizedUrl;
    private final DuplicateBookmarkItem retainedBookmark;
    private final List<DuplicateBookmarkItem> removalCandidates;
    private final List<DuplicateBookmarkItem> allItems;

    public DuplicateGroup(String groupId,
                          DuplicateType duplicateType,
                          String normalizedUrl,
                          DuplicateBookmarkItem retainedBookmark,
                          List<DuplicateBookmarkItem> removalCandidates) {
        this.groupId = groupId;
        this.duplicateType = duplicateType;
        this.normalizedUrl = normalizedUrl;
        this.retainedBookmark = retainedBookmark;
        this.removalCandidates = removalCandidates != null
                ? Collections.unmodifiableList(new ArrayList<>(removalCandidates))
                : Collections.emptyList();

        List<DuplicateBookmarkItem> combined = new ArrayList<>();
        if (retainedBookmark != null) {
            combined.add(retainedBookmark);
        }
        combined.addAll(this.removalCandidates);
        this.allItems = Collections.unmodifiableList(combined);
    }

    public String getGroupId() {
        return groupId;
    }

    public DuplicateType getDuplicateType() {
        return duplicateType;
    }

    public String getNormalizedUrl() {
        return normalizedUrl;
    }

    public DuplicateBookmarkItem getRetainedBookmark() {
        return retainedBookmark;
    }

    public List<DuplicateBookmarkItem> getRemovalCandidates() {
        return removalCandidates;
    }

    public List<DuplicateBookmarkItem> getAllItems() {
        return allItems;
    }

    public int size() {
        return allItems.size();
    }

    public boolean isExact() {
        return duplicateType == DuplicateType.EXACT;
    }

    public boolean isPotential() {
        return duplicateType == DuplicateType.POTENTIAL;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DuplicateGroup that = (DuplicateGroup) o;
        return Objects.equals(groupId, that.groupId) &&
                duplicateType == that.duplicateType &&
                Objects.equals(normalizedUrl, that.normalizedUrl) &&
                Objects.equals(retainedBookmark, that.retainedBookmark) &&
                Objects.equals(removalCandidates, that.removalCandidates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, duplicateType, normalizedUrl, retainedBookmark, removalCandidates);
    }

    @Override
    public String toString() {
        return "DuplicateGroup{" +
                "groupId='" + groupId + '\'' +
                ", duplicateType=" + duplicateType +
                ", totalCount=" + allItems.size() +
                ", removalCount=" + removalCandidates.size() +
                '}';
    }
}
