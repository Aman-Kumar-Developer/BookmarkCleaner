package com.aman.bookmark_cleaner.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Complete analysis report produced by the processing engine for a bookmark collection.
 */
public class BookmarkAnalysisReport {

    private final int totalBookmarksAnalyzed;
    private final int uniqueUrlsCount;
    private final List<BookmarkAnalysisItem> items;
    private final List<DuplicateGroup> duplicateGroups;
    private final Map<ValidationOutcome, Long> outcomeCounts;

    public BookmarkAnalysisReport(List<BookmarkAnalysisItem> items, List<DuplicateGroup> duplicateGroups, int uniqueUrlsCount) {
        this.items = items != null ? Collections.unmodifiableList(new ArrayList<>(items)) : Collections.emptyList();
        this.duplicateGroups = duplicateGroups != null ? Collections.unmodifiableList(new ArrayList<>(duplicateGroups)) : Collections.emptyList();
        this.totalBookmarksAnalyzed = this.items.size();
        this.uniqueUrlsCount = uniqueUrlsCount;

        Map<ValidationOutcome, Long> counts = new EnumMap<>(ValidationOutcome.class);
        for (ValidationOutcome outcome : ValidationOutcome.values()) {
            counts.put(outcome, 0L);
        }
        for (BookmarkAnalysisItem item : this.items) {
            if (item.getValidationResult() != null) {
                ValidationOutcome outcome = item.getValidationResult().getOutcome();
                counts.put(outcome, counts.getOrDefault(outcome, 0L) + 1);
            }
        }
        this.outcomeCounts = Collections.unmodifiableMap(counts);
    }

    public int getTotalBookmarksAnalyzed() {
        return totalBookmarksAnalyzed;
    }

    public int getUniqueUrlsCount() {
        return uniqueUrlsCount;
    }

    public List<BookmarkAnalysisItem> getItems() {
        return items;
    }

    public List<DuplicateGroup> getDuplicateGroups() {
        return duplicateGroups;
    }

    public Map<ValidationOutcome, Long> getOutcomeCounts() {
        return outcomeCounts;
    }

    public long getCount(ValidationOutcome outcome) {
        return outcomeCounts.getOrDefault(outcome, 0L);
    }

    public long getExactDuplicateCount() {
        return duplicateGroups.stream().filter(DuplicateGroup::isExact).mapToInt(g -> g.getRemovalCandidates().size()).sum();
    }

    public long getPotentialDuplicateCount() {
        return duplicateGroups.stream().filter(DuplicateGroup::isPotential).mapToInt(g -> g.getRemovalCandidates().size()).sum();
    }

    @Override
    public String toString() {
        return "BookmarkAnalysisReport{" +
                "total=" + totalBookmarksAnalyzed +
                ", uniqueUrls=" + uniqueUrlsCount +
                ", working=" + getCount(ValidationOutcome.WORKING) +
                ", redirected=" + getCount(ValidationOutcome.REDIRECTED) +
                ", broken=" + getCount(ValidationOutcome.BROKEN) +
                ", unknown=" + getCount(ValidationOutcome.UNKNOWN) +
                ", duplicateGroups=" + duplicateGroups.size() +
                '}';
    }
}
