package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.config.BookmarkValidationProperties;
import com.aman.bookmark_cleaner.exception.ProcessingException;
import com.aman.bookmark_cleaner.model.BookmarkAnalysisItem;
import com.aman.bookmark_cleaner.model.BookmarkAnalysisReport;
import com.aman.bookmark_cleaner.model.BookmarkDocument;
import com.aman.bookmark_cleaner.model.BookmarkNode;
import com.aman.bookmark_cleaner.model.DuplicateBookmarkItem;
import com.aman.bookmark_cleaner.model.DuplicateGroup;
import com.aman.bookmark_cleaner.model.DuplicateType;
import com.aman.bookmark_cleaner.model.ValidationResult;
import com.aman.bookmark_cleaner.validator.BookmarkValidator;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Processing engine that orchestrates URL validation, duplicate detection,
 * and parallel execution with host throttling and URL deduplication.
 */
@Service
public class BookmarkProcessingEngine implements AutoCloseable {

    private final BookmarkValidator bookmarkValidator;
    private final DuplicateDetectionService duplicateDetectionService;
    private final BookmarkValidationProperties properties;
    private final ExecutorService executor;

    @Autowired
    public BookmarkProcessingEngine(BookmarkValidator bookmarkValidator,
                                    DuplicateDetectionService duplicateDetectionService,
                                    BookmarkValidationProperties properties) {
        this.bookmarkValidator = bookmarkValidator;
        this.duplicateDetectionService = duplicateDetectionService;
        this.properties = properties != null ? properties : new BookmarkValidationProperties();

        int maxThreads = Math.max(1, this.properties.getMaxConcurrentRequests());
        this.executor = Executors.newFixedThreadPool(maxThreads, r -> {
            Thread t = new Thread(r, "bookmark-validator-worker");
            t.setDaemon(true);
            return t;
        });
    }

    public BookmarkProcessingEngine(BookmarkValidator bookmarkValidator,
                                    DuplicateDetectionService duplicateDetectionService,
                                    BookmarkValidationProperties properties,
                                    ExecutorService customExecutor) {
        this.bookmarkValidator = bookmarkValidator;
        this.duplicateDetectionService = duplicateDetectionService;
        this.properties = properties != null ? properties : new BookmarkValidationProperties();
        this.executor = customExecutor != null ? customExecutor : Executors.newFixedThreadPool(
                Math.max(1, this.properties.getMaxConcurrentRequests())
        );
    }

    /**
     * Executes the complete processing and analysis pipeline on a {@link BookmarkDocument}.
     * The input document is never mutated.
     */
    public BookmarkAnalysisReport analyze(BookmarkDocument document) {
        if (document == null || document.getRoots() == null) {
            return new BookmarkAnalysisReport(Collections.emptyList(), Collections.emptyList(), 0);
        }

        // 1. Traverse document to collect all bookmarks with their paths and identities
        List<DuplicateBookmarkItem> allBookmarks = new ArrayList<>();
        AtomicInteger generatedIdSeq = new AtomicInteger(1);
        Set<String> seenIds = new HashSet<>();

        for (Map.Entry<String, BookmarkNode> entry : document.getRoots().getAllRoots().entrySet()) {
            BookmarkNode root = entry.getValue();
            if (root != null) {
                String rootName = root.getName() != null && !root.getName().isBlank() ? root.getName() : entry.getKey();
                collectBookmarksRecursively(root, rootName, allBookmarks, generatedIdSeq, seenIds);
            }
        }

        if (allBookmarks.isEmpty()) {
            return new BookmarkAnalysisReport(Collections.emptyList(), Collections.emptyList(), 0);
        }

        // 2. Perform duplicate detection
        List<DuplicateGroup> duplicateGroups = duplicateDetectionService.findDuplicatesInList(allBookmarks);

        // Build lookup maps for duplicate assignments
        Map<String, String> itemToGroupId = new HashMap<>();
        Map<String, DuplicateType> itemToDuplicateType = new HashMap<>();
        Map<String, Boolean> itemToRetained = new HashMap<>();

        for (DuplicateGroup group : duplicateGroups) {
            String gid = group.getGroupId();
            DuplicateType dtype = group.getDuplicateType();

            // Retained bookmark
            if (group.getRetainedBookmark() != null) {
                String rId = group.getRetainedBookmark().getBookmarkId();
                itemToGroupId.put(rId, gid);
                itemToDuplicateType.put(rId, dtype);
                itemToRetained.put(rId, true);
            }

            // Removal candidates
            for (DuplicateBookmarkItem removal : group.getRemovalCandidates()) {
                String remId = removal.getBookmarkId();
                itemToGroupId.put(remId, gid);
                itemToDuplicateType.put(remId, dtype);
                itemToRetained.put(remId, false);
            }
        }

        // 3. Controlled parallel URL validation with per-host throttling and cache reuse
        ConcurrentHashMap<String, CompletableFuture<ValidationResult>> validationCache = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, Semaphore> hostSemaphores = new ConcurrentHashMap<>();

        int perHostLimit = Math.max(1, properties.getMaxConcurrentRequestsPerHost());

        // Schedule validation futures
        List<CompletableFuture<BookmarkAnalysisItem>> itemFutures = new ArrayList<>();

        for (DuplicateBookmarkItem bookmark : allBookmarks) {
            String rawUrl = bookmark.getOriginalUrl();
            String normalizedKey = duplicateDetectionService.normalizeForExactDuplicate(rawUrl);

            CompletableFuture<ValidationResult> valFuture = validationCache.computeIfAbsent(
                    normalizedKey.isEmpty() ? rawUrl : normalizedKey,
                    key -> CompletableFuture.supplyAsync(() -> validateWithHostThrottling(rawUrl, hostSemaphores, perHostLimit), executor)
            );

            CompletableFuture<BookmarkAnalysisItem> itemFuture = valFuture.thenApply(validationResult -> {
                String bId = bookmark.getBookmarkId();
                String dupGid = itemToGroupId.get(bId);
                DuplicateType dupType = itemToDuplicateType.get(bId);
                boolean retained = itemToRetained.getOrDefault(bId, true); // Unique items default to retained

                return new BookmarkAnalysisItem(
                        bId,
                        bookmark.getBookmarkName(),
                        rawUrl,
                        bookmark.getFolderPath(),
                        validationResult,
                        dupGid,
                        dupType,
                        retained
                );
            });

            itemFutures.add(itemFuture);
        }

        // Wait for all validations to complete
        List<BookmarkAnalysisItem> analysisItems = new ArrayList<>();
        try {
            CompletableFuture.allOf(itemFutures.toArray(new CompletableFuture[0])).join();
            for (CompletableFuture<BookmarkAnalysisItem> future : itemFutures) {
                analysisItems.add(future.join());
            }
        } catch (Exception e) {
            throw new ProcessingException("Error occurred during parallel bookmark analysis: " + e.getMessage(), e);
        }

        return new BookmarkAnalysisReport(analysisItems, duplicateGroups, validationCache.size());
    }

    private ValidationResult validateWithHostThrottling(String url, ConcurrentHashMap<String, Semaphore> hostSemaphores, int perHostLimit) {
        String host = extractHost(url);
        Semaphore hostSemaphore = hostSemaphores.computeIfAbsent(host, h -> new Semaphore(perHostLimit));

        boolean acquired = false;
        try {
            // Wait up to request timeout to acquire host permit so one slow host does not permanently lock
            long timeoutSec = properties.getRequestTimeout() != null ? properties.getRequestTimeout().toSeconds() : 10;
            acquired = hostSemaphore.tryAcquire(Math.max(1, timeoutSec), TimeUnit.SECONDS);

            if (!acquired) {
                return ValidationResult.unknown(url, -1, "Per-host concurrency rate limit exceeded for host: " + host, 0);
            }

            return bookmarkValidator.validate(url);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ValidationResult.unknown(url, -1, "Validation interrupted while waiting for host permit", 0);
        } catch (Exception e) {
            return ValidationResult.unknown(url, -1, "Validation execution failed: " + e.getMessage(), 0);
        } finally {
            if (acquired) {
                hostSemaphore.release();
            }
        }
    }

    private String extractHost(String url) {
        if (url == null || url.isBlank()) {
            return "unknown";
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host != null ? host.toLowerCase(Locale.ROOT) : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void collectBookmarksRecursively(BookmarkNode node, String currentPath, List<DuplicateBookmarkItem> results, AtomicInteger generatedIdSeq, Set<String> seenIds) {
        if (node == null) {
            return;
        }

        if (node.isUrl() && node.getUrl() != null && !node.getUrl().isBlank()) {
            String id = node.getId();
            if (id == null || id.isBlank() || !seenIds.add(id)) {
                id = (id != null && !id.isBlank() ? id : "gen") + "-" + generatedIdSeq.getAndIncrement();
                node.setId(id);
                seenIds.add(id);
            }
            String name = (node.getName() != null) ? node.getName() : "";
            results.add(new DuplicateBookmarkItem(id, name, node.getUrl(), currentPath));
        }

        if (node.getChildren() != null) {
            for (BookmarkNode child : node.getChildren()) {
                if (child != null) {
                    String childPath = currentPath;
                    if (child.isFolder()) {
                        String folderName = child.getName() != null && !child.getName().isBlank() ? child.getName() : child.getId();
                        childPath = currentPath.isEmpty() ? folderName : currentPath + " / " + folderName;
                    }
                    collectBookmarksRecursively(child, childPath, results, generatedIdSeq, seenIds);
                }
            }
        }
    }

    @Override
    @PreDestroy
    public void close() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
