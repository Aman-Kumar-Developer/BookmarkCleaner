package com.aman.bookmark_cleaner.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Domain model representing a browser installation and its discovered profiles.
 */
public class BrowserInfo {

    private final BrowserType browserType;
    private final boolean available;
    private final Path dataDirectory;
    private final List<BrowserProfile> profiles;

    public BrowserInfo(BrowserType browserType, boolean available, Path dataDirectory, List<BrowserProfile> profiles) {
        this.browserType = browserType;
        this.available = available;
        this.dataDirectory = dataDirectory;
        this.profiles = (profiles != null) ? Collections.unmodifiableList(new ArrayList<>(profiles)) : Collections.emptyList();
    }

    public BrowserType getBrowserType() {
        return browserType;
    }

    public boolean isAvailable() {
        return available;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public List<BrowserProfile> getProfiles() {
        return profiles;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BrowserInfo that = (BrowserInfo) o;
        return browserType == that.browserType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(browserType);
    }

    @Override
    public String toString() {
        return "BrowserInfo{" +
                "browserType=" + browserType +
                ", available=" + available +
                ", profileCount=" + profiles.size() +
                '}';
    }
}
