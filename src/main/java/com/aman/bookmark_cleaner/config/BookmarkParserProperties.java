package com.aman.bookmark_cleaner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for bookmark parsing and file reading.
 */
@Component
@ConfigurationProperties(prefix = "bookmark.reader")
public class BookmarkParserProperties {

    /**
     * Maximum allowed bookmark file size in bytes. Default is 52,428,800 bytes (50 MB).
     */
    private long maxFileSize = 50 * 1024 * 1024L;

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }
}
