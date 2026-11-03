package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.dto.DecisionUpdateResponse;
import com.aman.bookmark_cleaner.exception.ComparisonItemNotFoundException;
import com.aman.bookmark_cleaner.exception.InvalidDecisionException;
import com.aman.bookmark_cleaner.exception.ScanNotFoundException;
import com.aman.bookmark_cleaner.model.BookmarkDocument;
import com.aman.bookmark_cleaner.model.BookmarkNode;
import com.aman.bookmark_cleaner.model.BookmarkRoots;
import com.aman.bookmark_cleaner.model.ComparisonItem;
import com.aman.bookmark_cleaner.model.Priority;
import com.aman.bookmark_cleaner.model.ProposedAction;
import com.aman.bookmark_cleaner.model.ScanSession;
import com.aman.bookmark_cleaner.model.ValidationOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DecisionServiceTest {

    private ScanSessionManager sessionManager;
    private DecisionService decisionService;

    @BeforeEach
    void setUp() {
        sessionManager = new ScanSessionManager();
        decisionService = new DecisionService(sessionManager);
    }

    @Test
    @DisplayName("An applicable enabled proposal can be turned OFF, and turned back ON")
    void testToggleApplicableDecision() {
        BookmarkRoots roots = new BookmarkRoots();
        BookmarkNode bar = BookmarkNode.createFolder("1", "Bar");
        BookmarkNode b1 = BookmarkNode.createUrl("101", "Broken", "https://broken.com");
        bar.addChild(b1);
        roots.setBookmarkBar(bar);
        BookmarkDocument doc = new BookmarkDocument(roots);

        ScanSession session = sessionManager.createSession("Bookmarks.json", doc);

        ComparisonItem item = new ComparisonItem(
                "item-1", "101", "Broken", "https://broken.com", "Bar",
                null, ValidationOutcome.BROKEN, null, null,
                Priority.HIGH, ProposedAction.REMOVE_BOOKMARK, "Broken link", true, true
        );
        session.setComparisonItems(List.of(item));

        // Initial state is enabled
        assertTrue(item.isEnabled());

        // Turn OFF
        DecisionUpdateResponse respOff = decisionService.updateDecision(session.getScanId(), "item-1", false);
        assertFalse(respOff.isEnabled());
        assertFalse(item.isEnabled());

        // Turn back ON
        DecisionUpdateResponse respOn = decisionService.updateDecision(session.getScanId(), "item-1", true);
        assertTrue(respOn.isEnabled());
        assertTrue(item.isEnabled());
    }

    @Test
    @DisplayName("Attempting to enable a non-applicable/disabled item throws InvalidDecisionException")
    void testCannotEnableNonApplicableItem() {
        BookmarkDocument doc = new BookmarkDocument(new BookmarkRoots());
        ScanSession session = sessionManager.createSession("Bookmarks.json", doc);

        // Working item: applicable = false, enabled = false
        ComparisonItem workingItem = new ComparisonItem(
                "item-working", "102", "Working", "https://working.com", "Bar",
                "https://working.com", ValidationOutcome.WORKING, null, null,
                Priority.LOW, ProposedAction.NO_CHANGE, "No change", false, false
        );
        session.setComparisonItems(List.of(workingItem));

        assertThrows(InvalidDecisionException.class, () ->
                decisionService.updateDecision(session.getScanId(), "item-working", true));

        assertFalse(workingItem.isEnabled());
    }

    @Test
    @DisplayName("Updating a decision with null enabled value throws InvalidDecisionException")
    void testNullEnabledValueThrowsException() {
        BookmarkDocument doc = new BookmarkDocument(new BookmarkRoots());
        ScanSession session = sessionManager.createSession("Bookmarks.json", doc);

        ComparisonItem item = new ComparisonItem(
                "item-1", "101", "Site", "https://example.com", "Bar",
                null, ValidationOutcome.BROKEN, null, null,
                Priority.HIGH, ProposedAction.REMOVE_BOOKMARK, "Broken", true, true
        );
        session.setComparisonItems(List.of(item));

        assertThrows(InvalidDecisionException.class, () ->
                decisionService.updateDecision(session.getScanId(), "item-1", null));
    }

    @Test
    @DisplayName("Non-existent item ID throws ComparisonItemNotFoundException")
    void testNonExistentItemThrowsException() {
        BookmarkDocument doc = new BookmarkDocument(new BookmarkRoots());
        ScanSession session = sessionManager.createSession("Bookmarks.json", doc);

        assertThrows(ComparisonItemNotFoundException.class, () ->
                decisionService.updateDecision(session.getScanId(), "non-existent-item", true));
    }

    @Test
    @DisplayName("Non-existent scan ID throws ScanNotFoundException")
    void testNonExistentScanThrowsException() {
        assertThrows(ScanNotFoundException.class, () ->
                decisionService.updateDecision("unknown-scan-id", "item-1", true));
    }

    @Test
    @DisplayName("Updating decisions does NOT mutate the original parsed BookmarkDocument")
    void testDecisionsDoNotMutateOriginalDocument() {
        BookmarkRoots roots = new BookmarkRoots();
        BookmarkNode bar = BookmarkNode.createFolder("1", "Bar");
        BookmarkNode b1 = BookmarkNode.createUrl("101", "Original Title", "https://original.com");
        bar.addChild(b1);
        roots.setBookmarkBar(bar);
        BookmarkDocument doc = new BookmarkDocument(roots);

        ScanSession session = sessionManager.createSession("Bookmarks.json", doc);

        ComparisonItem item = new ComparisonItem(
                "item-1", "101", "Original Title", "https://original.com", "Bar",
                "https://new.com", ValidationOutcome.REDIRECTED, null, null,
                Priority.HIGH, ProposedAction.UPDATE_URL, "Redirect", true, true
        );
        session.setComparisonItems(List.of(item));

        // Update decision
        decisionService.updateDecision(session.getScanId(), "item-1", false);

        // Verify original document node is strictly identical
        BookmarkNode originalNode = doc.getRoots().getBookmarkBar().getChildren().get(0);
        assertEquals("https://original.com", originalNode.getUrl());
        assertEquals("Original Title", originalNode.getName());
    }
}
