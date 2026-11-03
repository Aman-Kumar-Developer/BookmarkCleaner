package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.model.BookmarkAnalysisItem;
import com.aman.bookmark_cleaner.model.BookmarkAnalysisReport;
import com.aman.bookmark_cleaner.model.ComparisonItem;
import com.aman.bookmark_cleaner.model.DuplicateType;
import com.aman.bookmark_cleaner.model.Priority;
import com.aman.bookmark_cleaner.model.ProposedAction;
import com.aman.bookmark_cleaner.model.RedirectType;
import com.aman.bookmark_cleaner.model.ValidationOutcome;
import com.aman.bookmark_cleaner.model.ValidationResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service that transforms Stage 2 analysis items into user-facing comparison items
 * with assigned priorities, explicit proposed actions, and default toggle states.
 */
@Service
public class ComparisonService {

    /**
     * Transforms the given {@link BookmarkAnalysisReport} into an ordered list of {@link ComparisonItem}s.
     * High priority items appear first, followed by Medium, then Low.
     */
    public List<ComparisonItem> buildComparisonItems(BookmarkAnalysisReport report) {
        if (report == null || report.getItems() == null || report.getItems().isEmpty()) {
            return Collections.emptyList();
        }

        List<ComparisonItem> comparisonItems = new ArrayList<>();
        AtomicInteger seq = new AtomicInteger(1);

        for (BookmarkAnalysisItem analysisItem : report.getItems()) {
            ComparisonItem item = createComparisonItem(analysisItem, "item-" + seq.getAndIncrement());
            comparisonItems.add(item);
        }

        // Sort: HIGH -> MEDIUM -> LOW, maintaining stable initial ordering within each priority
        comparisonItems.sort(Comparator.comparing(ComparisonItem::getPriority));

        return Collections.unmodifiableList(comparisonItems);
    }

    private ComparisonItem createComparisonItem(BookmarkAnalysisItem item, String itemId) {
        String bookmarkId = item.getBookmarkId();
        String bookmarkName = item.getBookmarkName();
        String originalUrl = item.getOriginalUrl();
        String folderPath = item.getFolderPath();

        ValidationResult valResult = item.getValidationResult();
        ValidationOutcome outcome = valResult != null ? valResult.getOutcome() : ValidationOutcome.UNKNOWN;
        RedirectType redirectType = (valResult != null && valResult.getRedirectChain() != null)
                ? valResult.getRedirectChain().getRedirectType()
                : RedirectType.NONE;

        DuplicateType duplicateType = item.getDuplicateType();
        boolean isRemovalCandidate = item.isRemovalCandidate();

        // 1. Duplicate removal proposals take precedence if this bookmark is a designated removal candidate
        if (isRemovalCandidate) {
            String dupKind = (duplicateType == DuplicateType.EXACT) ? "Exact" : "Potential";
            String desc = dupKind + " duplicate bookmark (group " + item.getDuplicateGroupId() + ") — propose removing duplicate";
            return new ComparisonItem(
                    itemId,
                    bookmarkId,
                    bookmarkName,
                    originalUrl,
                    folderPath,
                    null, // Proposed URL is null for removal
                    outcome,
                    redirectType,
                    duplicateType,
                    Priority.MEDIUM,
                    ProposedAction.REMOVE_DUPLICATE,
                    desc,
                    true,  // applicable
                    true   // enabled by default
            );
        }

        // 2. Confirmed broken bookmark -> HIGH priority, REMOVE_BOOKMARK
        if (outcome == ValidationOutcome.BROKEN) {
            String desc = "Confirmed broken link (" + (valResult != null ? valResult.getMessage() : "404 Not Found") + ") — propose removing bookmark";
            return new ComparisonItem(
                    itemId,
                    bookmarkId,
                    bookmarkName,
                    originalUrl,
                    folderPath,
                    null, // Proposed URL is null for removal
                    outcome,
                    redirectType,
                    duplicateType,
                    Priority.HIGH,
                    ProposedAction.REMOVE_BOOKMARK,
                    desc,
                    true,  // applicable
                    true   // enabled by default
            );
        }

        // 3. Redirected bookmark
        if (outcome == ValidationOutcome.REDIRECTED && valResult != null) {
            String finalUrl = valResult.getFinalUrl();
            if (redirectType == RedirectType.CROSS_DOMAIN) {
                // Cross-domain redirect -> HIGH priority, UPDATE_URL
                String desc = "Cross-domain redirect to " + finalUrl + " — propose updating URL";
                return new ComparisonItem(
                        itemId,
                        bookmarkId,
                        bookmarkName,
                        originalUrl,
                        folderPath,
                        finalUrl,
                        outcome,
                        redirectType,
                        duplicateType,
                        Priority.HIGH,
                        ProposedAction.UPDATE_URL,
                        desc,
                        true,  // applicable
                        true   // enabled by default
                );
            } else {
                // Same-website redirect -> MEDIUM priority, UPDATE_URL
                String desc = "Same-website redirect to " + finalUrl + " — propose updating URL";
                return new ComparisonItem(
                        itemId,
                        bookmarkId,
                        bookmarkName,
                        originalUrl,
                        folderPath,
                        finalUrl,
                        outcome,
                        redirectType,
                        duplicateType,
                        Priority.MEDIUM,
                        ProposedAction.UPDATE_URL,
                        desc,
                        true,  // applicable
                        true   // enabled by default
                );
            }
        }

        // 4. Working bookmark -> LOW priority, NO_CHANGE
        if (outcome == ValidationOutcome.WORKING) {
            return new ComparisonItem(
                    itemId,
                    bookmarkId,
                    bookmarkName,
                    originalUrl,
                    folderPath,
                    originalUrl,
                    outcome,
                    redirectType,
                    duplicateType,
                    Priority.LOW,
                    ProposedAction.NO_CHANGE,
                    "Working bookmark — no change needed",
                    false, // applicable = false (no proposed modification)
                    false  // enabled = false (disabled control)
            );
        }

        // 5. UNKNOWN bookmark -> LOW priority, NO_CHANGE (safe default: retain original)
        String unknownDesc = "Status inconclusive (" + (valResult != null ? valResult.getMessage() : "Unknown") + ") — safe default: retain original";
        return new ComparisonItem(
                itemId,
                bookmarkId,
                bookmarkName,
                originalUrl,
                folderPath,
                originalUrl,
                outcome,
                redirectType,
                duplicateType,
                Priority.LOW,
                ProposedAction.NO_CHANGE,
                unknownDesc,
                false, // applicable = false
                false  // enabled = false
        );
    }
}
