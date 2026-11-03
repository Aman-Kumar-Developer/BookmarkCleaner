package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.dto.ExportResponse;
import com.aman.bookmark_cleaner.model.BookmarkDocument;
import com.aman.bookmark_cleaner.model.BookmarkNode;
import com.aman.bookmark_cleaner.model.BookmarkRoots;
import com.aman.bookmark_cleaner.model.ComparisonItem;
import com.aman.bookmark_cleaner.model.Priority;
import com.aman.bookmark_cleaner.model.ProposedAction;
import com.aman.bookmark_cleaner.model.RedirectType;
import com.aman.bookmark_cleaner.model.ScanSession;
import com.aman.bookmark_cleaner.model.ScanStatus;
import com.aman.bookmark_cleaner.model.ValidationOutcome;
import com.aman.bookmark_cleaner.parser.JsonBookmarkReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookmarkExportServiceTest {

    private BookmarkExportService exportService;
    private ScanSessionManager sessionManager;
    private BookmarkTreeReconstructionService reconstructionService;
    private ObjectMapper objectMapper;
    private JsonBookmarkReader jsonReader;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        jsonReader = new JsonBookmarkReader(objectMapper);
        reconstructionService = new BookmarkTreeReconstructionService();
        sessionManager = new ScanSessionManager();
        exportService = new BookmarkExportService(reconstructionService, sessionManager, objectMapper);
    }

    private BookmarkDocument createSampleDocument() {
        BookmarkNode b1 = BookmarkNode.createUrl("1", "Working", "https://example.com/work");
        b1.setDateAdded("13300000000000000");

        BookmarkNode b2 = BookmarkNode.createUrl("2", "Old URL", "http://example.com/old");
        b2.setDateAdded("13300000000000001");

        BookmarkNode b3 = BookmarkNode.createUrl("3", "Broken", "https://broken.domain/404");
        b3.setDateAdded("13300000000000002");

        BookmarkNode bookmarkBar = BookmarkNode.createFolder("0", "Bookmark Bar");
        bookmarkBar.addChild(b1);
        bookmarkBar.addChild(b2);
        bookmarkBar.addChild(b3);

        BookmarkNode other = BookmarkNode.createFolder("10", "Other Bookmarks");

        BookmarkRoots roots = new BookmarkRoots(bookmarkBar, other, null);
        BookmarkDocument doc = new BookmarkDocument(roots);
        doc.setChecksum("sample-checksum");
        return doc;
    }

    @Test
    @DisplayName("generateExport creates clean JSON that re-parses into valid BookmarkDocument")
    void testGenerateExportAndReparse() throws IOException {
        BookmarkDocument originalDoc = createSampleDocument();
        ScanSession session = new ScanSession("scan-123", "bookmarks.json", originalDoc);
        session.setStatus(ScanStatus.COMPLETED);

        // Add comparison items
        ComparisonItem item1 = new ComparisonItem(
                "item-1", "2", "Old URL", "http://example.com/old", "Bookmark Bar",
                "https://example.com/new", ValidationOutcome.REDIRECTED, RedirectType.SAME_WEBSITE,
                null, Priority.MEDIUM, ProposedAction.UPDATE_URL,
                "HTTP 301 Moved Permanently", true, true
        );
        ComparisonItem item2 = new ComparisonItem(
                "item-2", "3", "Broken", "https://broken.domain/404", "Bookmark Bar",
                null, ValidationOutcome.BROKEN, RedirectType.NONE,
                null, Priority.HIGH, ProposedAction.REMOVE_BOOKMARK,
                "HTTP 404 Not Found", true, true
        );

        session.setComparisonItems(List.of(item1, item2));
        sessionManager.registerSession(session);

        ExportResponse response = exportService.generateExport("scan-123");

        assertNotNull(response);
        assertEquals("scan-123", response.getScanId());
        assertEquals("bookmarks-cleaned.json", response.getExportFilename());
        assertEquals(2, response.getChangesAppliedCount());
        assertEquals(2, response.getTotalBookmarksExported()); // 3 original - 1 removed = 2
        assertEquals("/api/scans/scan-123/export", response.getDownloadUrl());

        // Validate raw bytes
        byte[] bytes = exportService.getExportBytes("scan-123");
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        // Re-parse with JsonBookmarkReader to guarantee Chromium JSON compliance
        Path tempFile = Files.createTempFile("cleaned-bookmarks-", ".json");
        try {
            Files.write(tempFile, bytes);
            BookmarkDocument reloadedDoc = jsonReader.read(tempFile);

            assertNotNull(reloadedDoc);
            assertEquals(2, reloadedDoc.getTotalBookmarkCount());

            List<BookmarkNode> children = reloadedDoc.getRoots().getBookmarkBar().getChildren();
            assertEquals(2, children.size());

            // Child 0: Working (unchanged)
            assertEquals("1", children.get(0).getId());
            assertEquals("https://example.com/work", children.get(0).getUrl());

            // Child 1: Updated URL
            assertEquals("2", children.get(1).getId());
            assertEquals("https://example.com/new", children.get(1).getUrl());

            // Verify original doc inside session remains untouched
            assertEquals(3, originalDoc.getTotalBookmarkCount());
            assertEquals("http://example.com/old", originalDoc.getRoots().getBookmarkBar().getChildren().get(1).getUrl());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @DisplayName("getExportBytes generates on-demand if generateExport was not called directly first")
    void testGetExportBytesOnDemand() {
        BookmarkDocument originalDoc = createSampleDocument();
        ScanSession session = new ScanSession("scan-demand", "bookmarks.json", originalDoc);
        session.setStatus(ScanStatus.COMPLETED);
        sessionManager.registerSession(session);

        byte[] bytes = exportService.getExportBytes("scan-demand");
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        // Calling again returns cached bytes
        byte[] bytes2 = exportService.getExportBytes("scan-demand");
        assertArrayEquals(bytes, bytes2);
    }

    @Test
    @DisplayName("Export generation is idempotent and preserves user choices across multiple calls")
    void testExportIdempotency() throws IOException {
        BookmarkDocument originalDoc = createSampleDocument();
        ScanSession session = new ScanSession("scan-idem", "bookmarks.json", originalDoc);
        session.setStatus(ScanStatus.COMPLETED);

        ComparisonItem item = new ComparisonItem(
                "item-toggle", "2", "Old URL", "http://example.com/old", "Bookmark Bar",
                "https://example.com/new", ValidationOutcome.REDIRECTED, RedirectType.SAME_WEBSITE,
                null, Priority.MEDIUM, ProposedAction.UPDATE_URL,
                "HTTP 301", true, true
        );
        session.setComparisonItems(List.of(item));
        sessionManager.registerSession(session);

        ExportResponse resp1 = exportService.generateExport("scan-idem");
        assertEquals(1, resp1.getChangesAppliedCount());

        // User turns off the toggle
        item.setEnabled(false);
        ExportResponse resp2 = exportService.generateExport("scan-idem");
        assertEquals(0, resp2.getChangesAppliedCount());

        // Document now retains the original URL
        byte[] bytes = exportService.getExportBytes("scan-idem");
        Path tempFile = Files.createTempFile("cleaned-idem-", ".json");
        try {
            Files.write(tempFile, bytes);
            BookmarkDocument doc = jsonReader.read(tempFile);
            assertEquals("http://example.com/old", doc.getRoots().getBookmarkBar().getChildren().get(1).getUrl());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
