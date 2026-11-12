package com.aman.bookmark_cleaner.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory scan session retaining all state for an uploaded bookmark file,
 * its analysis, user decisions, and generated export.
 */
public class ScanSession {

    private final String scanId;
    private final String originalFilename;
    private final BookmarkDocument originalDocument;
    private final long createdAt;

    private volatile ScanStatus status;
    private volatile int progressPercentage;
    private volatile int totalBookmarks;
    private volatile int processedBookmarks;
    private volatile String errorMessage;

    private final AtomicInteger completedCount = new AtomicInteger(0);
    private final AtomicInteger workingCount = new AtomicInteger(0);
    private final AtomicInteger brokenCount = new AtomicInteger(0);
    private final AtomicInteger redirectedCount = new AtomicInteger(0);
    private final AtomicInteger duplicateCount = new AtomicInteger(0);
    private final AtomicInteger unknownCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);

    private volatile BookmarkAnalysisReport analysisReport;
    private volatile List<ComparisonItem> comparisonItems = Collections.emptyList();
    private final Map<String, ComparisonItem> itemMap = new ConcurrentHashMap<>();
    private final Map<String, ComparisonItem> bookmarkIdToItemMap = new ConcurrentHashMap<>();

    private volatile byte[] exportJsonBytes;
    private volatile String exportFilename = "bookmarks-cleaned.json";
    private volatile int changesAppliedCount;

    // Browser profile origin tracking
    private volatile boolean importedFromBrowser = false;
    private volatile BrowserType browserType;
    private volatile String profileId;
    private volatile String profileDirectoryName;
    private volatile String profileDisplayName;
    private volatile java.nio.file.Path originalBookmarkPath;
    private volatile String originalFileHash;

    public ScanSession(String scanId, String originalFilename, BookmarkDocument originalDocument) {
        this.scanId = scanId;
        this.originalFilename = originalFilename != null ? originalFilename : "Bookmarks";
        this.originalDocument = originalDocument;
        this.createdAt = System.currentTimeMillis();
        this.status = ScanStatus.PENDING;
        this.totalBookmarks = originalDocument != null ? originalDocument.getTotalBookmarkCount() : 0;
        this.progressPercentage = 0;
    }

    public String getScanId() {
        return scanId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public BookmarkDocument getOriginalDocument() {
        return originalDocument;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public ScanStatus getStatus() {
        return status;
    }

    public void setStatus(ScanStatus status) {
        this.status = status;
        if (status == ScanStatus.COMPLETED) {
            this.progressPercentage = 100;
            if (this.totalBookmarks > 0) {
                this.completedCount.set(this.totalBookmarks);
                this.processedBookmarks = this.totalBookmarks;
            }
        }
    }

    public int getProgressPercentage() {
        return progressPercentage;
    }

    public void setProgressPercentage(int progressPercentage) {
        this.progressPercentage = progressPercentage;
    }

    public int getTotalBookmarks() {
        return totalBookmarks;
    }

    public void setTotalBookmarks(int totalBookmarks) {
        this.totalBookmarks = totalBookmarks;
    }

    public int getProcessedBookmarks() {
        return processedBookmarks;
    }

    public void setProcessedBookmarks(int processedBookmarks) {
        this.processedBookmarks = processedBookmarks;
        this.completedCount.accumulateAndGet(processedBookmarks, Math::max);
    }

    public int getCompletedBookmarks() {
        return completedCount.get();
    }

    public int getPendingBookmarks() {
        return Math.max(0, totalBookmarks - completedCount.get());
    }

    public int getFailedBookmarks() {
        return failedCount.get();
    }

    public long getWorkingCount() {
        return workingCount.get();
    }

    public long getBrokenCount() {
        return brokenCount.get();
    }

    public long getRedirectedCount() {
        return redirectedCount.get();
    }

    public long getDuplicateCount() {
        return duplicateCount.get();
    }

    public void setDuplicateCount(int count) {
        this.duplicateCount.set(count);
    }

    public long getUnknownCount() {
        return unknownCount.get();
    }

    /**
     * Atomically records a bookmark completion and updates progress percentage and outcome counters.
     *
     * @param item the completed bookmark item
     * @param completed the new total completed bookmark count
     * @param total the total bookmarks scheduled for analysis
     */
    public void recordBookmarkCompleted(BookmarkAnalysisItem item, int completed, int total) {
        this.totalBookmarks = total;
        int currentCompleted = this.completedCount.accumulateAndGet(completed, Math::max);
        this.processedBookmarks = currentCompleted;

        if (item != null && item.getValidationResult() != null) {
            ValidationOutcome outcome = item.getValidationResult().getOutcome();
            if (outcome == ValidationOutcome.WORKING) {
                workingCount.incrementAndGet();
            } else if (outcome == ValidationOutcome.BROKEN) {
                brokenCount.incrementAndGet();
                failedCount.incrementAndGet();
            } else if (outcome == ValidationOutcome.REDIRECTED) {
                redirectedCount.incrementAndGet();
            } else if (outcome == ValidationOutcome.UNKNOWN) {
                unknownCount.incrementAndGet();
                failedCount.incrementAndGet();
            }
        }

        if (total <= 0) {
            this.progressPercentage = 100;
        } else {
            int calculated = (int) Math.round(((double) currentCompleted / (double) total) * 100.0);
            if (this.status == ScanStatus.RUNNING) {
                // Never report 100% while scan is still RUNNING
                this.progressPercentage = Math.min(99, Math.max(0, calculated));
            } else if (this.status == ScanStatus.COMPLETED) {
                this.progressPercentage = 100;
            } else {
                this.progressPercentage = Math.min(100, Math.max(0, calculated));
            }
        }
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public BookmarkAnalysisReport getAnalysisReport() {
        return analysisReport;
    }

    public void setAnalysisReport(BookmarkAnalysisReport analysisReport) {
        this.analysisReport = analysisReport;
    }

    public List<ComparisonItem> getComparisonItems() {
        return comparisonItems;
    }

    public void setComparisonItems(List<ComparisonItem> items) {
        this.comparisonItems = items != null ? Collections.unmodifiableList(new ArrayList<>(items)) : Collections.emptyList();
        this.itemMap.clear();
        this.bookmarkIdToItemMap.clear();
        if (items != null) {
            for (ComparisonItem item : items) {
                this.itemMap.put(item.getItemId(), item);
                if (item.getBookmarkId() != null) {
                    this.bookmarkIdToItemMap.put(item.getBookmarkId(), item);
                }
            }
        }
    }

    public ComparisonItem getComparisonItem(String itemId) {
        return itemMap.get(itemId);
    }

    public ComparisonItem getComparisonItemByBookmarkId(String bookmarkId) {
        return bookmarkIdToItemMap.get(bookmarkId);
    }

    public byte[] getExportJsonBytes() {
        return exportJsonBytes;
    }

    public void setExportJsonBytes(byte[] exportJsonBytes) {
        this.exportJsonBytes = exportJsonBytes;
    }

    public String getExportFilename() {
        return exportFilename;
    }

    public void setExportFilename(String exportFilename) {
        this.exportFilename = exportFilename;
    }

    public int getChangesAppliedCount() {
        return changesAppliedCount;
    }

    public void setChangesAppliedCount(int changesAppliedCount) {
        this.changesAppliedCount = changesAppliedCount;
    }

    public boolean isImportedFromBrowser() {
        return importedFromBrowser;
    }

    public void setImportedFromBrowser(boolean importedFromBrowser) {
        this.importedFromBrowser = importedFromBrowser;
    }

    public BrowserType getBrowserType() {
        return browserType;
    }

    public void setBrowserType(BrowserType browserType) {
        this.browserType = browserType;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getProfileDirectoryName() {
        return profileDirectoryName;
    }

    public void setProfileDirectoryName(String profileDirectoryName) {
        this.profileDirectoryName = profileDirectoryName;
    }

    public String getProfileDisplayName() {
        return profileDisplayName;
    }

    public String getProfileName() {
        return profileDisplayName != null ? profileDisplayName : profileDirectoryName;
    }

    public void setProfileDisplayName(String profileDisplayName) {
        this.profileDisplayName = profileDisplayName;
    }

    public java.nio.file.Path getOriginalBookmarkPath() {
        return originalBookmarkPath;
    }

    public void setOriginalBookmarkPath(java.nio.file.Path originalBookmarkPath) {
        this.originalBookmarkPath = originalBookmarkPath;
    }

    public String getOriginalFileHash() {
        return originalFileHash;
    }

    public void setOriginalFileHash(String originalFileHash) {
        this.originalFileHash = originalFileHash;
    }
}
