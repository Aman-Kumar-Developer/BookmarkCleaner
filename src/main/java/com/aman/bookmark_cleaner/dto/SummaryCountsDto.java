package com.aman.bookmark_cleaner.dto;

public class SummaryCountsDto {

    private final int total;
    private final long highCount;
    private final long mediumCount;
    private final long lowCount;
    private final long workingCount;
    private final long brokenCount;
    private final long redirectedCount;
    private final long duplicateCount;
    private final long unknownCount;
    private final long actionsAppliedCount;

    public SummaryCountsDto(int total,
                            long highCount,
                            long mediumCount,
                            long lowCount,
                            long workingCount,
                            long brokenCount,
                            long redirectedCount,
                            long duplicateCount,
                            long unknownCount,
                            long actionsAppliedCount) {
        this.total = total;
        this.highCount = highCount;
        this.mediumCount = mediumCount;
        this.lowCount = lowCount;
        this.workingCount = workingCount;
        this.brokenCount = brokenCount;
        this.redirectedCount = redirectedCount;
        this.duplicateCount = duplicateCount;
        this.unknownCount = unknownCount;
        this.actionsAppliedCount = actionsAppliedCount;
    }

    public int getTotal() {
        return total;
    }

    public long getHighCount() {
        return highCount;
    }

    public long getMediumCount() {
        return mediumCount;
    }

    public long getLowCount() {
        return lowCount;
    }

    public long getWorkingCount() {
        return workingCount;
    }

    public long getBrokenCount() {
        return brokenCount;
    }

    public long getRedirectedCount() {
        return redirectedCount;
    }

    public long getDuplicateCount() {
        return duplicateCount;
    }

    public long getUnknownCount() {
        return unknownCount;
    }

    public long getActionsAppliedCount() {
        return actionsAppliedCount;
    }
}
