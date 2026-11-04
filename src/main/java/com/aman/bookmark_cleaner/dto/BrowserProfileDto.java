package com.aman.bookmark_cleaner.dto;

/**
 * DTO representing a discovered browser profile exposed via the REST API.
 * Contains no internal filesystem paths to maintain security.
 */
public class BrowserProfileDto {

    private final String id;
    private final String name;
    private final boolean available;
    private final boolean bookmarkAvailable;

    public BrowserProfileDto(String id, String name, boolean available, boolean bookmarkAvailable) {
        this.id = id;
        this.name = name;
        this.available = available;
        this.bookmarkAvailable = bookmarkAvailable;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isBookmarkAvailable() {
        return bookmarkAvailable;
    }
}
