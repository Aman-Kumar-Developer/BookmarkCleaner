package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.config.BookmarkValidationProperties;
import com.aman.bookmark_cleaner.dto.ScanStatusResponse;
import com.aman.bookmark_cleaner.exception.ProcessingException;
import com.aman.bookmark_cleaner.model.BookmarkAnalysisItem;
import com.aman.bookmark_cleaner.model.BookmarkAnalysisReport;
import com.aman.bookmark_cleaner.model.BookmarkDocument;
import com.aman.bookmark_cleaner.model.BookmarkNode;
import com.aman.bookmark_cleaner.model.BookmarkRoots;
import com.aman.bookmark_cleaner.model.ScanSession;
import com.aman.bookmark_cleaner.model.ScanStatus;
import com.aman.bookmark_cleaner.model.ValidationOutcome;
import com.aman.bookmark_cleaner.model.ValidationResult;
import com.aman.bookmark_cleaner.parser.BookmarkReader;
import com.aman.bookmark_cleaner.validator.BookmarkValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ScanProgressTest {

    private BookmarkDocument createDocument(int bookmarkCount) {
        BookmarkRoots roots = new BookmarkRoots();
        BookmarkNode bar = BookmarkNode.createFolder("1", "Bookmarks bar");

        for (int i = 1; i <= bookmarkCount; i++) {
            bar.addChild(BookmarkNode.createUrl("b-" + i, "Bookmark " + i, "https://example.com/item/" + i));
        }
        roots.setBookmarkBar(bar);
        return new BookmarkDocument(roots);
    }

    @Test
    @DisplayName("Progress starts at 0% and increases incrementally as bookmarks complete")
    void testProgressStartsAtZeroAndIncreasesIncrementally() throws Exception {
        int total = 10;
        BookmarkRoots roots = new BookmarkRoots();
        BookmarkNode bar = BookmarkNode.createFolder("1", "Bookmarks bar");

        for (int i = 1; i <= total; i++) {
            bar.addChild(BookmarkNode.createUrl("b-" + i, "Bookmark " + i, "https://site" + i + ".example.com/item"));
        }
        roots.setBookmarkBar(bar);
        BookmarkDocument doc = new BookmarkDocument(roots);

        // Latches to control completion order of bookmarks
        CountDownLatch pauseRemainingBatch = new CountDownLatch(1);

        BookmarkValidator controlledValidator = url -> {
            if (url.contains("site1.") || url.contains("site2.") || url.contains("site3.")) {
                // Return fast
                return ValidationResult.working(url, 200, 5);
            }
            try {
                pauseRemainingBatch.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            return ValidationResult.working(url, 200, 5);
        };

        BookmarkValidationProperties props = new BookmarkValidationProperties();
        props.setMaxConcurrentRequests(15);
        props.setMaxConcurrentRequestsPerHost(10);
        DuplicateDetectionService dupService = new DuplicateDetectionService();

        List<Integer> observedCompleted = new CopyOnWriteArrayList<>();
        List<Integer> observedPercentages = new CopyOnWriteArrayList<>();

        try (BookmarkProcessingEngine engine = new BookmarkProcessingEngine(controlledValidator, dupService, props)) {
            ScanSession session = new ScanSession("progress-inc-test", "test.json", doc);
            session.setStatus(ScanStatus.RUNNING);

            assertEquals(0, session.getProgressPercentage(), "Progress must start at 0%");
            assertEquals(0, session.getCompletedBookmarks());
            assertEquals(10, session.getPendingBookmarks());

            BookmarkProgressListener listener = (item, completed, totalCount) -> {
                session.recordBookmarkCompleted(item, completed, totalCount);
                observedCompleted.add(completed);
                observedPercentages.add(session.getProgressPercentage());
            };

            ExecutorService testExec = Executors.newSingleThreadExecutor();
            try {
                testExec.submit(() -> engine.analyze(doc, listener));

                // Wait until at least 3 items have completed
                long deadline = System.currentTimeMillis() + 3000;
                while (session.getCompletedBookmarks() < 3 && System.currentTimeMillis() < deadline) {
                    Thread.sleep(10);
                }

                assertTrue(session.getCompletedBookmarks() >= 3, "At least 3 bookmarks should complete initially");
                assertTrue(session.getProgressPercentage() >= 30, "Progress should reflect completed bookmarks");
                assertTrue(session.getPendingBookmarks() <= 7, "Pending bookmarks should decrease");

                // Release the remaining batch
                pauseRemainingBatch.countDown();

                // Wait for all to finish
                deadline = System.currentTimeMillis() + 3000;
                while (session.getCompletedBookmarks() < total && System.currentTimeMillis() < deadline) {
                    Thread.sleep(10);
                }

                session.setStatus(ScanStatus.COMPLETED);

                assertEquals(total, session.getCompletedBookmarks());
                assertEquals(0, session.getPendingBookmarks());
                assertEquals(100, session.getProgressPercentage(), "Progress reaches 100% on completion");

                // Verify all increments 1 through total were produced exactly once
                assertEquals(total, observedCompleted.size());
                for (int i = 1; i <= total; i++) {
                    assertTrue(observedCompleted.contains(i), "Must record completion for index " + i);
                }
            } finally {
                pauseRemainingBatch.countDown();
                testExec.shutdownNow();
            }
        }
    }

    @Test
    @DisplayName("Progress calculation matches specified percentage table values")
    void testIllustrativeProgressPercentagesMatchSpecTable() {
        ScanSession session = new ScanSession("table-test", "test.json", createDocument(110));
        session.setStatus(ScanStatus.RUNNING);

        // Case 0 of 110: 0%
        session.recordBookmarkCompleted(null, 0, 110);
        assertEquals(0, session.getProgressPercentage());

        // Case 11 of 110: 10%
        session.recordBookmarkCompleted(null, 11, 110);
        assertEquals(10, session.getProgressPercentage());

        // Case 27 of 110: 25% (27/110 * 100 = 24.545% -> 25%)
        session.recordBookmarkCompleted(null, 27, 110);
        assertEquals(25, session.getProgressPercentage());

        // Case 55 of 110: 50%
        session.recordBookmarkCompleted(null, 55, 110);
        assertEquals(50, session.getProgressPercentage());

        // Case 82 of 110: 75% (82/110 * 100 = 74.545% -> 75%)
        session.recordBookmarkCompleted(null, 82, 110);
        assertEquals(75, session.getProgressPercentage());

        // Case 99 of 110: 90%
        session.recordBookmarkCompleted(null, 99, 110);
        assertEquals(90, session.getProgressPercentage());

        // Case 110 of 110 while still RUNNING: capped at 99%
        session.recordBookmarkCompleted(null, 110, 110);
        assertEquals(99, session.getProgressPercentage(), "Must not report 100% while scan is still RUNNING");

        // When scan completes: 100%
        session.setStatus(ScanStatus.COMPLETED);
        assertEquals(100, session.getProgressPercentage(), "Reports 100% when scan reaches COMPLETED");
    }

    @Test
    @DisplayName("Failed, broken, redirected, and UNKNOWN validation outcomes are counted as completed attempts")
    void testFailedAndUnknownOutcomesCountedAsCompletedAttempts() {
        BookmarkRoots roots = new BookmarkRoots();
        BookmarkNode bar = BookmarkNode.createFolder("1", "Bar");
        bar.addChild(BookmarkNode.createUrl("1", "Working", "https://example.com/working"));
        bar.addChild(BookmarkNode.createUrl("2", "Broken", "https://example.com/broken"));
        bar.addChild(BookmarkNode.createUrl("3", "Redirected", "https://example.com/redirected"));
        bar.addChild(BookmarkNode.createUrl("4", "Exception", "https://example.com/exception"));
        roots.setBookmarkBar(bar);
        BookmarkDocument doc = new BookmarkDocument(roots);

        BookmarkValidator mixedValidator = url -> {
            if (url.contains("working")) {
                return ValidationResult.working(url, 200, 5);
            } else if (url.contains("broken")) {
                return ValidationResult.broken(url, 404, "Not Found", 5);
            } else if (url.contains("redirected")) {
                return ValidationResult.redirected(url, "https://example.com/new", 301, null, 5);
            } else {
                throw new RuntimeException("Simulated network timeout");
            }
        };

        ScanSession session = new ScanSession("mixed-test", "mixed.json", doc);
        session.setStatus(ScanStatus.RUNNING);

        try (BookmarkProcessingEngine engine = new BookmarkProcessingEngine(mixedValidator, new DuplicateDetectionService(), new BookmarkValidationProperties())) {
            engine.analyze(doc, session::recordBookmarkCompleted);
            session.setStatus(ScanStatus.COMPLETED);

            // All 4 attempts counted as completed
            assertEquals(4, session.getCompletedBookmarks());
            assertEquals(4, session.getTotalBookmarks());
            assertEquals(0, session.getPendingBookmarks());
            assertEquals(100, session.getProgressPercentage());

            // Check counters
            assertEquals(1, session.getWorkingCount());
            assertEquals(1, session.getBrokenCount());
            assertEquals(1, session.getRedirectedCount());
            assertEquals(1, session.getUnknownCount());
            // failedCount includes broken + unknown
            assertEquals(2, session.getFailedBookmarks());
        }
    }

    @Test
    @DisplayName("Concurrent completion updates do not lose increments or corrupt counters")
    void testConcurrentCompletionUpdatesDoNotLoseIncrements() {
        int count = 60;
        BookmarkDocument doc = createDocument(count);

        BookmarkValidator fastValidator = url -> ValidationResult.working(url, 200, 1);
        BookmarkValidationProperties props = new BookmarkValidationProperties();
        props.setMaxConcurrentRequests(20);

        ScanSession session = new ScanSession("concurrent-test", "concurrent.json", doc);
        session.setStatus(ScanStatus.RUNNING);

        try (BookmarkProcessingEngine engine = new BookmarkProcessingEngine(fastValidator, new DuplicateDetectionService(), props)) {
            engine.analyze(doc, session::recordBookmarkCompleted);
            session.setStatus(ScanStatus.COMPLETED);

            assertEquals(count, session.getCompletedBookmarks(), "Every concurrent completion must be recorded");
            assertEquals(count, session.getTotalBookmarks());
            assertEquals(0, session.getPendingBookmarks());
            assertEquals(100, session.getProgressPercentage());
            assertEquals(count, session.getWorkingCount());
            assertEquals(0, session.getFailedBookmarks());
        }
    }

    @Test
    @DisplayName("No bookmark is counted twice even with shared normalized URLs")
    void testNoBookmarkIsCountedTwiceWithSharedUrls() {
        BookmarkRoots roots = new BookmarkRoots();
        BookmarkNode bar = BookmarkNode.createFolder("1", "Bar");
        // 5 bookmarks sharing identical URL
        for (int i = 1; i <= 5; i++) {
            bar.addChild(BookmarkNode.createUrl("id-" + i, "BM " + i, "https://identical.example.com"));
        }
        roots.setBookmarkBar(bar);
        BookmarkDocument doc = new BookmarkDocument(roots);

        AtomicInteger networkCalls = new AtomicInteger(0);
        BookmarkValidator validator = url -> {
            networkCalls.incrementAndGet();
            return ValidationResult.working(url, 200, 2);
        };

        ScanSession session = new ScanSession("dedup-test", "test.json", doc);
        session.setStatus(ScanStatus.RUNNING);

        try (BookmarkProcessingEngine engine = new BookmarkProcessingEngine(validator, new DuplicateDetectionService(), new BookmarkValidationProperties())) {
            engine.analyze(doc, session::recordBookmarkCompleted);
            session.setStatus(ScanStatus.COMPLETED);

            assertEquals(1, networkCalls.get(), "Network validation must be deduplicated to 1 call");
            assertEquals(5, session.getCompletedBookmarks(), "All 5 bookmarks must be marked completed");
            assertEquals(5, session.getTotalBookmarks());
            assertEquals(100, session.getProgressPercentage());
        }
    }

    @Test
    @DisplayName("Zero-bookmark scans handle division-by-zero safely")
    void testZeroBookmarkScanHandledSafely() {
        BookmarkDocument emptyDoc = new BookmarkDocument(new BookmarkRoots());
        ScanSession session = new ScanSession("empty-test", "empty.json", emptyDoc);

        assertEquals(0, session.getTotalBookmarks());
        assertEquals(0, session.getCompletedBookmarks());
        assertEquals(0, session.getPendingBookmarks());

        // Recording with total 0 should not throw ArithmeticException
        assertDoesNotThrow(() -> session.recordBookmarkCompleted(null, 0, 0));
        assertEquals(100, session.getProgressPercentage());

        // Start scan through ScanOrchestrationService
        BookmarkReader dummyReader = path -> emptyDoc;
        BookmarkProcessingEngine dummyEngine = new BookmarkProcessingEngine(url -> ValidationResult.working(url, 200, 1), new DuplicateDetectionService(), new BookmarkValidationProperties());
        ComparisonService dummyComp = new ComparisonService();
        ScanSessionManager manager = new ScanSessionManager();
        manager.registerSession(session);

        try (ScanOrchestrationService orchestration = new ScanOrchestrationService(dummyReader, dummyEngine, dummyComp, manager)) {
            ScanStatusResponse response = orchestration.startScan("empty-test");
            assertEquals("COMPLETED", response.getStatus());
            assertEquals(100, response.getProgressPercentage());
            assertEquals(0, response.getTotalBookmarks());
            assertEquals(0, response.getCompletedBookmarks());
            assertEquals(0, response.getPendingBookmarks());
        }
    }

    @Test
    @DisplayName("Scan status remains RUNNING until all processing and aggregation complete")
    void testScanStatusRemainsRunningUntilAggregationComplete() throws Exception {
        BookmarkDocument doc = createDocument(4);
        CountDownLatch holdValidation = new CountDownLatch(1);

        BookmarkValidator slowValidator = url -> {
            try {
                holdValidation.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            return ValidationResult.working(url, 200, 5);
        };

        BookmarkValidationProperties props = new BookmarkValidationProperties();
        props.setMaxConcurrentRequests(4);
        BookmarkProcessingEngine engine = new BookmarkProcessingEngine(slowValidator, new DuplicateDetectionService(), props);
        ComparisonService comparisonService = new ComparisonService();
        ScanSessionManager manager = new ScanSessionManager();

        ScanSession session = manager.createSession("bookmarks.json", doc);
        String scanId = session.getScanId();

        try (ScanOrchestrationService service = new ScanOrchestrationService(null, engine, comparisonService, manager)) {
            ScanStatusResponse initialResponse = service.startScan(scanId);

            // Must report RUNNING initially
            assertEquals("RUNNING", initialResponse.getStatus());
            assertEquals(0, initialResponse.getProgressPercentage());
            assertEquals(4, initialResponse.getTotalBookmarks());
            assertEquals(0, initialResponse.getCompletedBookmarks());
            assertEquals(4, initialResponse.getPendingBookmarks());

            // Poll while validator is held
            ScanStatusResponse intermediateResponse = service.getScanStatus(scanId);
            assertEquals("RUNNING", intermediateResponse.getStatus(), "Scan status must remain RUNNING while work is incomplete");

            // Release the validator
            holdValidation.countDown();

            // Wait for completion
            long deadline = System.currentTimeMillis() + 4000;
            while (service.getScanStatus(scanId).getStatus().equals("RUNNING") && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }

            ScanStatusResponse finalResponse = service.getScanStatus(scanId);
            assertEquals("COMPLETED", finalResponse.getStatus());
            assertEquals(100, finalResponse.getProgressPercentage());
            assertEquals(4, finalResponse.getCompletedBookmarks());
            assertEquals(0, finalResponse.getPendingBookmarks());
        } finally {
            holdValidation.countDown();
            engine.close();
        }
    }

    @Test
    @DisplayName("Failed scan reports FAILED and does not incorrectly report COMPLETED")
    void testFailedScanReportsFailureNotCompletion() throws Exception {
        BookmarkDocument doc = createDocument(2);

        // Subclass engine that throws unhandled ProcessingException during analysis
        BookmarkProcessingEngine brokenEngine = new BookmarkProcessingEngine(
                url -> ValidationResult.working(url, 200, 1),
                new DuplicateDetectionService(),
                new BookmarkValidationProperties()
        ) {
            @Override
            public BookmarkAnalysisReport analyze(BookmarkDocument document, BookmarkProgressListener listener) {
                throw new ProcessingException("Simulated unrecoverable analysis failure");
            }
        };

        ComparisonService compService = new ComparisonService();
        ScanSessionManager manager = new ScanSessionManager();
        ScanSession session = manager.createSession("broken.json", doc);
        String scanId = session.getScanId();

        try (ScanOrchestrationService service = new ScanOrchestrationService(null, brokenEngine, compService, manager)) {
            service.startScan(scanId);

            long deadline = System.currentTimeMillis() + 3000;
            while (service.getScanStatus(scanId).getStatus().equals("RUNNING") && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }

            ScanStatusResponse response = service.getScanStatus(scanId);
            assertEquals("FAILED", response.getStatus());
            assertNotNull(response.getErrorMessage());
            assertNotEquals(100, response.getProgressPercentage());
        } finally {
            brokenEngine.close();
        }
    }
}
