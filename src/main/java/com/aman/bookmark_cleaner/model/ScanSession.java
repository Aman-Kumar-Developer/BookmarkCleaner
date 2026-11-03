package com.aman.bookmark_cleaner.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    private volatile BookmarkAnalysisReport analysisReport;
    private volatile List<ComparisonItem> comparisonItems = Collections.emptyList();
    private final Map<String, ComparisonItem> itemMap = new ConcurrentHashMap<>();
    private final Map<String, ComparisonItem> bookmarkIdToItemMap = new ConcurrentHashMap<>();

    private volatile byte[] exportJsonBytes;
    private volatile String exportFilename = "bookmarks-cleaned.json";
    private volatile int changesAppliedCount;

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
}
