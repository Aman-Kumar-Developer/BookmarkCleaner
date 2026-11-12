package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.dto.ComparisonItemDto;
import com.aman.bookmark_cleaner.dto.ComparisonResultsPageResponse;
import com.aman.bookmark_cleaner.dto.ScanStatusResponse;
import com.aman.bookmark_cleaner.dto.SummaryCountsDto;
import com.aman.bookmark_cleaner.dto.UploadResponse;
import com.aman.bookmark_cleaner.exception.BookmarkReaderException;
import com.aman.bookmark_cleaner.exception.InvalidBookmarkException;
import com.aman.bookmark_cleaner.model.BookmarkAnalysisReport;
import com.aman.bookmark_cleaner.model.BookmarkDocument;
import com.aman.bookmark_cleaner.model.ComparisonItem;
import com.aman.bookmark_cleaner.model.Priority;
import com.aman.bookmark_cleaner.model.ScanSession;
import com.aman.bookmark_cleaner.model.ScanStatus;
import com.aman.bookmark_cleaner.model.ValidationOutcome;
import com.aman.bookmark_cleaner.parser.BookmarkReader;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Orchestrator service coordinating file intake, scan execution, result filtering/paging,
 * and session state.
 */
@Service
public class ScanOrchestrationService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ScanOrchestrationService.class);

    private final BookmarkReader bookmarkReader;
    private final BookmarkProcessingEngine processingEngine;
    private final ComparisonService comparisonService;
    private final ScanSessionManager sessionManager;
    private final ExecutorService scanExecutor;

    @Autowired
    public ScanOrchestrationService(BookmarkReader bookmarkReader,
                                    BookmarkProcessingEngine processingEngine,
                                    ComparisonService comparisonService,
                                    ScanSessionManager sessionManager) {
        this(bookmarkReader, processingEngine, comparisonService, sessionManager, null);
    }

    public ScanOrchestrationService(BookmarkReader bookmarkReader,
                                    BookmarkProcessingEngine processingEngine,
                                    ComparisonService comparisonService,
                                    ScanSessionManager sessionManager,
                                    ExecutorService customExecutor) {
        this.bookmarkReader = bookmarkReader;
        this.processingEngine = processingEngine;
        this.comparisonService = comparisonService;
        this.sessionManager = sessionManager;
        this.scanExecutor = customExecutor != null ? customExecutor : Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "bookmark-scan-orchestrator");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Accepts and parses an uploaded bookmark file, initializing an in-memory scan session.
     */
    public UploadResponse uploadBookmarkFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidBookmarkException("Uploaded bookmark file is empty or missing");
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "Bookmarks.json";
        Path tempFile = null;
        BookmarkDocument document;

        try {
            tempFile = Files.createTempFile("bookmark-upload-", ".json");
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            document = bookmarkReader.read(tempFile);
        } catch (BookmarkReaderException e) {
            throw e;
        } catch (Exception e) {
            throw new BookmarkReaderException("Failed to process uploaded bookmark file: " + e.getMessage(), e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {
                }
            }
        }

        ScanSession session = sessionManager.createSession(originalFilename, document);
        return new UploadResponse(session.getScanId(), originalFilename, session.getTotalBookmarks(), session.getStatus().name());
    }

    /**
     * Executes the bookmark scan and analysis pipeline for a session.
     */
    public ScanStatusResponse startScan(String scanId) {
        ScanSession session = sessionManager.getSession(scanId);

        // If already RUNNING or COMPLETED, return current state
        if (session.getStatus() == ScanStatus.RUNNING || session.getStatus() == ScanStatus.COMPLETED) {
            return getScanStatus(scanId);
        }

        // Initialize state for running scan
        session.setStatus(ScanStatus.RUNNING);
        session.setProgressPercentage(0);
        session.setErrorMessage(null);

        int total = session.getTotalBookmarks();
        if (total == 0) {
            session.setProgressPercentage(100);
            session.setStatus(ScanStatus.COMPLETED);
            return getScanStatus(scanId);
        }

        scanExecutor.submit(() -> {
            try {
                // Execute Stage 2 processing engine with progress listener
                BookmarkAnalysisReport report = processingEngine.analyze(
                        session.getOriginalDocument(),
                        (item, completed, totalCount) -> session.recordBookmarkCompleted(item, completed, totalCount)
                );
                session.setAnalysisReport(report);

                // Build comparison items with priority and proposed actions (final aggregation stage)
                List<ComparisonItem> items = comparisonService.buildComparisonItems(report);
                session.setComparisonItems(items);
                session.setProcessedBookmarks(session.getTotalBookmarks());

                session.setProgressPercentage(100);
                session.setStatus(ScanStatus.COMPLETED);
            } catch (Exception e) {
                log.error("Scan analysis failed for scanId: {}", scanId, e);
                session.setStatus(ScanStatus.FAILED);
                session.setErrorMessage(e.getMessage() != null ? e.getMessage() : "Scan failed during analysis");
            }
        });

        return getScanStatus(scanId);
    }

    /**
     * Retrieves current scan status and progress.
     */
    public ScanStatusResponse getScanStatus(String scanId) {
        ScanSession session = sessionManager.getSession(scanId);
        BookmarkAnalysisReport report = session.getAnalysisReport();

        long working = report != null ? report.getCount(ValidationOutcome.WORKING) : session.getWorkingCount();
        long broken = report != null ? report.getCount(ValidationOutcome.BROKEN) : session.getBrokenCount();
        long redirected = report != null ? report.getCount(ValidationOutcome.REDIRECTED) : session.getRedirectedCount();
        long duplicate = report != null ? (report.getExactDuplicateCount() + report.getPotentialDuplicateCount()) : session.getDuplicateCount();
        long unknown = report != null ? report.getCount(ValidationOutcome.UNKNOWN) : session.getUnknownCount();

        int total = session.getTotalBookmarks();
        int completed = session.getCompletedBookmarks();
        int pending = session.getPendingBookmarks();
        int failed = session.getFailedBookmarks();

        return new ScanStatusResponse(
                session.getScanId(),
                session.getStatus().name(),
                session.getProgressPercentage(),
                total,
                session.getProcessedBookmarks(),
                completed,
                pending,
                failed,
                working,
                broken,
                redirected,
                duplicate,
                unknown,
                session.getErrorMessage(),
                session.isImportedFromBrowser(),
                session.getBrowserType() != null ? session.getBrowserType().getId() : null,
                session.getBrowserType() != null ? session.getBrowserType().getDisplayName() : null,
                session.getProfileId(),
                session.getProfileName()
        );
    }

    /**
     * Retrieves comparison results supporting filtering by priority, text search, and pagination.
     */
    public ComparisonResultsPageResponse getComparisonResults(String scanId, String priorityFilter, String search, int page, int size) {
        ScanSession session = sessionManager.getSession(scanId);
        List<ComparisonItem> allItems = session.getComparisonItems();

        // 1. Calculate overall summary counts before filtering
        SummaryCountsDto summary = buildSummaryCounts(session, allItems);

        // 2. Filter by priority
        List<ComparisonItem> filtered = allItems;
        if (priorityFilter != null && !priorityFilter.isBlank() && !"ALL".equalsIgnoreCase(priorityFilter.trim())) {
            try {
                Priority targetPriority = Priority.valueOf(priorityFilter.trim().toUpperCase(Locale.ROOT));
                filtered = filtered.stream()
                        .filter(item -> item.getPriority() == targetPriority)
                        .collect(Collectors.toList());
            } catch (IllegalArgumentException ignored) {
                // If invalid priority string supplied, return unfiltered
            }
        }

        // 3. Filter by search term on name or URL
        if (search != null && !search.isBlank()) {
            String query = search.trim().toLowerCase(Locale.ROOT);
            filtered = filtered.stream()
                    .filter(item -> (item.getBookmarkName() != null && item.getBookmarkName().toLowerCase(Locale.ROOT).contains(query)) ||
                            (item.getOriginalUrl() != null && item.getOriginalUrl().toLowerCase(Locale.ROOT).contains(query)) ||
                            (item.getProposedUrl() != null && item.getProposedUrl().toLowerCase(Locale.ROOT).contains(query)))
                    .collect(Collectors.toList());
        }

        // 4. Pagination
        int totalItems = filtered.size();
        int pageSize = Math.max(1, size);
        int pageNumber = Math.max(0, page);
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);

        int fromIndex = Math.min(pageNumber * pageSize, totalItems);
        int toIndex = Math.min(fromIndex + pageSize, totalItems);

        List<ComparisonItemDto> pagedDtos = filtered.subList(fromIndex, toIndex).stream()
                .map(ComparisonItemDto::new)
                .collect(Collectors.toList());

        return new ComparisonResultsPageResponse(scanId, pageNumber, pageSize, totalItems, totalPages, pagedDtos, summary);
    }

    private SummaryCountsDto buildSummaryCounts(ScanSession session, List<ComparisonItem> items) {
        int total = items.size();
        long high = items.stream().filter(i -> i.getPriority() == Priority.HIGH).count();
        long med = items.stream().filter(i -> i.getPriority() == Priority.MEDIUM).count();
        long low = items.stream().filter(i -> i.getPriority() == Priority.LOW).count();
        long applied = items.stream().filter(ComparisonItem::isEnabled).count();

        BookmarkAnalysisReport report = session.getAnalysisReport();
        long working = report != null ? report.getCount(ValidationOutcome.WORKING) : 0;
        long broken = report != null ? report.getCount(ValidationOutcome.BROKEN) : 0;
        long redirected = report != null ? report.getCount(ValidationOutcome.REDIRECTED) : 0;
        long duplicate = report != null ? (report.getExactDuplicateCount() + report.getPotentialDuplicateCount()) : 0;
        long unknown = report != null ? report.getCount(ValidationOutcome.UNKNOWN) : 0;

        return new SummaryCountsDto(total, high, med, low, working, broken, redirected, duplicate, unknown, applied);
    }

    @Override
    @PreDestroy
    public void close() {
        if (scanExecutor != null && !scanExecutor.isShutdown()) {
            scanExecutor.shutdown();
            try {
                if (!scanExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    scanExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                scanExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
