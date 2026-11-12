package com.aman.bookmark_cleaner.dto;

/**
 * Data transfer object representing a versioned backup file within a browser profile.
 */
public class BackupInfoDto {

    private final String id;
    private final String filename;
    private final String displayName;
    private final long fileSize;
    private final int bookmarkCount;
    private final boolean valid;
    private final long lastModified;

    public BackupInfoDto(String id, String filename, String displayName, long fileSize, int bookmarkCount, boolean valid, long lastModified) {
        this.id = id;
        this.filename = filename;
        this.displayName = displayName;
        this.fileSize = fileSize;
        this.bookmarkCount = bookmarkCount;
        this.valid = valid;
        this.lastModified = lastModified;
    }

    public String getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public int getBookmarkCount() {
        return bookmarkCount;
    }

    public boolean isValid() {
        return valid;
    }

    public long getLastModified() {
        return lastModified;
    }
}
