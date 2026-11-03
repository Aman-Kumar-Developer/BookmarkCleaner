package com.aman.bookmark_cleaner.dto;

import java.util.List;

public class ComparisonResultsPageResponse {

    private final String scanId;
    private final int page;
    private final int size;
    private final int totalItems;
    private final int totalPages;
    private final List<ComparisonItemDto> items;
    private final SummaryCountsDto summary;

    public ComparisonResultsPageResponse(String scanId,
                                         int page,
                                         int size,
                                         int totalItems,
                                         int totalPages,
                                         List<ComparisonItemDto> items,
                                         SummaryCountsDto summary) {
        this.scanId = scanId;
        this.page = page;
        this.size = size;
        this.totalItems = totalItems;
        this.totalPages = totalPages;
        this.items = items;
        this.summary = summary;
    }

    public String getScanId() {
        return scanId;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public List<ComparisonItemDto> getItems() {
        return items;
    }

    public SummaryCountsDto getSummary() {
        return summary;
    }
}
