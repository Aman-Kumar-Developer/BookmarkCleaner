package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.model.BookmarkDocument;
import com.aman.bookmark_cleaner.model.BookmarkNode;
import com.aman.bookmark_cleaner.model.BookmarkRoots;
import com.aman.bookmark_cleaner.model.ComparisonItem;
import com.aman.bookmark_cleaner.model.DuplicateType;
import com.aman.bookmark_cleaner.model.Priority;
import com.aman.bookmark_cleaner.model.ProposedAction;
import com.aman.bookmark_cleaner.model.RedirectType;
import com.aman.bookmark_cleaner.model.ScanSession;
import com.aman.bookmark_cleaner.model.ValidationOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BookmarkTreeReconstructionServiceTest {

    private BookmarkTreeReconstructionService reconstructionService;
    private ScanSessionManager sessionManager;

    @BeforeEach
    void setUp() {
        reconstructionService = new BookmarkTreeReconstructionService();
        sessionManager = new ScanSessionManager();
    }

    @Test
    @DisplayName("Accepted removal removes broken bookmark, while rejected removal retains original")
    void testBrokenBookmarkAcceptedAndRejectedRemoval() {
        BookmarkRoots roots = new BookmarkRoots();
        BookmarkNode bar = BookmarkNode.createFolder("1", "Bookmarks bar");

        BookmarkNode b1 = BookmarkNode.createUrl("101", "Accepted Broken", "https://broken-accepted.com");
        BookmarkNode b2 = BookmarkNode.createUrl("102", "Rejected Broken", "https://broken-rejected.com");
        bar.addChild(b1);
        bar.addChild(b2);
        roots.setBookmarkBar(bar);

        BookmarkDocument originalDoc = new BookmarkDocument(roots);
        ScanSession session = sessionManager.createSession("Bookmarks.json", originalDoc);

        // Item 1: Accepted broken (enabled = true) -> should be removed
        ComparisonItem item1 = new ComparisonItem(
                "item-1", "101", "Accepted Broken", "https://broken-accepted.com", "Bar",
                null, ValidationOutcome.BROKEN, null, null,
                Priority.HIGH, ProposedAction.REMOVE_BOOKMARK, "Broken", true, true
        );

        // Item 2: Rejected broken (enabled = false) -> should be retained
        ComparisonItem item2 = new ComparisonItem(
                "item-2", "102", "Rejected Broken", "https://broken-rejected.com", "Bar",
                null, ValidationOutcome.BROKEN, null, null,
                Priority.HIGH, ProposedAction.REMOVE_BOOKMARK, "Broken", true, false
        );

        session.setComparisonItems(List.of(item1, item2));

        BookmarkTreeReconstructionService.ReconstructionResult result = reconstructionService.reconstruct(session);
        BookmarkDocument recDoc = result.reconstructedDocument();

        assertEquals(1, result.changesAppliedCount(), "Only 1 accepted removal should be counted");
        BookmarkNode recBar = recDoc.getRoots().getBookmarkBar();
        assertNotNull(recBar);
        assertEquals(1, recBar.getChildren().size(), "Only rejected broken bookmark should remain");
        assertEquals("102", recBar.getChildren().get(0).getId());
        assertEquals("https://broken-rejected.com", recBar.getChildren().get(0).getUrl());

        // Original document must remain untouched!
        assertEquals(2, originalDoc.getRoots().getBookmarkBar().getChildren().size());
    }

    @Test
    @DisplayName("Accepted redirect updates URL, while rejected redirect retains original URL")
    void testRedirectAcceptedAndRejectedUpdate() {
        BookmarkRoots roots = new BookmarkRoots();
        BookmarkNode bar = BookmarkNode.createFolder("1", "Bookmarks bar");

        BookmarkNode b1 = BookmarkNode.createUrl("101", "Accepted Redirect", "http://old-accepted.com");
        BookmarkNode b2 = BookmarkNode.createUrl("102", "Rejected Redirect", "http://old-rejected.com");
        bar.addChild(b1);
        bar.addChild(b2);
        roots.setBookmarkBar(bar);

        BookmarkDocument originalDoc = new BookmarkDocument(roots);
        ScanSession session = sessionManager.createSession("Bookmarks.json", originalDoc);

        // Accepted redirect: enabled = true
        ComparisonItem item1 = new ComparisonItem(
                "item-1", "101", "Accepted Redirect", "http://old-accepted.com", "Bar",
                "https://new-accepted.com", ValidationOutcome.REDIRECTED, RedirectType.CROSS_DOMAIN, null,
                Priority.HIGH, ProposedAction.UPDATE_URL, "Redirect", true, true
        );

        // Rejected redirect: enabled = false
        ComparisonItem item2 = new ComparisonItem(
                "item-2", "102", "Rejected Redirect", "http://old-rejected.com", "Bar",
                "https://new-rejected.com", ValidationOutcome.REDIRECTED, RedirectType.SAME_WEBSITE, null,
                Priority.MEDIUM, ProposedAction.UPDATE_URL, "Redirect", true, false
        );

        session.setComparisonItems(List.of(item1, item2));

        BookmarkTreeReconstructionService.ReconstructionResult result = reconstructionService.reconstruct(session);
        BookmarkDocument recDoc = result.reconstructedDocument();

        assertEquals(1, result.changesAppliedCount());
        BookmarkNode recBar = recDoc.getRoots().getBookmarkBar();
        assertEquals(2, recBar.getChildren().size());

        assertEquals("https://new-accepted.com", recBar.getChildren().get(0).getUrl(), "Accepted redirect must be updated");
        assertEquals("http://old-rejected.com", recBar.getChildren().get(1).getUrl(), "Rejected redirect must retain original URL");
    }

    @Test
    @DisplayName("Accepted duplicate removal removes only designated duplicate; retained copy is kept")
    void testDuplicateRemovalRemovesOnlyDesignatedDuplicate() {
        BookmarkRoots roots = new BookmarkRoots();
        BookmarkNode bar = BookmarkNode.createFolder("1", "Bookmarks bar");

        BookmarkNode b1 = BookmarkNode.createUrl("101", "GitHub Main", "https://github.com");
        BookmarkNode b2 = BookmarkNode.createUrl("102", "GitHub Copy", "https://github.com");
        bar.addChild(b1);
        bar.addChild(b2);
        roots.setBookmarkBar(bar);

        BookmarkDocument originalDoc = new BookmarkDocument(roots);
        ScanSession session = sessionManager.createSession("Bookmarks.json", originalDoc);

        // b1 is retained: NO_CHANGE, disabled
        ComparisonItem item1 = new ComparisonItem(
                "item-1", "101", "GitHub Main", "https://github.com", "Bar",
                "https://github.com", ValidationOutcome.WORKING, null, null,
                Priority.LOW, ProposedAction.NO_CHANGE, "Retained copy", false, false
        );

        // b2 is removal candidate: REMOVE_DUPLICATE, enabled = true
        ComparisonItem item2 = new ComparisonItem(
                "item-2", "102", "GitHub Copy", "https://github.com", "Bar",
                null, ValidationOutcome.WORKING, null, DuplicateType.EXACT,
                Priority.MEDIUM, ProposedAction.REMOVE_DUPLICATE, "Remove duplicate", true, true
        );

        session.setComparisonItems(List.of(item1, item2));

        BookmarkTreeReconstructionService.ReconstructionResult result = reconstructionService.reconstruct(session);
        BookmarkDocument recDoc = result.reconstructedDocument();

        assertEquals(1, result.changesAppliedCount());
        BookmarkNode recBar = recDoc.getRoots().getBookmarkBar();
        assertEquals(1, recBar.getChildren().size());
        assertEquals("101", recBar.getChildren().get(0).getId(), "Designated retained copy must remain");
    }

    @Test
    @DisplayName("Nested folder hierarchy, order, and metadata are strictly preserved")
    void testNestedHierarchyAndMetadataPreserved() {
        BookmarkRoots roots = new BookmarkRoots();
        BookmarkNode bar = BookmarkNode.createFolder("1", "Bar");
        bar.setAdditionalProperty("custom_folder_prop", "blue");

        BookmarkNode folderA = BookmarkNode.createFolder("10", "Dev Resources");
        folderA.setDateAdded("13300000000000000");

        BookmarkNode b1 = BookmarkNode.createUrl("101", "Link 1", "https://site1.com");
        b1.setGuid("guid-101");
        b1.setAdditionalProperty("custom_node_prop", 42);

        BookmarkNode folderB = BookmarkNode.createFolder("20", "Empty Subfolder");

        folderA.addChild(b1);
        folderA.addChild(folderB);
        bar.addChild(folderA);
        roots.setBookmarkBar(bar);

        BookmarkDocument originalDoc = new BookmarkDocument(roots);
        originalDoc.setChecksum("checksum-xyz");
        originalDoc.setAdditionalProperty("document_meta", "v1.2");

        ScanSession session = sessionManager.createSession("Bookmarks.json", originalDoc);
        // No modifications accepted (NO_CHANGE)
        ComparisonItem item1 = new ComparisonItem(
                "item-1", "101", "Link 1", "https://site1.com", "Bar / Dev Resources",
                "https://site1.com", ValidationOutcome.WORKING, null, null,
                Priority.LOW, ProposedAction.NO_CHANGE, "Working", false, false
        );
        session.setComparisonItems(List.of(item1));

        BookmarkTreeReconstructionService.ReconstructionResult result = reconstructionService.reconstruct(session);
        BookmarkDocument recDoc = result.reconstructedDocument();

        assertEquals(0, result.changesAppliedCount());
        assertEquals("checksum-xyz", recDoc.getChecksum());
        assertEquals("v1.2", recDoc.getAdditionalProperty("document_meta"));

        BookmarkNode recBar = recDoc.getRoots().getBookmarkBar();
        assertEquals("blue", recBar.getAdditionalProperty("custom_folder_prop"));

        BookmarkNode recFolderA = recBar.getChildren().get(0);
        assertEquals("10", recFolderA.getId());
        assertEquals("Dev Resources", recFolderA.getName());
        assertEquals("13300000000000000", recFolderA.getDateAdded());
        assertEquals(2, recFolderA.getChildren().size());

        BookmarkNode recB1 = recFolderA.getChildren().get(0);
        assertEquals("101", recB1.getId());
        assertEquals("guid-101", recB1.getGuid());
        assertEquals(42, recB1.getAdditionalProperty("custom_node_prop"));

        BookmarkNode recFolderB = recFolderA.getChildren().get(1);
        assertEquals("20", recFolderB.getId());
        assertTrue(recFolderB.getChildren().isEmpty(), "Empty folder must be preserved");
    }
}
