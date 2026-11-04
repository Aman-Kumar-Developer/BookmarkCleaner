package com.aman.bookmark_cleaner.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Domain model representing a discovered browser profile and its bookmark file status.
 */
public class BrowserProfile {

    private final String id;
    private final BrowserType browserType;
    private final String directoryName;
    private final String displayName;
    private final Path bookmarkPath;
    private final boolean bookmarkAvailable;
    private final long bookmarkFileSize;

    public BrowserProfile(String id,
                          BrowserType browserType,
                          String directoryName,
                          String displayName,
                          Path bookmarkPath,
                          boolean bookmarkAvailable,
                          long bookmarkFileSize) {
        this.id = id;
        this.browserType = browserType;
        this.directoryName = directoryName;
        this.displayName = (displayName != null && !displayName.isBlank()) ? displayName.trim() : directoryName;
        this.bookmarkPath = bookmarkPath;
        this.bookmarkAvailable = bookmarkAvailable;
        this.bookmarkFileSize = bookmarkFileSize;
    }

    public String getId() {
        return id;
    }

    public BrowserType getBrowserType() {
        return browserType;
    }

    public String getDirectoryName() {
        return directoryName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Path getBookmarkPath() {
        return bookmarkPath;
    }

    public boolean isBookmarkAvailable() {
        return bookmarkAvailable;
    }

    public long getBookmarkFileSize() {
        return bookmarkFileSize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BrowserProfile that = (BrowserProfile) o;
        return Objects.equals(id, that.id) && browserType == that.browserType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, browserType);
    }

    @Override
    public String toString() {
        return "BrowserProfile{" +
                "id='" + id + '\'' +
                ", browserType=" + browserType +
                ", directoryName='" + directoryName + '\'' +
                ", displayName='" + displayName + '\'' +
                ", bookmarkAvailable=" + bookmarkAvailable +
                ", bookmarkFileSize=" + bookmarkFileSize +
                '}';
    }
}
