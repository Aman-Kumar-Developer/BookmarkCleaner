package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.config.BookmarkValidationProperties;
import com.aman.bookmark_cleaner.model.BookmarkAnalysisItem;
import com.aman.bookmark_cleaner.model.BookmarkAnalysisReport;
import com.aman.bookmark_cleaner.model.BookmarkDocument;
import com.aman.bookmark_cleaner.model.BookmarkNode;
import com.aman.bookmark_cleaner.model.BookmarkRoots;
import com.aman.bookmark_cleaner.model.ValidationOutcome;
import com.aman.bookmark_cleaner.model.ValidationResult;
import com.aman.bookmark_cleaner.validator.BookmarkValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BookmarkProcessingEngineTest {

    @Test
    @DisplayName("Parallel processing executes concurrently and validates all bookmarks")
    void testParallelProcessingValidatesAllBookmarks() {
        BookmarkRoots roots = new BookmarkRoots();
        BookmarkNode bar = BookmarkNode.createFolder("1", "Bookmarks bar");

        bar.addChild(BookmarkNode.createUrl("101", "Site 1", "https://site1.example.com"));
        bar.addChild(BookmarkNode.createUrl("102", "Site 2", "https://site2.example.com"));
        bar.addChild(BookmarkNode.createUrl("103", "Site 3", "https://site3.example.com"));
        roots.setBookmarkBar(bar);

        BookmarkDocument doc = new BookmarkDocument(roots);

        BookmarkValidator mockValidator = url -> ValidationResult.working(url, 200, 10);
        BookmarkValidationProperties props = new BookmarkValidationProperties();
        props.setMaxConcurrentRequests(5);

        try (BookmarkProcessingEngine engine = new BookmarkProcessingEngine(mockValidator, new DuplicateDetectionService(), props)) {
            BookmarkAnalysisReport report = engine.analyze(doc);

            assertEquals(3, report.getTotalBookmarksAnalyzed());
            assertEquals(3, report.getCount(ValidationOutcome.WORKING));
            assertEquals(0, report.getCount(ValidationOutcome.BROKEN));
        }
    }

    @Test
    @DisplayName("Repeated normalized URLs reuse validation results without duplicate network calls")
    void testRepeatedUrlsReuseValidationResult() {
        BookmarkRoots roots = new BookmarkRoots();
        BookmarkNode bar = BookmarkNode.createFolder("1", "Bookmarks bar");

        // 3 bookmarks sharing identical URL across different folders
        bar.addChild(BookmarkNode.createUrl("101", "GitHub", "https://github.com"));
        bar.addChild(BookmarkNode.createUrl("102", "GitHub Tools", "https://github.com"));
        bar.addChild(BookmarkNode.createUrl("103", "GitHub Dev", "https://github.com"));
        roots.setBookmarkBar(bar);

        BookmarkDocument doc = new BookmarkDocument(roots);

        AtomicInteger networkCallCount = new AtomicInteger(0);
        BookmarkValidator countingValidator = url -> {
            networkCallCount.incrementAndGet();
            return ValidationResult.working(url, 200, 5);
        };

        try (BookmarkProcessingEngine engine = new BookmarkProcessingEngine(countingValidator, new DuplicateDetectionService(), new BookmarkValidationProperties())) {
            BookmarkAnalysisReport report = engine.analyze(doc);

            // Validated all 3 items
            assertEquals(3, report.getTotalBookmarksAnalyzed());
            // But network call was executed exactly ONCE!
            assertEquals(1, networkCallCount.get(), "Network validation must be executed once per unique normalized URL");
            // Exactly 1 unique URL
            assertEquals(1, report.getUniqueUrlsCount());
            // Duplicate group exists with 1 retained and 2 removal candidates
            assertEquals(1, report.getDuplicateGroups().size());
            assertEquals(2, report.getExactDuplicateCount());
        }
    }

    @Test
    @DisplayName("One failing or slow URL does not fail the scan or corrupt other bookmark results")
    void testFailedUrlDoesNotAbortScan() {
        BookmarkRoots roots = new BookmarkRoots();
        BookmarkNode bar = BookmarkNode.createFolder("1", "Bookmarks bar");

        bar.addChild(BookmarkNode.createUrl("101", "Good Site", "https://good.example.com"));
        bar.addChild(BookmarkNode.createUrl("102", "Failing Site", "https://failing.example.com"));
        bar.addChild(BookmarkNode.createUrl("103", "Broken Site", "https://broken.example.com"));
        roots.setBookmarkBar(bar);

        BookmarkDocument doc = new BookmarkDocument(roots);

        BookmarkValidator mockValidator = url -> {
            if (url.contains("failing")) {
                throw new RuntimeException("Simulated network exception");
            }
            if (url.contains("broken")) {
                return ValidationResult.broken(url, 404, "HTTP 404 Not Found", 10);
            }
            return ValidationResult.working(url, 200, 10);
        };

        try (BookmarkProcessingEngine engine = new BookmarkProcessingEngine(mockValidator, new DuplicateDetectionService(), new BookmarkValidationProperties())) {
            BookmarkAnalysisReport report = engine.analyze(doc);

            assertEquals(3, report.getTotalBookmarksAnalyzed());
            assertEquals(1, report.getCount(ValidationOutcome.WORKING));
            assertEquals(1, report.getCount(ValidationOutcome.BROKEN));
            assertEquals(1, report.getCount(ValidationOutcome.UNKNOWN)); // The failing site safely mapped to UNKNOWN

            BookmarkAnalysisItem goodItem = report.getItems().stream().filter(i -> i.getBookmarkId().equals("101")).findFirst().orElseThrow();
            assertEquals(ValidationOutcome.WORKING, goodItem.getValidationResult().getOutcome());

            BookmarkAnalysisItem brokenItem = report.getItems().stream().filter(i -> i.getBookmarkId().equals("103")).findFirst().orElseThrow();
            assertEquals(ValidationOutcome.BROKEN, brokenItem.getValidationResult().getOutcome());
        }
    }

    @Test
    @DisplayName("Per-host concurrency throttling is enforced")
    void testPerHostConcurrencyThrottling() {
        BookmarkRoots roots = new BookmarkRoots();
        BookmarkNode bar = BookmarkNode.createFolder("1", "Bookmarks bar");

        // 6 distinct URLs on the same host
        for (int i = 1; i <= 6; i++) {
            bar.addChild(BookmarkNode.createUrl("id-" + i, "Page " + i, "https://same-host.example.com/page/" + i));
        }
        roots.setBookmarkBar(bar);
        BookmarkDocument doc = new BookmarkDocument(roots);

        AtomicInteger currentConcurrency = new AtomicInteger(0);
        AtomicInteger maxObservedConcurrency = new AtomicInteger(0);

        BookmarkValidator slowValidator = url -> {
            int current = currentConcurrency.incrementAndGet();
            maxObservedConcurrency.accumulateAndGet(current, Math::max);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                currentConcurrency.decrementAndGet();
            }
            return ValidationResult.working(url, 200, 50);
        };

        BookmarkValidationProperties props = new BookmarkValidationProperties();
        props.setMaxConcurrentRequests(10);
        props.setMaxConcurrentRequestsPerHost(2); // At most 2 concurrent requests on same-host.example.com

        try (BookmarkProcessingEngine engine = new BookmarkProcessingEngine(slowValidator, new DuplicateDetectionService(), props)) {
            BookmarkAnalysisReport report = engine.analyze(doc);
            assertEquals(6, report.getTotalBookmarksAnalyzed());
            assertTrue(maxObservedConcurrency.get() <= 2,
                    "Observed concurrency (" + maxObservedConcurrency.get() + ") must not exceed per-host limit of 2");
        }
    }

    @Test
    @DisplayName("Empty bookmark document produces empty report without crashing")
    void testEmptyDocumentHandledSafely() {
        BookmarkDocument emptyDoc = new BookmarkDocument(new BookmarkRoots());
        try (BookmarkProcessingEngine engine = new BookmarkProcessingEngine(url -> ValidationResult.working(url, 200, 1), new DuplicateDetectionService(), new BookmarkValidationProperties())) {
            BookmarkAnalysisReport report = engine.analyze(emptyDoc);
            assertEquals(0, report.getTotalBookmarksAnalyzed());
            assertEquals(0, report.getUniqueUrlsCount());
            assertTrue(report.getItems().isEmpty());
            assertTrue(report.getDuplicateGroups().isEmpty());
        }
    }

    @Test
    @DisplayName("Duplicate or null bookmark node IDs are sanitized with unique scan-local IDs")
    void testDuplicateAndNullBookmarkIdsAreDisambiguated() {
        BookmarkRoots roots = new BookmarkRoots();
        BookmarkNode bar = BookmarkNode.createFolder("1", "Bookmarks bar");

        // Two nodes with duplicate ID "100", one node with null ID
        BookmarkNode node1 = BookmarkNode.createUrl("100", "Site 1", "https://site1.example.com");
        BookmarkNode node2 = BookmarkNode.createUrl("100", "Site 2", "https://site2.example.com");
        BookmarkNode node3 = BookmarkNode.createUrl(null, "Site 3", "https://site3.example.com");

        bar.addChild(node1);
        bar.addChild(node2);
        bar.addChild(node3);
        roots.setBookmarkBar(bar);

        BookmarkDocument doc = new BookmarkDocument(roots);

        try (BookmarkProcessingEngine engine = new BookmarkProcessingEngine(url -> ValidationResult.working(url, 200, 1), new DuplicateDetectionService(), new BookmarkValidationProperties())) {
            BookmarkAnalysisReport report = engine.analyze(doc);

            assertEquals(3, report.getItems().size());
            long distinctIds = report.getItems().stream().map(BookmarkAnalysisItem::getBookmarkId).distinct().count();
            assertEquals(3, distinctIds, "All items must receive distinct IDs");

            // Also verify that the nodes in doc were updated with the same unique IDs
            assertEquals(report.getItems().get(0).getBookmarkId(), node1.getId());
            assertEquals(report.getItems().get(1).getBookmarkId(), node2.getId());
            assertEquals(report.getItems().get(2).getBookmarkId(), node3.getId());
        }
    }
}
